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

package ru.protonmod.next.ui.screens.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

data class SettingsUiState(
    val killSwitchEnabled: Boolean = false,
    val autoConnectEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,

    // Connection configs
    val splitTunnelingEnabled: Boolean = false,
    val excludedApps: Set<String> = emptySet(),
    val excludedIps: Set<String> = emptySet(),
    val vpnPort: Int = 1194,

    // API Bypass Feature
    val apiBypassEnabled: Boolean = false,
    val apiBypassStrategy: String = "netlify",
    val isAnyVpnActive: Boolean = false,

    // AWG low-level params
    val awgJc: Int = 3,
    val awgJmin: Int = 1,
    val awgJmax: Int = 3,
    val awgS1: Int = 0,
    val awgS2: Int = 0,
    val awgH1: String = "1",
    val awgH2: String = "2",
    val awgH3: String = "3",
    val awgH4: String = "4",
    val awgI1: String = SettingsManager.DEFAULT_I1,

    // States
    val isVpnConnected: Boolean = false,

    // Obfuscation configuration state
    val isObfuscationEnabled: Boolean = false,
    val customObfuscationProfiles: List<ObfuscationProfile> = emptyList(),
    val selectedProfileId: String = "standard_1",
    val customDns: String = "",

    // Privacy & Analytics
    val isAnalyticsEnabled: Boolean = true,
    val isCrashReportsEnabled: Boolean = true
)

@HiltViewModel
@Suppress("UNCHECKED_CAST")
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    amneziaVpnManager: AmneziaVpnManager,
    private val settingsManager: SettingsManager
) : ViewModel() {

    // Internal state tracking if any VPN is operating at the OS level
    private val _isAnyVpnActive = MutableStateFlow(false)

    init {
        // Monitor system networks to automatically detect active VPN connections
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isAnyVpnActive.value = true
            }
            override fun onLost(network: Network) {
                _isAnyVpnActive.value = false
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)

            // Initial synchronous check
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            _isAnyVpnActive.value = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } catch (e: Exception) {
            // Ignore if missing permissions in some edge cases
        }
    }

    // Using array combine to bypass the 5 Flow limit in coroutines
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsManager.killSwitchEnabled,
        settingsManager.autoConnectEnabled,
        settingsManager.notificationsEnabled,
        settingsManager.splitTunnelingEnabled,
        settingsManager.excludedApps,
        settingsManager.excludedIps,
        settingsManager.vpnPort,
        settingsManager.awgJc,
        settingsManager.awgJmin,
        settingsManager.awgJmax,
        settingsManager.awgS1,
        settingsManager.awgS2,
        settingsManager.awgH1,
        settingsManager.awgH2,
        settingsManager.awgH3,
        settingsManager.awgH4,
        settingsManager.awgI1,
        amneziaVpnManager.tunnelState,
        settingsManager.obfuscationEnabled,
        settingsManager.customProfiles,
        settingsManager.selectedProfileId,
        settingsManager.customDns,
        settingsManager.analyticsEnabled,
        settingsManager.crashReportsEnabled,
        settingsManager.apiBypassEnabled,
        settingsManager.apiBypassStrategy,
        _isAnyVpnActive
    ) { args: Array<Any?> ->
        SettingsUiState(
            killSwitchEnabled = args[0] as Boolean,
            autoConnectEnabled = args[1] as Boolean,
            notificationsEnabled = args[2] as Boolean,
            splitTunnelingEnabled = args[3] as Boolean,
            excludedApps = args[4] as Set<String>,
            excludedIps = args[5] as Set<String>,
            vpnPort = args[6] as Int,
            awgJc = args[7] as Int,
            awgJmin = args[8] as Int,
            awgJmax = args[9] as Int,
            awgS1 = args[10] as Int,
            awgS2 = args[11] as Int,
            awgH1 = args[12] as String,
            awgH2 = args[13] as String,
            awgH3 = args[14] as String,
            awgH4 = args[15] as String,
            awgI1 = args[16] as String,
            isVpnConnected = args[17] == Tunnel.State.UP,
            isObfuscationEnabled = args[18] as Boolean,
            customObfuscationProfiles = args[19] as List<ObfuscationProfile>,
            selectedProfileId = args[20] as String,
            customDns = args[21] as String,
            isAnalyticsEnabled = args[22] as Boolean,
            isCrashReportsEnabled = args[23] as Boolean,
            apiBypassEnabled = args[24] as Boolean,
            apiBypassStrategy = args[25] as String,
            isAnyVpnActive = args[26] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAutoConnect(enabled)
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setNotifications(enabled)
        }
    }

    fun setSplitTunneling(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSplitTunnelingEnabled(enabled)
        }
    }

    fun setVpnPort(port: Int) {
        viewModelScope.launch {
            settingsManager.setVpnPort(port)
        }
    }

    fun setCustomDns(dns: String) {
        viewModelScope.launch {
            settingsManager.setCustomDns(dns)
        }
    }

    fun setApiBypassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setApiBypassEnabled(enabled)
        }
    }

    fun setApiBypassStrategy(strategy: String) {
        viewModelScope.launch {
            settingsManager.setApiBypassStrategy(strategy)
        }
    }

    fun setObfuscationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setObfuscationEnabled(enabled)
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAnalyticsEnabled(enabled)
        }
    }

    fun setCrashReportsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setCrashReportsEnabled(enabled)
        }
    }

    fun setAwgParams(
        jc: Int, jmin: Int, jmax: Int, s1: Int, s2: Int,
        h1: String, h2: String, h3: String, h4: String, i1: String
    ) {
        viewModelScope.launch {
            settingsManager.setAwgParams(jc, jmin, jmax, s1, s2, h1, h2, h3, h4, i1)
        }
    }

    fun selectObfuscationProfile(profile: ObfuscationProfile) {
        viewModelScope.launch {
            settingsManager.setSelectedProfileId(profile.id)
            setAwgParams(
                jc = profile.jc, jmin = profile.jmin, jmax = profile.jmax,
                s1 = profile.s1, s2 = profile.s2,
                h1 = profile.h1, h2 = profile.h2, h3 = profile.h3, h4 = profile.h4,
                i1 = profile.i1
            )
        }
    }

    fun saveObfuscationProfile(profile: ObfuscationProfile) {
        viewModelScope.launch {
            val currentList = uiState.value.customObfuscationProfiles
            val index = currentList.indexOfFirst { it.id == profile.id }
            val newList = if (index != -1) {
                currentList.toMutableList().apply { this[index] = profile }
            } else {
                currentList + profile
            }
            settingsManager.saveCustomProfiles(newList)
            selectObfuscationProfile(profile)
        }
    }

    fun resetToStandard() {
        val standard = ObfuscationProfile.getStandardProfile()
        selectObfuscationProfile(standard)
    }
}
