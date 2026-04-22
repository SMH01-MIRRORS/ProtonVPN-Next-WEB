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

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors the network capabilities to detect if the connection is validated.
 * This is used to ensure the VPN tunnel is actually passing traffic before
 * marking it as "Connected".
 */
@Singleton
class VpnNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isValidated = MutableStateFlow(false)
    val isValidated: StateFlow<Boolean> = _isValidated.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                // On older versions, we can't easily check for validation this way
                true 
            }
            
            val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            
            if (isVpn) {
                if (_isValidated.value != validated) {
                    ProtonLogger.d("VpnNetworkMonitor", "VPN Network validation state changed: $validated")
                    _isValidated.value = validated
                }
            }
        }

        override fun onLost(network: Network) {
            // Check if any VPN network is still available
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isVpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            
            if (!isVpnActive) {
                _isValidated.value = false
            }
        }
    }

    init {
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            ProtonLogger.e("VpnNetworkMonitor", "Failed to register network callback", e)
        }
    }
}
