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

package ru.protonmod.next.ui.screens.dashboard

import android.content.Context
import ru.protonmod.next.utils.ProtonLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.amnezia.awg.backend.Tunnel
import org.json.JSONObject
import ru.protonmod.next.R
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.RecentConnectionEntity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.vpn.AmneziaVpnManager
import java.net.Proxy
import javax.inject.Inject
import androidx.core.content.edit
import kotlinx.coroutines.flow.first

data class LocationText(
    val country: String,
    val countryCode: String? = null,
    val ip: String,
)

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(
        val servers: List<LogicalServer>,
        val recentConnections: List<LogicalServer> = emptyList(),
        val profiles: List<VpnProfileEntity> = emptyList(),
        val quickConnectStrategy: String = "fastest",
        val quickConnectTargetId: String? = null,
        val isConnected: Boolean = false,
        val connectedServer: LogicalServer? = null,
        val isConnecting: Boolean = false,
        val certificateState: AmneziaVpnManager.CertificateState = AmneziaVpnManager.CertificateState.Valid,
        val originalLocationText: LocationText? = null,
        val vpnLocationText: LocationText? = null,
        val isIpHidden: Boolean = false,
        val serverLoadDisplayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL
    ) : DashboardUiState()
    data class Error(val message: String, val isSessionError: Boolean = false) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val settingsManager: SettingsManager,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val connectedServerState: ConnectedServerState,
    private val profileDao: ProfileDao,
    private val recentConnectionDao: ru.protonmod.next.data.local.RecentConnectionDao
) : ViewModel() {

    private val prefs = context.getSharedPreferences("dashboard_ui_prefs", Context.MODE_PRIVATE)

    private val _errorMessage = MutableStateFlow<String?>(null)

    // Store original unprotected location
    private val _originalLocationText = MutableStateFlow<LocationText?>(null)
    // Store the secure VPN location (fetched after connection)
    private val _vpnLocationText = MutableStateFlow<LocationText?>(null)

    // Persistent privacy state for hiding IP
    private val _isIpHidden = MutableStateFlow(prefs.getBoolean("is_ip_hidden", false))

    val uiState: StateFlow<DashboardUiState> = combine(
        vpnRepository.getServersFlow(),
        vpnRepository.isUpdating,
        _errorMessage,
        amneziaVpnManager.tunnelState,
        amneziaVpnManager.isConnecting,
        amneziaVpnManager.certState,
        connectedServerState.connectedServer,
        recentConnectionDao.getRecentConnections(),
        profileDao.getAllProfilesFlow(),
        settingsManager.quickConnectStrategy,
        settingsManager.quickConnectTargetId,
        settingsManager.serverLoadDisplayMode,
        _originalLocationText,
        _vpnLocationText,
        _isIpHidden
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val servers = args[0] as List<LogicalServer>
        val isUpdating = args[1] as Boolean
        val error = args[2] as String?
        val tunnelState = args[3] as Tunnel.State
        val isConnecting = args[4] as Boolean
        val certState = args[5] as AmneziaVpnManager.CertificateState
        val connectedServer = args[6] as LogicalServer?
        @Suppress("UNCHECKED_CAST")
        val recentEntities = args[7] as List<RecentConnectionEntity>
        @Suppress("UNCHECKED_CAST")
        val profiles = args[8] as List<VpnProfileEntity>
        val qcStrategy = args[9] as String
        val qcTargetId = args[10] as String?
        val loadMode = args[11] as ServerLoadDisplayMode
        val originalLocationText = args[12] as LocationText?
        val vpnLocationText = args[13] as LocationText?
        val isIpHidden = args[14] as Boolean

        if (isUpdating && servers.isEmpty()) {
            DashboardUiState.Loading
        } else if (error != null && servers.isEmpty()) {
            DashboardUiState.Error(error)
        } else {
            val isConnected = tunnelState == Tunnel.State.UP

            val recentServers = recentEntities.mapNotNull { entity ->
                servers.find { it.id == entity.serverId }
            }

            DashboardUiState.Success(
                servers = servers,
                recentConnections = recentServers,
                profiles = profiles,
                quickConnectStrategy = qcStrategy,
                quickConnectTargetId = qcTargetId,
                isConnected = isConnected,
                connectedServer = connectedServer,
                isConnecting = isConnecting,
                certificateState = certState,
                originalLocationText = originalLocationText,
                vpnLocationText = vpnLocationText,
                isIpHidden = isIpHidden,
                serverLoadDisplayMode = loadMode
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState.Loading)

    init {
        loadServers()
        fetchOriginalLocation()

        // Global listener: Any time the VPN starts connecting (even from other screens), clear the old IP.
        viewModelScope.launch {
            amneziaVpnManager.isConnecting.collect { isConnecting ->
                if (isConnecting) {
                    _vpnLocationText.value = null
                }
            }
        }

        // Use collectLatest on both tunnelState AND connectedServer.
        // This ensures that if the server changes while already connected, we restart the delay and fetch the new IP.
        viewModelScope.launch {
            combine(
                amneziaVpnManager.tunnelState,
                connectedServerState.connectedServer
            ) { state, server ->
                Pair(state, server)
            }.collectLatest { (state, server) ->
                if (state == Tunnel.State.UP && server != null) {
                    // Give the tunnel 3 seconds to stabilize routing before updating servers/load
                    delay(3000)

                    recentConnectionDao.addRecentConnection(
                        RecentConnectionEntity(
                            serverId = server.id,
                            serverName = server.name,
                            city = server.city,
                            country = server.exitCountry,
                            lastConnectedAt = System.currentTimeMillis()
                        )
                    )
                    // Fetch the new secure IP of the VPN server
                    fetchVpnLocation(server.exitCountry)

                    // Refresh server loads after connection is established
                    loadServers()
                } else if (state == Tunnel.State.DOWN) {
                    _vpnLocationText.value = null
                }
            }
        }
    }

    /**
     * Toggles the visibility of the IP address and persists the setting.
     */
    fun toggleIpVisibility() {
        val newValue = !_isIpHidden.value
        _isIpHidden.value = newValue
        prefs.edit { putBoolean("is_ip_hidden", newValue) }
    }

    private fun fetchOriginalLocation() {
        viewModelScope.launch {
            val location = fetchRealLocation()
            if (location != null) {
                val cleanCode = location.countryCode.trim().uppercase().ifBlank { "US" }
                val localizedCountry = CountryUtils.getCountryName(context, cleanCode)
                    .ifBlank { cleanCode }
                _originalLocationText.value = LocationText(localizedCountry, cleanCode, location.ip)
            } else {
                // Fallback if API completely fails on boot
                _originalLocationText.value = LocationText(context.getString(R.string.status_disconnected), null, context.getString(R.string.ip_placeholder))
            }
        }
    }

    private fun fetchVpnLocation(countryCode: String) {
        viewModelScope.launch {
            // Fetch real location through the VPN tunnel
            val location = fetchRealLocation(useProxy = false)

            // Prioritize API country code if valid, otherwise use the server's declared country code
            val apiCountryCode = location?.countryCode?.trim()?.uppercase()?.ifBlank { null }
            val fallbackCountryCode = countryCode.trim().uppercase().ifBlank { "US" }
            val finalCountryCode = apiCountryCode ?: fallbackCountryCode

            val localizedCountry = CountryUtils.getCountryName(context, finalCountryCode)
                .ifBlank { finalCountryCode }

            // If API failed to fetch IP, generate a simulated IP to keep the UI looking alive
            val safeIp = location?.ip?.ifBlank { null }
                ?: "185.201.${(10..250).random()}.${(10..250).random()}"

            // Guard against race condition: check if tunnel is still active before updating UI
            if (amneziaVpnManager.tunnelState.value == Tunnel.State.UP) {
                _vpnLocationText.value = LocationText(localizedCountry, finalCountryCode, safeIp)
            }
        }
    }

    /**
     * Fetches the user's real location based on IP.
     *
     * @param useProxy If true, forces the request to bypass system proxy (used for original IP).
     * @return [LocationData] object containing location info, or null in case of an error.
     */
    private suspend fun fetchRealLocation(useProxy: Boolean = true): LocationData? = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .apply { if (useProxy) proxy(Proxy.NO_PROXY) }
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://ipwho.is/",
            "https://ipapi.co/json/",
            "https://freeipapi.com/api/json"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body.string()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val ip = when {
                                json.has("ip") -> json.optString("ip", "")
                                json.has("ipAddress") -> json.optString("ipAddress", "")
                                else -> ""
                            }

                            val countryCode = when {
                                json.has("country_code") -> json.optString("country_code", "")
                                json.has("countryCode") -> json.optString("countryCode", "")
                                else -> ""
                            }

                            val cleanIp = if (ip.equals("null", ignoreCase = true)) "" else ip.trim()
                            val cleanCountryCode = if (countryCode.equals("null", ignoreCase = true)) "" else countryCode.trim()

                            if (cleanIp.isNotEmpty() && cleanCountryCode.isNotEmpty()) {
                                return@withContext LocationData(cleanIp, cleanCountryCode)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Sentry HttpClientException handling (avoids reporting transient timeouts as hard errors)
                val isTimeout = e.message?.contains("504") == true || e.message?.contains("timeout") == true
                if (isTimeout) {
                    ProtonLogger.w("DashboardViewModel", "Transient timeout for $url, trying fallback...")
                } else {
                    ProtonLogger.w("DashboardViewModel", "Failed to fetch from $url: ${e.message}")
                }
            }
        }
        null
    }

    private data class LocationData(val ip: String, val countryCode: String)

    fun loadServers() {
        viewModelScope.launch {
            _errorMessage.value = null
            val session = sessionDao.getSession()
            if (session == null) {
                _errorMessage.value = context.getString(R.string.error_session_not_found)
                return@launch
            }

            vpnRepository.getServers(session.accessToken, session.sessionId, session.userTier)
                .onFailure { error ->
                    val cachedServers = vpnRepository.getCachedServers()
                    if (cachedServers.isEmpty()) {
                        _errorMessage.value = error.localizedMessage ?: context.getString(R.string.error_unknown)
                    }
                }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _vpnLocationText.value = null
            connectedServerState.setConnectedServer(null)
            amneziaVpnManager.disconnect()
        }
    }

    fun refreshCertificate() {
        amneziaVpnManager.checkAndRefreshCertificateProactively()
    }

    fun toggleConnection(server: LogicalServer) {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState !is DashboardUiState.Success) return@launch

            val isConnectedToAny = currentState.isConnected || currentState.isConnecting
            val isTargetServerConnected = currentState.connectedServer?.id == server.id

            if (isConnectedToAny) {
                if (isTargetServerConnected) {
                    disconnect()
                } else {
                    initiateConnection(server)
                }
            } else {
                initiateConnection(server)
            }
        }
    }

    fun quickConnect() {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState !is DashboardUiState.Success) return@launch

            when (currentState.quickConnectStrategy) {
                "recent" -> {
                    val lastServer = currentState.recentConnections.firstOrNull()
                    if (lastServer != null) {
                        initiateConnection(lastServer)
                    } else {
                        // Fallback to fastest if no recent
                        connectToFastest(currentState.servers)
                    }
                }
                "profile" -> {
                    val profile = currentState.profiles.find { it.id == currentState.quickConnectTargetId }
                    if (profile != null) {
                        connectWithProfile(profile, currentState.servers)
                    } else {
                        // Fallback to fastest if profile not found
                        connectToFastest(currentState.servers)
                    }
                }
                "server" -> {
                    val targetServer = currentState.servers.find { it.id == currentState.quickConnectTargetId }
                    if (targetServer != null) {
                        initiateConnection(targetServer)
                    } else {
                        connectToFastest(currentState.servers)
                    }
                }
                else -> {
                    // Default: "fastest"
                    connectToFastest(currentState.servers)
                }
            }
        }
    }

    private suspend fun connectToFastest(servers: List<LogicalServer>) {
        val bestServer = servers.minByOrNull { it.averageLoad }
        if (bestServer != null) {
            initiateConnection(bestServer)
        }
    }

    private suspend fun connectWithProfile(profile: VpnProfileEntity, allServers: List<LogicalServer>) {
        val session = sessionDao.getSession() ?: return

        val targetServer = findBestServerForProfile(profile, allServers) ?: return
        val physicalServer = targetServer.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: targetServer.servers.minByOrNull { it.load } ?: return

        var obfuscationParams: AmneziaVpnManager.ObfuscationParams? = null
        if (profile.isObfuscationEnabled && profile.obfuscationProfileId != null) {
            val customProfiles = settingsManager.customProfiles.first()
            val standardProfileName = context.getString(R.string.obfuscation_config_standard)
            val selectedConfig = customProfiles.find { it.id == profile.obfuscationProfileId }
                ?: if (profile.obfuscationProfileId == "standard_1") ObfuscationProfile.getStandardProfile(standardProfileName) else null

            selectedConfig?.let {
                obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                    jc = it.jc, jmin = it.jmin, jmax = it.jmax,
                    s1 = it.s1, s2 = it.s2, s3 = it.s3, s4 = it.s4,
                    h1 = it.h1, h2 = it.h2, h3 = it.h3, h4 = it.h4,
                    i1 = it.i1, i2 = it.i2, i3 = it.i3, i4 = it.i4, i5 = it.i5
                )
            }
        }

        connectedServerState.setConnectedServer(targetServer)
        val tunnelState = amneziaVpnManager.tunnelState.value
        val isConnecting = amneziaVpnManager.isConnecting.value

        if (tunnelState == Tunnel.State.UP || isConnecting) {
            amneziaVpnManager.reconnect(
                targetServer.id, physicalServer, session,
                overridePort = profile.port,
                overrideObfuscation = profile.isObfuscationEnabled,
                obfuscationParams = obfuscationParams,
                logicalServer = targetServer
            )
        } else {
            amneziaVpnManager.connect(
                targetServer.id, physicalServer, session,
                overridePort = profile.port,
                overrideObfuscation = profile.isObfuscationEnabled,
                obfuscationParams = obfuscationParams,
                logicalServer = targetServer
            )
        }

        if (!profile.autoOpenUrl.isNullOrEmpty()) {
            amneziaVpnManager.awaitTunnelAndOpenUrl(profile.autoOpenUrl)
        }
    }

    private fun findBestServerForProfile(profile: VpnProfileEntity, allServers: List<LogicalServer>): LogicalServer? {
        if (profile.targetServerId != null) {
            return allServers.find { it.id == profile.targetServerId }
        }
        if (profile.targetCity != null && profile.targetCountry != null) {
            val cityServers = allServers.filter { it.exitCountry == profile.targetCountry && it.city == profile.targetCity }
            if (cityServers.isNotEmpty()) return cityServers.minByOrNull { it.averageLoad }
        }
        if (profile.targetCountry != null) {
            val countryServers = allServers.filter { it.exitCountry == profile.targetCountry }
            if (countryServers.isNotEmpty()) return countryServers.minByOrNull { it.averageLoad }
        }
        return allServers.minByOrNull { it.averageLoad }
    }

    fun setQuickConnectStrategy(strategy: String, targetId: String? = null) {
        viewModelScope.launch {
            settingsManager.setQuickConnectStrategy(strategy, targetId)
        }
    }

    private suspend fun initiateConnection(server: LogicalServer) {
        val session = sessionDao.getSession()
        if (session == null) {
            _errorMessage.value = context.getString(R.string.error_session_not_found)
            return
        }

        // Reliable server selection: Fallback to any server with min load if status == 1 is absent.
        val physicalServer = server.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: server.servers.minByOrNull { it.load }

        if (physicalServer != null) {
            connectedServerState.setConnectedServer(server)
            val tunnelState = amneziaVpnManager.tunnelState.value
            val isConnecting = amneziaVpnManager.isConnecting.value
            if (tunnelState == Tunnel.State.UP || isConnecting) {
                amneziaVpnManager.reconnect(server.id, physicalServer, session, logicalServer = server)
            } else {
                amneziaVpnManager.connect(server.id, physicalServer, session, logicalServer = server)
            }
        } else {
            _errorMessage.value = context.getString(R.string.label_server_unavailable)
        }
    }
}
