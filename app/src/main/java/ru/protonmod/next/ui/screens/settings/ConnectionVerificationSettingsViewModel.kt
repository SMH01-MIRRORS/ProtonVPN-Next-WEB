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
