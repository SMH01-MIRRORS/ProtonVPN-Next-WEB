/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.ui.screens.netshield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.netshield.LocalNetShield
import ru.protonmod.next.netshield.NetShieldLevel
import ru.protonmod.next.netshield.NetShieldListState
import javax.inject.Inject

data class NetShieldSettingsUiState(
    val level: NetShieldLevel = NetShieldLevel.DISABLED,
    val lists: NetShieldListState = NetShieldListState(),
)

@HiltViewModel
class NetShieldSettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val localNetShield: LocalNetShield,
) : ViewModel() {
    init {
        if (localNetShield.needsListUpdate) {
            viewModelScope.launch { localNetShield.updateLists() }
        }
    }

    val uiState: StateFlow<NetShieldSettingsUiState> = combine(
        settingsManager.netShieldLevel,
        localNetShield.listState,
    ) { level, lists -> NetShieldSettingsUiState(level, lists) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetShieldSettingsUiState())

    fun setLevel(level: NetShieldLevel) {
        viewModelScope.launch {
            settingsManager.setNetShieldLevel(level)
            if (level.enabled && localNetShield.listState.value.domainCount == 0 && !localNetShield.listState.value.isUpdating) {
                localNetShield.updateLists()
            }
        }
    }

    fun updateLists() {
        viewModelScope.launch { localNetShield.updateLists() }
    }
}
