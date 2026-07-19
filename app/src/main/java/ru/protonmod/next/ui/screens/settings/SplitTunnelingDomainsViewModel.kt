/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it under the terms of the
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.vpn.SplitTunnelingDomainRule
import javax.inject.Inject

data class DomainEntry(
    val domain: String,
    val displayDomain: String = domain,
    val isValid: Boolean = true
)

data class SplitTunnelingDomainsUiState(
    val domains: List<DomainEntry> = emptyList(),
    val splitTunnelingMode: String = "exclude"
)

@HiltViewModel
class SplitTunnelingDomainsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val uiState: StateFlow<SplitTunnelingDomainsUiState> = combine(
        settingsManager.excludedDomains,
        settingsManager.splitTunnelingMode
    ) { excludedDomains, mode ->
        SplitTunnelingDomainsUiState(
            domains = excludedDomains.map {
                DomainEntry(
                    domain = it,
                    displayDomain = SplitTunnelingDomainRule.toDisplay(it),
                    isValid = true
                )
            }.sortedBy { it.displayDomain },
            splitTunnelingMode = mode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SplitTunnelingDomainsUiState()
    )

    fun addDomain(domain: String): Boolean {
        val normalizedRule = SplitTunnelingDomainRule.normalize(domain) ?: return false
        viewModelScope.launch {
            val current = settingsManager.excludedDomains.first()
            if (normalizedRule !in current) {
                settingsManager.setExcludedDomains(current + normalizedRule)
            }
        }
        return true
    }

    fun removeDomain(domain: String) {
        viewModelScope.launch {
            val current = settingsManager.excludedDomains.first()
            settingsManager.setExcludedDomains(current - domain)
        }
    }
}
