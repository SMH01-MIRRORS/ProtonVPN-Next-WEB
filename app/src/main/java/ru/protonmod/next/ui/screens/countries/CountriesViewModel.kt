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

package ru.protonmod.next.ui.screens.countries

import ru.protonmod.next.utils.ProtonLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

data class CountryDisplayItem(val code: String, val averageLoad: Int)
data class CityDisplayItem(val name: String, val averageLoad: Int)

sealed class CountriesUiState {
    data object Loading : CountriesUiState()
    data class CountriesList(val countries: List<CountryDisplayItem>) : CountriesUiState()
    data class CitiesList(
        val country: String,
        val cities: List<CityDisplayItem>
    ) : CountriesUiState()
    data class ServersList(
        val country: String,
        val city: String,
        val servers: List<LogicalServer>
    ) : CountriesUiState()
    data class Error(val message: String) : CountriesUiState()
}

sealed class NavigationState {
    data object Countries : NavigationState()
    data class Cities(val countryCode: String) : NavigationState()
    data class Servers(val countryCode: String, val cityName: String) : NavigationState()
}

@HiltViewModel
class CountriesViewModel @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val connectedServerState: ConnectedServerState
) : ViewModel() {

    companion object {
        private const val TAG = "CountriesViewModel"
    }

    private val _navState = MutableStateFlow<NavigationState>(NavigationState.Countries)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CountriesUiState> = combine(
        vpnRepository.getServersFlow(),
        _navState,
        vpnRepository.isUpdating,
        _error
    ) { servers, nav, isUpdating, error ->
        if (isUpdating && servers.isEmpty()) {
            return@combine CountriesUiState.Loading
        }
        if (error != null && servers.isEmpty()) {
            return@combine CountriesUiState.Error(error)
        }

        when (nav) {
            is NavigationState.Countries -> {
                val countries = servers.groupBy { it.exitCountry }
                    .map { (code, countryServers) ->
                        val avg = if (countryServers.isEmpty()) 0 else countryServers.map { it.averageLoad }.average().toInt()
                        CountryDisplayItem(code, avg)
                    }
                    .sortedBy { it.code }
                CountriesUiState.CountriesList(countries)
            }
            is NavigationState.Cities -> {
                val cities = servers.filter { it.exitCountry == nav.countryCode }
                    .groupBy { it.city }
                    .map { (name, cityServers) ->
                        val avg = if (cityServers.isEmpty()) 0 else cityServers.map { it.averageLoad }.average().toInt()
                        CityDisplayItem(name, avg)
                    }
                    .sortedBy { it.name }
                CountriesUiState.CitiesList(nav.countryCode, cities)
            }
            is NavigationState.Servers -> {
                val cityServers = servers.filter { it.exitCountry == nav.countryCode && it.city == nav.cityName }
                    .sortedBy { it.name }
                CountriesUiState.ServersList(nav.countryCode, nav.cityName, cityServers)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CountriesUiState.Loading)

    val connectedServer: StateFlow<LogicalServer?> = connectedServerState.connectedServer

    init {
        initialFetch()
    }

    private fun initialFetch() {
        viewModelScope.launch {
            _error.value = null
            val session = sessionDao.getSession()
            if (session == null) {
                _error.value = "Session not found"
                return@launch
            }
            vpnRepository.getServers(session.accessToken, session.sessionId, session.userTier, forceRefresh = false)
                .onFailure { _error.value = it.localizedMessage }
        }
    }

    fun loadServers() {
        initialFetch()
    }

    private suspend fun connectToServer(server: LogicalServer) {
        val session = sessionDao.getSession()
        if (session == null) {
            ProtonLogger.e(TAG, "Cannot connect: No session found")
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
                amneziaVpnManager.reconnect(server.id, physicalServer, session)
            } else {
                amneziaVpnManager.connect(server.id, physicalServer, session)
            }
        } else {
            _error.value = "Selected server is currently unavailable."
        }
    }

    fun selectCountry(country: String) {
        viewModelScope.launch {
            val servers = vpnRepository.getCachedServers()
            val serversInCountry = servers.filter { it.exitCountry == country }
            if (serversInCountry.isNotEmpty()) {
                val bestServer = serversInCountry
                    .filter { it.servers.any { s -> s.status == 1 } }
                    .minByOrNull { it.averageLoad } 
                    ?: serversInCountry.minByOrNull { it.averageLoad }
                
                bestServer?.let { connectToServer(it) }
            }
        }
    }

    fun expandCitiesForCountry(country: String) {
        _navState.value = NavigationState.Cities(country)
    }

    fun backToCountries() {
        _navState.value = NavigationState.Countries
    }

    fun selectCity(city: String) {
        viewModelScope.launch {
            val nav = _navState.value
            if (nav !is NavigationState.Cities) return@launch

            val servers = vpnRepository.getCachedServers()
            val serversInCity = servers.filter { it.exitCountry == nav.countryCode && it.city == city }
            if (serversInCity.isNotEmpty()) {
                val bestServer = serversInCity
                    .filter { it.servers.any { s -> s.status == 1 } }
                    .minByOrNull { it.averageLoad }
                    ?: serversInCity.minByOrNull { it.averageLoad }
                    
                bestServer?.let { connectToServer(it) }
            }
        }
    }

    fun expandServersForCity(city: String) {
        val nav = _navState.value
        if (nav is NavigationState.Cities) {
            _navState.value = NavigationState.Servers(nav.countryCode, city)
        }
    }

    fun backToCities() {
        val nav = _navState.value
        if (nav is NavigationState.Servers) {
            _navState.value = NavigationState.Cities(nav.countryCode)
        }
    }

    fun selectServer(server: LogicalServer) {
        viewModelScope.launch {
            connectToServer(server)
        }
    }
}
