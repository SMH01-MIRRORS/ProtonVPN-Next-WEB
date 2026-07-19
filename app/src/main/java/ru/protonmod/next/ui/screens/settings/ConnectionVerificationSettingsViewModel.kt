/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import ru.protonmod.next.data.local.ConnectionVerificationMode
import ru.protonmod.next.data.local.SettingsManager
import javax.inject.Inject

data class ConnectionVerificationUiState(
    val mode: ConnectionVerificationMode = ConnectionVerificationMode.BALANCED,
    val requireVerification: Boolean = false,
    val requirePreflight: Boolean = false,
    val detectFailures: Boolean = true,
    val autoReconnect: Boolean = true,
)

@HiltViewModel
class ConnectionVerificationSettingsViewModel @Inject constructor(
    private val settings: SettingsManager,
) : ViewModel() {
    val uiState: StateFlow<ConnectionVerificationUiState> = combine(
        settings.connectionVerificationMode,
        settings.connectionVerificationRequired,
        settings.connectionPreflightRequired,
        settings.connectionFailureDetection,
        settings.connectionAutoReconnect,
    ) { mode, required, preflight, detection, reconnect ->
        ConnectionVerificationUiState(mode, required, preflight, detection, reconnect)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ConnectionVerificationUiState(),
    )

    fun setMode(mode: ConnectionVerificationMode) = viewModelScope.launch {
        settings.setConnectionVerificationMode(mode)
    }

    fun setRequireVerification(value: Boolean) = viewModelScope.launch {
        settings.setConnectionVerificationRequired(value)
    }

    fun setRequirePreflight(value: Boolean) = viewModelScope.launch {
        settings.setConnectionPreflightRequired(value)
    }

    fun setDetectFailures(value: Boolean) = viewModelScope.launch {
        settings.setConnectionFailureDetection(value)
    }

    fun setAutoReconnect(value: Boolean) = viewModelScope.launch {
        settings.setConnectionAutoReconnect(value)
    }
}
