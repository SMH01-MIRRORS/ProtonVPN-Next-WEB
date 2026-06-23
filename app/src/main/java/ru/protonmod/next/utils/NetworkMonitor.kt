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

package ru.protonmod.next.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive

    private val _networkChanged = MutableStateFlow(0L)
    /**
     * Emits the current timestamp whenever the network connectivity (internet) becomes available
     * or a significant network transition occurs.
     */
    val networkChanged: StateFlow<Long> = _networkChanged

    private var lastInternetState = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val isAvailable = checkNetworkAvailability()
            _isNetworkAvailable.value = isAvailable
            if (isAvailable && !lastInternetState) {
                lastInternetState = true
                _networkChanged.value = System.currentTimeMillis()
            }
        }

        override fun onLost(network: Network) {
            val isAvailable = checkNetworkAvailability()
            _isNetworkAvailable.value = isAvailable
            if (!isAvailable) {
                lastInternetState = false
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            
            _isNetworkAvailable.value = hasInternet
            _isVpnActive.value = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            
            // Only trigger a network change event if we just gained internet access.
            // This prevents "signal strength" or other capability updates from
            // triggering infinite server refresh loops.
            if (hasInternet && !lastInternetState) {
                lastInternetState = true
                _networkChanged.value = System.currentTimeMillis()
            } else if (!hasInternet) {
                lastInternetState = false
            }
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        val initialInternet = checkNetworkAvailability()
        _isNetworkAvailable.value = initialInternet
        lastInternetState = initialInternet
        
        _isVpnActive.value = checkVpnAvailability()
    }

    private fun checkNetworkAvailability(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkVpnAvailability(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
