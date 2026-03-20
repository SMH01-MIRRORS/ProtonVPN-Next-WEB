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
import android.util.Log
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
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.di.ApplicationScope
import java.io.ByteArrayInputStream
import java.net.InetAddress
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
                        if (!(isReconnecting && newState == Tunnel.State.DOWN)) {
                            _tunnelState.value = newState
                            if (newState == Tunnel.State.UP) {
                                checkAndRefreshCertificateProactively()
                            }
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

    private suspend fun performCertificateRefresh(): Result<String> = refreshMutex.withLock {
        val currentSession = sessionDao.getSession() ?: return Result.failure(Exception("No session"))
        updateCertificateState(currentSession.wgCertificate)

        if (_certState.value is CertificateState.Valid) {
            return Result.success(currentSession.wgCertificate ?: "")
        }

        val previousState = _certState.value
        _certState.value = CertificateState.Refreshing
        Log.d(TAG, "Refreshing certificate (previous state: $previousState)")

        val result = vpnRepositoryProvider.get().registerWireGuardKey(
            accessToken = currentSession.accessToken,
            sessionId = currentSession.sessionId,
            publicKeyPem = currentSession.wgPublicKeyPem ?: ""
        )
        return if (result.isSuccess) {
            val newCert = result.getOrNull()?.certificate
            if (newCert != null) {
                sessionDao.updateCertificate(newCert)
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

                Log.d(TAG, "Proactive refresh starting (cert state: ${_certState.value})")
                val result = performCertificateRefresh()
                
                if (result.isSuccess) {
                    currentRetryDelay = 5000L
                    delay(PERIODIC_REFRESH_MS)
                } else {
                    // API access is expected to be preserved, so we retry with backoff.
                    // This covers cases where internet is temporarily down.
                    Log.w(TAG, "Proactive refresh failed, retrying in ${currentRetryDelay}ms")
                    delay(currentRetryDelay)
                    currentRetryDelay = (currentRetryDelay * 2).coerceAtMost(RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun isEffectivelyExpired(): Boolean {
        val state = _certState.value
        return state is CertificateState.Expired || (state is CertificateState.RefreshFailed && state.isFullyExpired)
    }

    private suspend fun updateServiceSettings() {
        val intent = Intent(ProtonVpnService.ACTION_UPDATE_SETTINGS).apply {
            setPackage(context.packageName)
            putExtra(ProtonVpnService.EXTRA_NOTIFICATIONS_ENABLED, settingsManager.notificationsEnabled.first())
            putExtra(ProtonVpnService.EXTRA_KILL_SWITCH_ENABLED, settingsManager.killSwitchEnabled.first())
        }
        context.sendBroadcast(intent)
    }

    fun connect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null
    ) {
        connectionJob?.cancel()
        connectionJob = applicationScope.launch {
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isConnecting.value = true
            var currentSession = session

            // Proactively refresh certificate if it's not valid (Expired or ExpiringSoon)
            updateCertificateState(currentSession.wgCertificate)
            if (_certState.value !is CertificateState.Valid) {
                Log.d(TAG, "Certificate state is ${_certState.value}, attempting refresh before connection.")
                performCertificateRefresh()

                if (isEffectivelyExpired()) {
                    // Even if expired, we try to connect because Proton API might be reachable 
                    // and some servers might still accept the old key for a short grace period.
                    // But we keep the UI warning.
                    Log.w(TAG, "Certificate is expired. Proceeding with connection anyway as Proton API is accessible.")
                }
                
                // Refresh session from DB to get the new certificate and any other potential updates
                currentSession = sessionDao.getSession() ?: currentSession
            }

            val wgPrivateKeyB64 = currentSession.wgPrivateKey ?: throw Exception("Offline VPN private key missing!")
            var targetIp: String? = null
            for (i in 1..DNS_RETRY_COUNT) {
                try {
                    targetIp = InetAddress.getByName(server.domain).hostAddress
                    if (targetIp != null) break
                } catch (e: Exception) {
                    if (i < DNS_RETRY_COUNT) delay(DNS_RETRY_DELAY_MS)
                }
            }

            if (targetIp == null) {
                _isConnecting.value = false
                _tunnelState.value = Tunnel.State.DOWN
                throw Exception("DNS resolution failed for ${server.domain}")
            }

            val serverPubKey = server.wgPublicKey ?: throw Exception("Missing WG Public Key for Server")
            val splitTunnelingEnabled = settingsManager.splitTunnelingEnabled.first()
            val excludedApps = if (splitTunnelingEnabled) settingsManager.excludedApps.first() else emptySet()
            val excludedIps = if (splitTunnelingEnabled) settingsManager.excludedIps.first() else emptySet()
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
            Log.d(TAG, "Using DNS Server: $activeDns")

            val config = buildAwgConfig(
                serverPublicKey = serverPubKey, privateKey = wgPrivateKeyB64, localIp = PROTON_CLIENT_IP, dnsServer = activeDns,
                targetIp = targetIp, excludedApps = excludedApps, excludedIps = excludedIps, port = selectedPort,
                jc = params.jc, jmin = params.jmin, jmax = params.jmax, s1 = params.s1, s2 = params.s2,
                h1 = params.h1, h2 = params.h2, h3 = params.h3, h4 = params.h4,
                i1 = params.i1, i2 = params.i2, i3 = params.i3, i4 = params.i4, i5 = params.i5,
                customId = params.customId, ip = params.ip, ib = params.ib
            )

            val configStr = config.toAwgQuickString(false, false)
            Log.d(TAG, "Connecting with AWG config:\n$configStr")

            val intent = Intent(context, ProtonVpnService::class.java).apply {
                action = ProtonVpnService.ACTION_CONNECT
                putExtra(ProtonVpnService.EXTRA_CONFIG, configStr)
                putExtra(ProtonVpnService.EXTRA_NOTIFICATIONS_ENABLED, settingsManager.notificationsEnabled.first())
                putExtra(ProtonVpnService.EXTRA_KILL_SWITCH_ENABLED, settingsManager.killSwitchEnabled.first())
                putStringArrayListExtra(ProtonVpnService.EXTRA_EXCLUDED_APPS, ArrayList(excludedApps))
                putStringArrayListExtra(ProtonVpnService.EXTRA_EXCLUDED_IPS, ArrayList(excludedIps))
            }
            context.startService(intent)

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
        obfuscationParams: ObfuscationParams? = null
    ) {
        connectionJob?.cancel()
        connectionJob = applicationScope.launch {
            isReconnecting = true
            _isConnecting.value = true
            disconnectInternal()
            try { withTimeout(5000) { _rawTunnelState.first { it == Tunnel.State.DOWN } } } catch (_: Exception) {}
            delay(500)
            isReconnecting = false
            connectInternal(logicalServerId, server, session, overridePort, overrideObfuscation, obfuscationParams)
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        applicationScope.launch {
            isReconnecting = false
            disconnectInternal()
        }
    }

    private suspend fun disconnectInternal() = withContext(Dispatchers.IO) {
        val intent = Intent(context, ProtonVpnService::class.java).apply {
            action = ProtonVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    private fun buildAwgConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        excludedApps: Set<String> = emptySet(),
        excludedIps: Set<String> = emptySet(),
        port: Int = 1194,
        jc: Int = 3, jmin: Int = 1, jmax: Int = 3,
        s1: Int = 0, s2: Int = 0,
        h1: String = "1", h2: String = "2", h3: String = "3", h4: String = "4",
        i1: String = "", i2: String = "", i3: String = "", i4: String = "", i5: String = "",
        customId: String = "", ip: String = "", ib: String = ""
    ): Config {
        val allowedIpsList = if (excludedIps.isEmpty()) listOf("0.0.0.0/0") else IpSubnetCalculator.complementOfExcluded(excludedIps)
        val peer = Peer.Builder()
            .parsePublicKey(serverPublicKey)
            .parseEndpoint("$targetIp:$port")
            .apply {
                if (allowedIpsList.isEmpty()) parseAllowedIPs("0.0.0.0/0") else allowedIpsList.forEach { parseAllowedIPs(it) }
            }
            .setPersistentKeepalive(60)
            .build()

        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(privateKey)
            .parseAddresses("$localIp/32")
            // WireGuard accepts the custom DNS directly here
            .parseDnsServers(dnsServer)
            .setMtu(1280)
            .setJunkPacketCount(jc)
            .setJunkPacketMinSize(jmin)
            .setJunkPacketMaxSize(jmax)
            .setInitPacketJunkSize(s1)
            .setResponsePacketJunkSize(s2)
            .apply {
                if (h1.isNotEmpty()) setInitPacketMagicHeader(h1)
                if (h2.isNotEmpty()) setResponsePacketMagicHeader(h2)
                if (h3.isNotEmpty()) setUnderloadPacketMagicHeader(h3)
                if (h4.isNotEmpty()) setTransportPacketMagicHeader(h4)
            }

        if (i1.isNotEmpty()) ifaceBuilder.parseSpecialJunkI1(i1)
        // Note: I2-I5, Id, Ip, Ib support depends on the underlying awg-android library version.
        // We attempt to call them if they exist in the SDK.
        // For now, only I1 is explicitly supported in the provided builder snippet.

        if (excludedApps.isNotEmpty()) ifaceBuilder.parseExcludedApplications(excludedApps.joinToString(","))

        return Config.Builder().setInterface(ifaceBuilder.build()).addPeer(peer).build()
    }
}
