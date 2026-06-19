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

package ru.protonmod.next.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

// --- Entities ---

@Serializable
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: Int = 1, // We only store one active user session
    val accessToken: String,
    val refreshToken: String,
    val sessionId: String,
    val userId: String,
    val userTier: Int = 0, // 0: Free, 1: Basic, 2: Plus
    val wgPrivateKey: String? = null,
    val wgPublicKeyPem: String? = null,
    val wgCertificate: String? = null,
    val certExpiresAt: Long = 0, // Unix timestamp in seconds
    val certRefreshAt: Long = 0,
    val vpnIpv4: String? = null,
    val vpnIpv6: String? = null,
    val vpnDns: String? = null // Comma-separated list
)

@Entity(tableName = "servers_cache")
data class ServersCacheEntity(
    @PrimaryKey val id: Int = 1, // We only store one cache entry
    val cachedAt: Long, // Timestamp in milliseconds
    val expiresAt: Long, // Timestamp when cache expires
    val lastModified: String? = null, // RFC 1123 header from server
    val statusId: String? = null
)


// --- DAO ---

@Dao
interface SessionDao {
    @Query("SELECT * FROM session WHERE id = 1")
    suspend fun getSession(): SessionEntity?

    @Query("SELECT * FROM session WHERE id = 1")
    fun getSessionFlow(): Flow<SessionEntity?>

    @Upsert
    suspend fun saveSession(session: SessionEntity)

    @Query("DELETE FROM session")
    suspend fun clearSession()
    
    @Query("UPDATE session SET userTier = :tier WHERE id = 1")
    suspend fun updateUserTier(tier: Int)

    @Query("UPDATE session SET wgCertificate = :certificate, certExpiresAt = :expiresAt, certRefreshAt = :refreshAt WHERE id = 1")
    suspend fun updateCertificate(certificate: String, expiresAt: Long, refreshAt: Long)

    @Query("UPDATE session SET certExpiresAt = :expiresAt, certRefreshAt = :refreshAt WHERE id = 1")
    suspend fun updateCertificateExpiry(expiresAt: Long, refreshAt: Long)

    @Query("UPDATE session SET vpnIpv4 = :ipv4, vpnIpv6 = :ipv6, vpnDns = :dns WHERE id = 1")
    suspend fun updateVpnConnectionInfo(ipv4: String?, ipv6: String?, dns: String?)

    @Query("UPDATE session SET wgPrivateKey = :privateKey, wgPublicKeyPem = :publicKeyPem, wgCertificate = :certificate, certExpiresAt = :expiresAt, certRefreshAt = :refreshAt WHERE id = 1")
    suspend fun updateVpnKeys(privateKey: String, publicKeyPem: String, certificate: String, expiresAt: Long, refreshAt: Long)
}

@Dao
interface ServersCacheDao {
    @Query("SELECT * FROM servers_cache WHERE id = 1")
    suspend fun getCacheInfo(): ServersCacheEntity?

    @Upsert
    suspend fun saveCacheInfo(cache: ServersCacheEntity)

    @Query("DELETE FROM servers_cache")
    suspend fun clearCacheInfo()
}
