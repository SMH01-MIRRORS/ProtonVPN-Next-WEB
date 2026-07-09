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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.vpn.IpSubnetCalculator
import javax.inject.Inject

data class IpEntry(
    val ip: String,
    val isValid: Boolean = true
)

data class SplitTunnelingIpsUiState(
    val ips: List<IpEntry> = emptyList(),
    val newIpInput: String = "",
    val splitTunnelingMode: String = "exclude"
)

@HiltViewModel
class SplitTunnelingIpsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val ipSubnetCalculator: IpSubnetCalculator
) : ViewModel() {

    val uiState: StateFlow<SplitTunnelingIpsUiState> = combine(
        settingsManager.excludedIps,
        settingsManager.splitTunnelingMode
    ) { excludedIps, mode ->
        SplitTunnelingIpsUiState(
            ips = excludedIps.map { IpEntry(ip = it, isValid = true) }
                .sortedBy { it.ip },
            splitTunnelingMode = mode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SplitTunnelingIpsUiState()
    )

    fun addIp(ip: String) {
        val trimmedIp = ip.trim()
        if (ipSubnetCalculator.isValidIpOrCidr(trimmedIp)) {
            viewModelScope.launch {
                val current = settingsManager.excludedIps.stateIn(viewModelScope).value
                val normalizedIp = ipSubnetCalculator.normalizeIp(trimmedIp)
                if (normalizedIp !in current) {
                    settingsManager.setExcludedIps(current + normalizedIp)
                }
            }
        }
    }

    fun removeIp(ip: String) {
        viewModelScope.launch {
            val current = settingsManager.excludedIps.stateIn(viewModelScope).value
            settingsManager.setExcludedIps(current - ip)
        }
    }
}
