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

package ru.protonmod.next.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import ru.protonmod.next.utils.ProtonLogger
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import org.amnezia.awg.config.Interface
import org.amnezia.awg.config.Peer
import java.net.InetAddress
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.di.ApplicationScope
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import ru.protonmod.next.utils.crypto.CryptoWrapper
import ru.protonmod.next.utils.system.SystemContextWrapper
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AmneziaVpnManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val vpnRepositoryProvider: Provider<VpnRepository>,
    private val sessionDao: SessionDao,
    private val connectedServerState: ConnectedServerState,
    private val systemContextWrapper: SystemContextWrapper,
    private val cryptoWrapper: CryptoWrapper,
    private val amneziaConfigGenerator: AmneziaConfigGenerator,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AmneziaVpnManager"
        private const val PROTON_CLIENT_IP = "10.2.0.2"
        private const val PROTON_DNS_IP = "10.2.0.1" // Fallback default DNS
        private const val DNS_RETRY_COUNT = 5
        private const val DNS_RETRY_DELAY_MS = 1000L
        private const val STATE_CONNECTING = "CONNECTING"

        private const val REFRESH_THRESHOLD_MS = 1 * 3600 * 1000L // 1 hour
        private const val RETRY_DELAY_MS = 15 * 60 * 1000L // 15 minutes
        private const val PERIODIC_REFRESH_MS = 2 * 3600 * 1000L // 2 hours
    }

    sealed class CertificateState {
        data object Valid : CertificateState()
        data class ExpiringSoon(val hoursRemaining: Int) : CertificateState()
        data object Expired : CertificateState()
        data class RefreshFailed(val error: String, val isFullyExpired: Boolean) : CertificateState()
        data object Refreshing : CertificateState()
    }

    private val _certState = MutableStateFlow<CertificateState>(CertificateState.Valid)
    val certState: StateFlow<CertificateState> = _certState.asStateFlow()

    data class ObfuscationParams(
        val jc: Int, val jmin: Int, val jmax: Int,
        val s1: Int, val s2: Int,
        val h1: String, val h2: String, val h3: String, val h4: String,
        val i1: String, val i2: String = "", val i3: String = "", val i4: String = "", val i5: String = "",
        val customId: String = "", val ip: String = "", val ib: String = ""
    )

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _tunnelState = MutableStateFlow(Tunnel.State.DOWN)
    val tunnelState: StateFlow<Tunnel.State> = _tunnelState

    private val _rawTunnelState = MutableStateFlow(Tunnel.State.DOWN)
    private var isReconnecting = false
    private var currentServerId: String? = null
    private var connectionJob: Job? = null
    private var refreshJob: Job? = null
    private val refreshMutex = Mutex()

    init {
        val filter = IntentFilter(ProtonVpnService.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val stateStr = intent?.getStringExtra(ProtonVpnService.EXTRA_STATE)
                stateStr?.let {
                    if (it == STATE_CONNECTING) {
                        _isConnecting.value = true
                    } else {
                        val newState = Tunnel.State.valueOf(it)
                        _rawTunnelState.value = newState
                        _isConnecting.value = false
                        
                        _tunnelState.value = newState
                        if (newState == Tunnel.State.UP) {
                            checkAndRefreshCertificateProactively()
                        } else if (newState == Tunnel.State.DOWN && !isReconnecting) {
                            currentServerId = null
                            connectedServerState.setConnectedServer(null)
                        }
                    }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        applicationScope.launch { settingsManager.notificationsEnabled.collect { updateServiceSettings() } }
        applicationScope.launch { settingsManager.killSwitchEnabled.collect { updateServiceSettings() } }

        applicationScope.launch {
            val session = sessionDao.getSession()
            if (session != null) {
                updateCertificateState(session.wgCertificate)
                if (_certState.value !is CertificateState.Valid) {
                    checkAndRefreshCertificateProactively()
                }
            }
        }
    }

    private fun updateCertificateState(certPem: String?) {
        if (certPem.isNullOrEmpty()) {
            _certState.value = CertificateState.Expired
            return
        }
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val x509 = cf.generateCertificate(ByteArrayInputStream(certPem.toByteArray())) as X509Certificate
            val now = System.currentTimeMillis()
            val expiry = x509.notAfter.time

            if (now >= expiry) {
                _certState.value = CertificateState.Expired
            } else if (expiry - now < REFRESH_THRESHOLD_MS) {
                val hours = ((expiry - now) / (3600 * 1000L)).toInt()
                _certState.value = CertificateState.ExpiringSoon(hours)
            } else {
                _certState.value = CertificateState.Valid
            }
        } catch (e: Exception) {
            _certState.value = CertificateState.Expired
        }
    }

    private suspend fun performCertificateRefresh(force: Boolean = false): Result<String> = refreshMutex.withLock {
        val currentSession = sessionDao.getSession() ?: return Result.failure(Exception("No session"))
        updateCertificateState(currentSession.wgCertificate)

        if (!force && _certState.value is CertificateState.Valid) {
            return Result.success(currentSession.wgCertificate ?: "")
        }

        val previousState = _certState.value
        _certState.value = CertificateState.Refreshing
        ProtonLogger.d(TAG, "Refreshing certificate (previous state: $previousState)")

        val keyPair = cryptoWrapper.generateVpnKeyPair()

        val result = vpnRepositoryProvider.get().registerWireGuardKey(
            accessToken = currentSession.accessToken,
            sessionId = currentSession.sessionId,
            publicKeyPem = keyPair.publicKeyPem
        )
        return if (result.isSuccess) {
            val newCert = result.getOrNull()?.certificate
            if (newCert != null) {
                sessionDao.updateVpnKeys(
                    privateKey = keyPair.privateKeyX25519,
                    publicKeyPem = keyPair.publicKeyPem,
                    certificate = newCert
                )
                updateCertificateState(newCert)
                Result.success(newCert)
            } else {
                _certState.value = previousState
                Result.failure(Exception("Empty certificate in response"))
            }
        } else {
            val error = result.exceptionOrNull()?.message ?: "Unknown error"
            val isFullyExpired = previousState is CertificateState.Expired ||
                    (previousState is CertificateState.RefreshFailed && previousState.isFullyExpired)
            _certState.value = CertificateState.RefreshFailed(error, isFullyExpired)
            Result.failure(result.exceptionOrNull() ?: Exception(error))
        }
    }

    fun checkAndRefreshCertificateProactively() {
        if (refreshJob?.isActive == true) return
        refreshJob = applicationScope.launch {
            var currentRetryDelay = 60000L // Start retrying after 1 minute
            while (isActive) {
                val session = sessionDao.getSession() ?: break
                updateCertificateState(session.wgCertificate)

                if (_certState.value is CertificateState.Valid) {
                    // All good, check again in 2 hours
                    delay(PERIODIC_REFRESH_MS)
                    currentRetryDelay = 5000L
                    continue
                }

                ProtonLogger.d(TAG, "Proactive refresh starting (cert state: ${_certState.value})")
                val result = performCertificateRefresh(force = false)
                
                if (result.isSuccess) {
                    currentRetryDelay = 5000L
                    delay(PERIODIC_REFRESH_MS)
                } else {
                    // API access is expected to be preserved, so we retry with backoff.
                    // This covers cases where internet is temporarily down.
                    ProtonLogger.w(TAG, "Proactive refresh failed, retrying in ${currentRetryDelay}ms")
                    delay(currentRetryDelay)
                    currentRetryDelay = (currentRetryDelay * 2).coerceAtMost(RETRY_DELAY_MS)
                }
            }
        }
    }

    fun isEffectivelyExpired(): Boolean {
        val state = _certState.value
        return state is CertificateState.Expired || (state is CertificateState.RefreshFailed && state.isFullyExpired)
    }

    suspend fun forceRefreshCertificate(): Result<String> {
        return performCertificateRefresh(force = true)
    }

    private suspend fun updateServiceSettings() {
        systemContextWrapper.updateVpnSettings(
            notificationsEnabled = settingsManager.notificationsEnabled.first(),
            killSwitchEnabled = settingsManager.killSwitchEnabled.first()
        )
    }

    fun connect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null,
        logicalServer: LogicalServer? = null
    ) {
        if (currentServerId == logicalServerId && _tunnelState.value == Tunnel.State.UP) {
            ProtonLogger.d(TAG, "Already connected to $logicalServerId")
            return
        }
        
        connectionJob?.cancel()
        connectionJob = applicationScope.launch(dispatcherProvider.io()) {
            currentServerId = logicalServerId
            
            // Resolve logical server if not provided to ensure UI can show location info
            if (logicalServer != null) {
                connectedServerState.setConnectedServer(logicalServer)
            } else if (connectedServerState.connectedServer.value?.id != logicalServerId) {
                val resolved = vpnRepositoryProvider.get().getCachedServers().find { it.id == logicalServerId }
                connectedServerState.setConnectedServer(resolved)
            }

            connectInternal(logicalServerId, server, session, overridePort, overrideObfuscation, obfuscationParams)
        }
    }

    private suspend fun connectInternal(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        try {
            _isConnecting.value = true
            var currentSession = session

            // Proactively refresh certificate if it's not valid (Expired or ExpiringSoon)
            updateCertificateState(currentSession.wgCertificate)
            if (_certState.value !is CertificateState.Valid) {
                ProtonLogger.d(TAG, "Certificate state is ${_certState.value}, attempting refresh before connection.")
                performCertificateRefresh()

                if (isEffectivelyExpired()) {
                    // Even if expired, we try to connect because Proton API might be reachable 
                    // and some servers might still accept the old key for a short grace period.
                    // But we keep the UI wrning.
                    ProtonLogger.w(TAG, "Certificate is expired. Proceeding with connection anyway as Proton API is accessible.")
                }
                
                // Refresh session from DB to get the new certificate and any other potential updates
                currentSession = sessionDao.getSession() ?: currentSession
            }

            val wgPrivateKeyB64 = currentSession.wgPrivateKey ?: throw Exception("Offline VPN private key missing!")
            var targetIp: String? = null
            
            // DNS resolution with improved retry and logging
            for (i in 1..DNS_RETRY_COUNT) {
                try {
                    targetIp = InetAddress.getByName(server.domain).hostAddress
                    if (targetIp != null) {
                        ProtonLogger.d(TAG, "DNS resolved ${server.domain} to $targetIp")
                        break
                    }
                } catch (e: Exception) {
                    ProtonLogger.w(TAG, "DNS retry $i failed for ${server.domain}: ${e.message}")
                    if (i < DNS_RETRY_COUNT) delay(DNS_RETRY_DELAY_MS * i) // Exponential-ish backoff
                }
            }

            if (targetIp == null) {
                _isConnecting.value = false
                _tunnelState.value = Tunnel.State.DOWN
                throw Exception("DNS resolution failed for ${server.domain}")
            }

            val serverPubKey = server.wgPublicKey ?: throw Exception("Missing WG Public Key for Server")
            val splitTunnelingEnabled = settingsManager.splitTunnelingEnabled.first()
            val stMode = settingsManager.splitTunnelingMode.first()
            val isIncludeMode = stMode == "include"
            val selectedApps = if (splitTunnelingEnabled) settingsManager.excludedApps.first() else emptySet()
            val selectedIps = if (splitTunnelingEnabled) settingsManager.excludedIps.first().toMutableSet() else mutableSetOf()
            val selectedDomains = if (splitTunnelingEnabled) settingsManager.excludedDomains.first() else emptySet()

            // Resolve split tunneling domains
            if (splitTunnelingEnabled && selectedDomains.isNotEmpty()) {
                ProtonLogger.d(TAG, "Resolving ${selectedDomains.size} split-tunneling domains...")
                selectedDomains.forEach { domain ->
                    try {
                        val addresses = InetAddress.getAllByName(domain)
                        addresses.forEach { addr ->
                            val ip = addr.hostAddress
                            if (ip != null) {
                                selectedIps.add(if (ip.contains(":")) "$ip/128" else "$ip/32")
                                ProtonLogger.v(TAG, "Domain $domain resolved to $ip for split tunneling")
                            }
                        }
                    } catch (e: Exception) {
                        ProtonLogger.w(TAG, "Failed to resolve split-tunneling domain $domain: ${e.message}")
                    }
                }
            }

            val selectedPort = overridePort?.takeIf { it != 0 } ?: settingsManager.vpnPort.first().let { port ->
                if (port == 0) listOf(443, 123, 1194, 51820).random() else port
            }
            val isObfuscationEnabled = overrideObfuscation ?: settingsManager.obfuscationEnabled.first()

            val params = if (isObfuscationEnabled) {
                obfuscationParams ?: ObfuscationParams(
                    jc = settingsManager.awgJc.first(), jmin = settingsManager.awgJmin.first(), jmax = settingsManager.awgJmax.first(),
                    s1 = settingsManager.awgS1.first(), s2 = settingsManager.awgS2.first(),
                    h1 = settingsManager.awgH1.first(), h2 = settingsManager.awgH2.first(), h3 = settingsManager.awgH3.first(), h4 = settingsManager.awgH4.first(),
                    i1 = settingsManager.awgI1.first(), i2 = settingsManager.awgI2.first(), i3 = settingsManager.awgI3.first(), i4 = settingsManager.awgI4.first(), i5 = settingsManager.awgI5.first(),
                    customId = settingsManager.awgId.first(), ip = settingsManager.awgIp.first(), ib = settingsManager.awgIb.first()
                )
            } else {
                ObfuscationParams(0, 0, 0, 0, 0, "", "", "", "", "", "", "", "", "", "", "", "")
            }

            // Retrieve Custom DNS IP or fallback to Proton Default
            val userDns = settingsManager.customDns.first().trim()
            val activeDns = if (userDns.isNotEmpty()) userDns else PROTON_DNS_IP
            ProtonLogger.d(TAG, "Using DNS Server: $activeDns")

            val configStr = amneziaConfigGenerator.buildConfig(
                serverPublicKey = serverPubKey,
                privateKey = wgPrivateKeyB64,
                localIp = PROTON_CLIENT_IP,
                dnsServer = activeDns,
                targetIp = targetIp,
                isIncludeMode = isIncludeMode,
                selectedApps = selectedApps,
                selectedIps = selectedIps,
                port = selectedPort,
                certificate = currentSession.wgCertificate,
                obfuscationParams = params
            )
            ProtonLogger.d(TAG, "Connecting with AWG config:\n$configStr")

            systemContextWrapper.startVpnService(
                configStr = configStr,
                notificationsEnabled = settingsManager.notificationsEnabled.first(),
                killSwitchEnabled = settingsManager.killSwitchEnabled.first(),
                excludedApps = selectedApps,
                excludedIps = selectedIps
            )

            Result.success(Unit)
        } catch (e: Exception) {
            _isConnecting.value = false
            _tunnelState.value = Tunnel.State.DOWN
            Result.failure(e)
        }
    }

    fun reconnect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null,
        logicalServer: LogicalServer? = null
    ) {
        // Only skip if we're already connecting (to avoid multiple rapid clicks)
        if (_isConnecting.value) {
            ProtonLogger.d(TAG, "Reconnect skipped: Already in a connecting state.")
            return
        }

        connectionJob?.cancel()
        connectionJob = applicationScope.launch {
            try {
                isReconnecting = true
                _isConnecting.value = true
                currentServerId = logicalServerId

                // Resolve logical server if not provided
                if (logicalServer != null) {
                    connectedServerState.setConnectedServer(logicalServer)
                } else if (connectedServerState.connectedServer.value?.id != logicalServerId) {
                    val resolved = vpnRepositoryProvider.get().getCachedServers().find { it.id == logicalServerId }
                    connectedServerState.setConnectedServer(resolved)
                }

                disconnectInternal()
                try {
                    withTimeout(5000) {
                        _rawTunnelState.first { it == Tunnel.State.DOWN }
                    }
                } catch (_: Exception) {
                }
                delay(500)
                connectInternal(logicalServerId, server, session, overridePort, overrideObfuscation, obfuscationParams)
            } finally {
                isReconnecting = false
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        applicationScope.launch {
            isReconnecting = false
            currentServerId = null
            disconnectInternal()
        }
    }

    private suspend fun disconnectInternal() = withContext(dispatcherProvider.io()) {
        systemContextWrapper.stopVpnService()
    }
}
