/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.data.repository

import ru.protonmod.next.utils.ProtonLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import ru.protonmod.next.data.network.*
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServerMapper
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.ServersCacheDao
import ru.protonmod.next.data.local.ServersCacheEntity
import ru.protonmod.next.di.ApplicationScope
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    private val vpnApi: ProtonVpnApi,
    private val serverDao: ServerDao,
    private val sessionDao: SessionDao,
    private val serversCacheDao: ServersCacheDao,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope private val managerScope: CoroutineScope
) {
    private var autoUpdateJob: Job? = null
    private val fetchMutex = Mutex()

    // Variable for storing the currently executing fetch request
    private var activeFetch: Deferred<Result<List<LogicalServer>>>? = null
    private var cachedServers: List<LogicalServer> = emptyList()

    companion object {
        private const val TAG = "VpnRepository"
        private val json = Json { ignoreUnknownKeys = true }
        private const val CACHE_DURATION_MILLIS = 60 * 60 * 1000L // 1 hour
        private const val AUTO_UPDATE_INTERVAL_MINUTES = 20L
    }

    fun startAutoUpdate() {
        if (autoUpdateJob?.isActive == true) return

        autoUpdateJob = managerScope.launch {
            ProtonLogger.d(TAG, "Starting auto-update loop")
            while (isActive) {
                val session = sessionDao.getSession()
                if (session != null) {
                    getServers(
                        session.accessToken,
                        session.sessionId,
                        session.userTier,
                        forceRefresh = false
                    )
                }
                delay(TimeUnit.MINUTES.toMillis(AUTO_UPDATE_INTERVAL_MINUTES))
            }
        }
    }

    fun stopAutoUpdate() {
        autoUpdateJob?.cancel()
        autoUpdateJob = null
    }

    fun getServersFlow(): Flow<List<LogicalServer>> {
        return serverDao.getServersFlow().map { entities ->
            // Extract tier from the current active session dynamically
            val userTier = sessionDao.getSession()?.userTier ?: 0
            entities
                .map { ServerMapper.toDomain(it) }
                .filter { it.tier <= userTier } // Filter dynamically based on session tier
        }
    }

    suspend fun getCachedServers(): List<LogicalServer> {
        val userTier = sessionDao.getSession()?.userTier ?: 0
        return serverDao.getAllServers()
            .map { ServerMapper.toDomain(it) }
            .filter { it.tier <= userTier } // Filter dynamically based on session tier
    }

    suspend fun getServers(
        accessToken: String,
        sessionId: String,
        userTier: Int = 0,
        forceRefresh: Boolean = false
    ): Result<List<LogicalServer>> {
        val deferred = fetchMutex.withLock {
            if (activeFetch != null && !forceRefresh) {
                ProtonLogger.d(TAG, "Joining existing servers fetch request")
                activeFetch!!
            } else {
                val newFetch = managerScope.async {
                    performGetServers(accessToken, sessionId, userTier, forceRefresh)
                }
                activeFetch = newFetch
                newFetch
            }
        }

        return try {
            deferred.await()
        } finally {
            fetchMutex.withLock {
                if (activeFetch == deferred) {
                    activeFetch = null
                }
            }
        }
    }

    private suspend fun performGetServers(
        accessToken: String,
        sessionId: String,
        userTier: Int,
        forceRefresh: Boolean
    ): Result<List<LogicalServer>> = withContext(dispatcherProvider.io()) {
        try {
            val now = System.currentTimeMillis()
            val cacheInfo = serversCacheDao.getCacheInfo()

            val shouldCheckApi = forceRefresh || cacheInfo == null || now > cacheInfo.expiresAt
            val isStale = cacheInfo != null && (now - cacheInfo.cachedAt > TimeUnit.MINUTES.toMillis(AUTO_UPDATE_INTERVAL_MINUTES))

            if (!shouldCheckApi && !isStale) {
                val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
                if (dbServers.isNotEmpty()) {
                    val result = dbServers.filter { it.tier <= userTier }
                    cachedServers = result
                    return@withContext Result.success(result)
                }
            }

            val bearer = "Bearer $accessToken"
            val ifModifiedSince = if (!forceRefresh) cacheInfo?.lastModified else null

            ProtonLogger.d(TAG, "Fetching servers from API (forceRefresh=$forceRefresh)")
            val response = vpnApi.getLogicalServers(
                authorization = bearer,
                sessionId = sessionId,
                lastModified = ifModifiedSince,
                protocols = "wireguard",
                userTier = userTier
            )

            val (serversList, newLastModified) = when (response.code()) {
                304 -> {
                    ProtonLogger.d(TAG, "Servers not modified (304), updating timestamps only")
                    val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
                    dbServers to cacheInfo?.lastModified
                }
                200 -> {
                    val body = response.body()
                    if (body?.code == 1000) {
                        body.logicalServers to response.headers()["Last-Modified"]
                    } else {
                        return@withContext Result.failure(Exception("API error: ${body?.code}"))
                    }
                }
                else -> {
                    val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
                    if (dbServers.isNotEmpty()) return@withContext Result.success(dbServers.filter { it.tier <= userTier })
                    return@withContext Result.failure(Exception("Network error: ${response.code()}"))
                }
            }

            if (serversList.isEmpty()) {
                return@withContext Result.failure(Exception("No servers available"))
            }

            // Fetch server loads
            val loadsResponse = vpnApi.getLoads(bearer, sessionId, userTier)
            if (loadsResponse.isSuccessful) {
                val loadsBody = loadsResponse.body()?.string()
                val loadsData = loadsBody?.let {
                    try { json.decodeFromString<LoadsResponse>(it) } catch (e: Exception) { null }
                }

                val loadsMap = loadsData?.loads?.associate { it.id to it.load } ?: emptyMap()

                serversList.forEach { logical ->
                    val logicalLoad = loadsMap[logical.id]
                    if (logicalLoad != null) {
                        logical.averageLoad = logicalLoad
                        logical.servers.forEach { it.load = loadsMap[it.id] ?: logicalLoad }
                    } else {
                        var totalLoad = 0
                        var activeServers = 0
                        logical.servers.forEach { physical ->
                            val load = loadsMap[physical.id]
                            if (load != null) {
                                physical.load = load
                                totalLoad += load
                                activeServers++
                            }
                        }
                        logical.averageLoad = if (activeServers > 0) totalLoad / activeServers else 0
                    }
                }
            }

            // Save to DB AFTER fetching loads, ensuring the DB has the latest load values
            val entities = serversList.map { ServerMapper.toEntity(it) }
            serverDao.insertServers(entities)

            // Update cache metadata
            val newCacheInfo = ServersCacheEntity(
                cachedAt = now,
                expiresAt = now + CACHE_DURATION_MILLIS,
                lastModified = newLastModified
            )
            serversCacheDao.saveCacheInfo(newCacheInfo)

            val logicalServers = serversList.filter { it.tier <= userTier }
            cachedServers = logicalServers
            Result.success(logicalServers)
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Error in performGetServers", e)
            val dbServers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
            if (dbServers.isNotEmpty()) Result.success(dbServers.filter { it.tier <= userTier })
            else Result.failure(e)
        }
    }

    suspend fun getUserLocation(accessToken: String, sessionId: String): Result<String> = withContext(dispatcherProvider.io()) {
        try {
            val response = vpnApi.getUserLocation("Bearer $accessToken", sessionId)
            val body = response.body()?.string()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Failed to get location: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVpnInfo(accessToken: String, sessionId: String): Result<VpnInfoResponse> = withContext(dispatcherProvider.io()) {
        try {
            val bearer = "Bearer $accessToken"
            val response = vpnApi.getVpnInfo(bearer, sessionId)
            val body = response.body()?.string()

            ProtonLogger.d(TAG, "getVpnInfo raw body: $body")

            if (response.isSuccessful && body != null) {
                Result.success(json.decodeFromString<VpnInfoResponse>(body))
            } else {
                Result.failure(Exception("Failed to fetch VPN info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerWireGuardKey(
        accessToken: String,
        sessionId: String,
        publicKeyPem: String
    ): Result<CreateCertificateResponse> = withContext(dispatcherProvider.io()) {
        try {
            val bearer = "Bearer $accessToken"
            val request = CreateCertificateRequest(clientPublicKey = publicKeyPem)
            val response = vpnApi.registerVpnKey(bearer, sessionId, request)

            ProtonLogger.d(TAG, "registerWireGuardKey response code: ${response.code}, cert length: ${response.certificate?.length ?: 0}")

            if (response.code == 1000) {
                val cert = response.certificate
                if (cert != null) {
                    sessionDao.updateCertificate(cert)
                }
                Result.success(response)
            } else {
                Result.failure(Exception("Proton Cert Error: ${response.code}"))
            }
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Error in registerWireGuardKey", e)
            Result.failure(e)
        }
    }
}