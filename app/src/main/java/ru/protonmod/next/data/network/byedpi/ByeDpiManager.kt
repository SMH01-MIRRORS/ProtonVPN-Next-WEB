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

package ru.protonmod.next.data.network.byedpi

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.NetworkMonitor
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.utils.ShellUtils
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ByeDpiManager @Inject constructor(
    private val settingsManager: SettingsManager,
    private val networkMonitor: NetworkMonitor,
    private val vpnManagerProvider: Provider<AmneziaVpnManager>
) {

    private val proxy = ByeDpiProxy()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var proxyJob: Job? = null
    private val mutex = Mutex()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _isAutoManagementEnabled = MutableStateFlow(true)
    var isAutoManagementEnabled: Boolean
        get() = _isAutoManagementEnabled.value
        set(value) { _isAutoManagementEnabled.value = value }

    init {
        scope.launch {
            combine(
                settingsManager.apiBypassEnabled,
                settingsManager.apiBypassStrategy,
                settingsManager.byeDpiFlags,
                settingsManager.byeDpiSni,
                networkMonitor.isVpnActive,
                vpnManagerProvider.get().tunnelState,
                _isAutoManagementEnabled
            ) { args: Array<Any?> ->
                val enabled = args[0] as Boolean
                val strategy = args[1] as String
                val flags = args[2] as String
                val sni = args[3] as String
                val systemVpn = args[4] as Boolean
                val ourVpn = args[5] as Tunnel.State
                val autoManage = args[6] as Boolean

                if (!autoManage) {
                    return@combine
                }

                val shouldBeRunning = enabled && 
                        strategy == SettingsManager.STRATEGY_BYEDPI && 
                        !systemVpn && 
                        ourVpn != Tunnel.State.UP
                
                if (shouldBeRunning) {
                    val port = settingsManager.getApiProxyPortSync()
                    val argsList = prepareArgs(flags, sni, port)
                    start(argsList)
                } else {
                    stop()
                }
            }.collect()
        }
    }

    private fun prepareArgs(flags: String, sni: String, port: Int): Array<String> {
        val baseArgs = listOf("ciadpi", "--ip", "127.0.0.1", "--port", port.toString())
        val processedFlags = ShellUtils.shellSplit(flags.replace("{sni}", sni))
        return (baseArgs + processedFlags).toTypedArray()
    }

    suspend fun start(args: Array<String>) {
        mutex.withLock {
            if (_isRunning.value) {
                // If already running with same args, do nothing
                // But comparing args is complex, let's just restart if requested via start()
                // Auto-management will call start() if it decides it should be running.
                // We should probably check if it's already running with DIFFERENT args.
                // For simplicity, stopInternal always stops it.
                stopInternal()
            }

            val currentArgs = args
            proxyJob = scope.launch {
                _isRunning.value = true
                ProtonLogger.i("ByeDpiManager", "Starting ByeDPI with args: ${currentArgs.joinToString(" ")}")
                try {
                    val result = proxy.startProxy(currentArgs)
                    _isRunning.value = false
                    ProtonLogger.i("ByeDpiManager", "ByeDPI stopped with result: $result")
                } catch (e: Exception) {
                    _isRunning.value = false
                    ProtonLogger.e("ByeDpiManager", "ByeDPI crashed", e)
                } finally {
                    _isRunning.value = false
                    ProtonLogger.i("ByeDpiManager", "ByeDPI job finished")
                }
            }
            // Give it a tiny bit to actually start and update _isRunning
            delay(100)
        }
    }

    suspend fun stop() {
        mutex.withLock {
            stopInternal()
        }
    }

    private suspend fun stopInternal() {
        if (!_isRunning.value && proxyJob == null) return
        
        ProtonLogger.i("ByeDpiManager", "Stopping ByeDPI proxy")
        proxy.stopProxy()
        
        // Force close if it takes too long to respond to shutdown
        val forceCloseJob = scope.launch {
            delay(1000)
            if (_isRunning.value) {
                ProtonLogger.w("ByeDpiManager", "ByeDPI didn't stop in 1s, forcing close")
                proxy.forceClose()
            }
        }
        
        try {
            withTimeout(3000) {
                proxyJob?.join()
            }
        } catch (_: Exception) {
            ProtonLogger.w("ByeDpiManager", "ByeDPI job join timed out, forcing state update")
        } finally {
            forceCloseJob.cancel()
            // Small delay to ensure native thread has finished releasing g_proxy_running
            delay(100)
            proxyJob = null
            _isRunning.value = false
            ProtonLogger.i("ByeDpiManager", "ByeDPI stopped")
        }
    }
}
