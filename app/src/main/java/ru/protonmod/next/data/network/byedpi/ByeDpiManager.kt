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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ByeDpiManager @Inject constructor() {

    private val proxy = ByeDpiProxy()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var proxyJob: Job? = null
    private val mutex = Mutex()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    suspend fun start(args: Array<String>) {
        mutex.withLock {
            if (_isRunning.value) {
                ProtonLogger.w("ByeDpiManager", "Proxy is already running. Stopping it first.")
                stopInternal()
            }

            val currentArgs = args
            proxyJob = scope.launch {
                _isRunning.value = true
                ProtonLogger.i("ByeDpiManager", "Starting ByeDPI with args: ${currentArgs.joinToString(" ")}")
                try {
                    val result = proxy.startProxy(currentArgs)
                    ProtonLogger.i("ByeDpiManager", "ByeDPI stopped with result: $result")
                } catch (e: Exception) {
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
            delay(2000)
            if (_isRunning.value) {
                ProtonLogger.w("ByeDpiManager", "ByeDPI didn't stop in 2s, forcing close")
                proxy.forceClose()
            }
        }
        
        proxyJob?.join()
        forceCloseJob.cancel()
        proxyJob = null
        _isRunning.value = false
    }
}
