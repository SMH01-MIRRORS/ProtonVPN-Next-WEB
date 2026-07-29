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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.di.ApplicationScope
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user that connection settings edited during an active session only take effect after a
 * reconnect. Settings that are pushed to the running service never reach this manager, so the
 * notice appears exclusively for configuration baked into the tunnel at connection time.
 */
@Singleton
class ReconnectPromptManager @Inject constructor(
    private val settingsManager: SettingsManager,
    private val amneziaVpnManager: AmneziaVpnManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    data class State(
        val isVisible: Boolean = false,
        /** Whether the tunnel can be restarted from here, or the user has to do it manually. */
        val canReconnect: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Set when the user postpones the reconnect: further edits stay silent until the tunnel is
     * restarted, so that reworking a whole settings screen does not raise the notice repeatedly.
     */
    private var postponedUntilReconnect = false

    init {
        applicationScope.launch {
            amneziaVpnManager.tunnelState.collect { tunnelState ->
                if (tunnelState != VpnTunnelState.UP) {
                    postponedUntilReconnect = false
                    _state.value = State()
                }
            }
        }

        applicationScope.launch {
            settingsManager.connectionConfigChanged.collect {
                if (amneziaVpnManager.tunnelState.value != VpnTunnelState.UP) return@collect
                if (postponedUntilReconnect) return@collect
                if (!settingsManager.reconnectHintEnabled.first()) return@collect

                ProtonLogger.d(TAG, "Connection settings changed while connected, asking to reconnect")
                _state.value = State(
                    isVisible = true,
                    canReconnect = amneziaVpnManager.canReconnectCurrent()
                )
            }
        }
    }

    /** Keeps the current tunnel; the user reconnects whenever they are done changing settings. */
    fun postpone() {
        postponedUntilReconnect = true
        _state.value = _state.value.copy(isVisible = false)
    }

    fun reconnectNow() {
        _state.value = _state.value.copy(isVisible = false)
        amneziaVpnManager.reconnectCurrent()
    }

    /** Hides the notice for good, from the "do not show again" option inside the dialog. */
    fun disablePrompt() {
        postponedUntilReconnect = true
        _state.value = _state.value.copy(isVisible = false)
        applicationScope.launch { settingsManager.setReconnectHintEnabled(false) }
    }

    private companion object {
        const val TAG = "ReconnectPrompt"
    }
}
