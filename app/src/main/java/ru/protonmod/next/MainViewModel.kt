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

package ru.protonmod.next

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.SetupStep
import ru.protonmod.next.ui.nav.Screen
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.vpn.ReconnectPromptManager
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val settingsManager: SettingsManager,
    private val reconnectPromptManager: ReconnectPromptManager
) : ViewModel() {
    private val _startDestination = MutableStateFlow("")
    val startDestination: StateFlow<String> = _startDestination.asStateFlow()

    val reconnectPrompt: StateFlow<ReconnectPromptManager.State> = reconnectPromptManager.state

    fun postponeReconnect() = reconnectPromptManager.postpone()

    fun reconnectNow() = reconnectPromptManager.reconnectNow()

    fun disableReconnectPrompt() = reconnectPromptManager.disablePrompt()

    val session = sessionDao.getSessionFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val appTheme: StateFlow<AppTheme> = settingsManager.appTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = settingsManager.defaultTheme
        )

    init {
        viewModelScope.launch {
            val acceptedVersion = settingsManager.policyAcceptedVersion.first()
            val session = sessionDao.getSession()
            val hasSession = session != null && session.accessToken.isNotEmpty()

            // If the user has a session but hasn't accepted the CURRENT policy, show the acceptance screen.
            // This target existing users during an update.
            if (hasSession && acceptedVersion < SettingsManager.CURRENT_POLICY_VERSION) {
                ProtonLogger.d("MainViewModel", "Existing user with session needs to accept policy.")
                _startDestination.value = Screen.PolicyAcceptance.route
                return@launch
            }

            // For new users (no session), we automatically mark the policy as accepted 
            // since they agree to it by continuing from the Welcome screen.
            if (!hasSession && acceptedVersion < SettingsManager.CURRENT_POLICY_VERSION) {
                ProtonLogger.d("MainViewModel", "New user, auto-accepting policy version.")
                settingsManager.setPolicyAcceptedVersion(SettingsManager.CURRENT_POLICY_VERSION)
            }

            val step = settingsManager.setupStep.first()

            if (hasSession && step == SetupStep.COMPLETE) {
                ProtonLogger.d("MainViewModel", "User logged in and setup complete, going home.")
                _startDestination.value = Screen.Home.route
            } else {
                ProtonLogger.d("MainViewModel", "No session or setup incomplete, going to welcome.")
                _startDestination.value = "welcome"
            }
        }
    }

    fun acceptPolicy() {
        ProtonLogger.d("MainViewModel", "acceptPolicy() called")
        viewModelScope.launch {
            try {
                settingsManager.setPolicyAcceptedVersion(SettingsManager.CURRENT_POLICY_VERSION)
                val session = sessionDao.getSession()
                val nextDestination = if (session != null && session.accessToken.isNotEmpty()) {
                    Screen.Home.route
                } else {
                    "welcome"
                }
                ProtonLogger.d("MainViewModel", "Setting startDestination to: $nextDestination")
                _startDestination.value = nextDestination
            } catch (e: Exception) {
                ProtonLogger.e("MainViewModel", "Error in acceptPolicy", e)
            }
        }
    }
}
