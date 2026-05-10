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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import ru.protonmod.next.utils.ProtonLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.local.RecentConnectionDao
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnAutomationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val settingsManager: SettingsManager,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val recentConnectionDao: RecentConnectionDao,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val TAG = "VpnAutomationManager"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var lastHandledNetworkName: String? = null
    private var debounceJob: kotlinx.coroutines.Job? = null

    init {
        applicationScope.launch {
            // Monitor Pause state with a real timer
            settingsManager.pauseEndTime.collectLatest { endTime ->
                val now = System.currentTimeMillis()
                if (endTime > now) {
                    val delayMs = endTime - now
                    ProtonLogger.d(TAG, "VPN is paused. Waiting ${delayMs}ms to auto-resume.")
                    delay(delayMs)
                    
                    // Final check: did someone manually resume or change the timer?
                    val currentEndTime = settingsManager.pauseEndTime.first()
                    if (currentEndTime != 0L && currentEndTime <= System.currentTimeMillis()) {
                        ProtonLogger.i(TAG, "Pause expired, auto-resuming...")
                        lastHandledNetworkName = null // Clear to allow trigger
                        amneziaVpnManager.resumeVpn()
                        triggerAutoConnect()
                    }
                } else if (endTime > 0) {
                    // Already expired but not cleared (e.g. app just started)
                    // Add a small safety delay to avoid race condition on immediate pause calls
                    delay(500)
                    val currentEndTime = settingsManager.pauseEndTime.first()
                    if (currentEndTime > 0 && currentEndTime <= System.currentTimeMillis()) {
                        ProtonLogger.i(TAG, "Detected expired pause, clearing and resuming...")
                        lastHandledNetworkName = null // Clear to allow trigger
                        amneziaVpnManager.resumeVpn()
                        triggerAutoConnect()
                    }
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val name = if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    getSsid(capabilities)
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    getCarrierName()
                } else null

                if (name != null) {
                    debounceNetworkChange(name)
                }
            }

            override fun onLost(network: Network) {
                // Potential to handle auto-connect on mobile data here
            }
        })
    }

    private fun debounceNetworkChange(networkName: String) {
        debounceJob?.cancel()
        debounceJob = applicationScope.launch {
            delay(1000) // Debounce for 1 second to let network stabilize
            handleNetworkChanged(networkName)
        }
    }

    private fun getSsid(capabilities: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = capabilities.transportInfo as? WifiInfo
            return wifiInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
        } else {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            return wifiManager.connectionInfo.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
        }
    }

    private fun getCarrierName(): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.networkOperatorName?.takeIf { it.isNotBlank() }
    }

    suspend fun resumeVpn() {
        ProtonLogger.i(TAG, "resumeVpn: Manually requested resumption.")
        lastHandledNetworkName = null // Force re-evaluation of current network
        amneziaVpnManager.resumeVpn()
        triggerAutoConnect()
    }

    private fun handleNetworkChanged(networkName: String) {
        if (networkName == lastHandledNetworkName) {
            // Already handled this network, and it hasn't changed.
            // Only re-handle if VPN is unexpectedly down.
            if (amneziaVpnManager.tunnelState.value == Tunnel.State.UP || 
                amneziaVpnManager.vpnState.value == AmneziaVpnManager.VpnState.CONNECTING) {
                return
            }
        }

        lastHandledNetworkName = networkName

        applicationScope.launch {
            val isEnabled = settingsManager.autoConnectOnUntrusted.first()
            if (!isEnabled) return@launch

            // Don't automate if VPN is currently paused
            val pauseEndTime = settingsManager.pauseEndTime.first()
            if (pauseEndTime > System.currentTimeMillis()) {
                ProtonLogger.d(TAG, "Network changed to $networkName but VPN is paused. Skipping automation.")
                return@launch
            }

            val trustedNetworks = settingsManager.trustedWifiNetworks.first()
            val isTrusted = trustedNetworks.contains(networkName)
            val isVpnConnected = amneziaVpnManager.tunnelState.value == Tunnel.State.UP
            val isConnecting = amneziaVpnManager.vpnState.value == AmneziaVpnManager.VpnState.CONNECTING ||
                               amneziaVpnManager.vpnState.value == AmneziaVpnManager.VpnState.VERIFYING

            ProtonLogger.d(TAG, "Network changed to $networkName (isTrusted=$isTrusted, vpnActive=$isVpnConnected, isConnecting=$isConnecting)")

            if (isTrusted && (isVpnConnected || isConnecting)) {
                ProtonLogger.i(TAG, "Connected to trusted network $networkName. Disconnecting VPN.")
                amneziaVpnManager.disconnect()
            } else if (!isTrusted && !isVpnConnected && !isConnecting) {
                ProtonLogger.i(TAG, "Connected to untrusted network $networkName. Connecting VPN.")
                triggerAutoConnect()
            }
        }
    }

    private suspend fun triggerAutoConnect() {
        // Don't auto-connect if paused
        val pauseEndTime = settingsManager.pauseEndTime.first()
        if (pauseEndTime > System.currentTimeMillis()) {
            ProtonLogger.d(TAG, "triggerAutoConnect: Still paused until $pauseEndTime. Skipping.")
            return
        }

        val session = sessionDao.getSession() ?: run {
            ProtonLogger.w(TAG, "triggerAutoConnect: No active session found.")
            return
        }
        
        val servers = vpnRepository.getCachedServers()
        if (servers.isEmpty()) {
            ProtonLogger.w(TAG, "triggerAutoConnect: Server cache is empty.")
            return
        }

        val recent = recentConnectionDao.getRecentConnections().first().firstOrNull()
        val targetServer = if (recent != null) {
            servers.find { it.id == recent.serverId } ?: servers.minByOrNull { it.averageLoad }
        } else {
            servers.minByOrNull { it.averageLoad }
        }

        if (targetServer == null) {
            ProtonLogger.w(TAG, "triggerAutoConnect: Could not determine target server.")
            return
        }

        val physicalServer = targetServer.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: targetServer.servers.minByOrNull { it.load }

        if (physicalServer == null) {
            ProtonLogger.w(TAG, "triggerAutoConnect: No physical servers available for ${targetServer.name}")
            return
        }

        ProtonLogger.i(TAG, "triggerAutoConnect: Initiating connection to ${targetServer.name}")
        amneziaVpnManager.connect(targetServer.id, physicalServer, session, logicalServer = targetServer)
    }
}
