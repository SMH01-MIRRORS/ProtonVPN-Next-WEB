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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.utils.SiteCheckUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ByeDpiStrategyTester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val byeDpiManager: ByeDpiManager,
    private val settingsManager: SettingsManager
) {

    private val _isTesting = MutableStateFlow(false)
    val isTesting = _isTesting.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _currentStrategy = MutableStateFlow("")
    val currentStrategy = _currentStrategy.asStateFlow()

    private var testJob: Job? = null

    fun startTesting(sites: List<String>, strategies: List<String>) {
        if (_isTesting.value) return

        testJob = CoroutineScope(Dispatchers.IO).launch {
            _isTesting.value = true
            _progress.value = 0f
            
            var bestStrategy = ""
            var maxSuccess = -1

            val proxyIp = "127.0.0.1"
            val proxyPort = 1080
            val siteChecker = SiteCheckUtils(proxyIp, proxyPort)

            for ((index, strategy) in strategies.withIndex()) {
                if (!isActive) break
                
                _currentStrategy.value = strategy
                _progress.value = index.toFloat() / strategies.size

                val args = prepareArgs(strategy, proxyIp, proxyPort)
                byeDpiManager.start(args)
                
                // Wait for proxy to stabilize
                delay(1000)

                if (byeDpiManager.isRunning.value) {
                    val results = siteChecker.checkSitesAsync(
                        sites = sites,
                        requestsCount = 1,
                        requestTimeoutSeconds = 5,
                        concurrentRequests = 3
                    )
                    
                    val totalSuccess = results.sumOf { it.second }
                    ProtonLogger.i("ByeDpiStrategyTester", "Strategy: $strategy, Success: $totalSuccess/${sites.size}")

                    if (totalSuccess > maxSuccess) {
                        maxSuccess = totalSuccess
                        bestStrategy = strategy
                    }
                    
                    // Optimization: if all sites are reachable, we can stop
                    if (totalSuccess == sites.size) {
                        ProtonLogger.i("ByeDpiStrategyTester", "Found perfect strategy: $bestStrategy")
                        break
                    }
                } else {
                    ProtonLogger.w("ByeDpiStrategyTester", "Proxy failed to start for strategy: $strategy")
                }

                byeDpiManager.stop()
                delay(500)
            }

            if (bestStrategy.isNotEmpty()) {
                ProtonLogger.i("ByeDpiStrategyTester", "Best strategy found: $bestStrategy with $maxSuccess successes")
                settingsManager.setApiProxyHost("127.0.0.1")
                settingsManager.setApiProxyPort(proxyPort)
                settingsManager.setByeDpiFlags(bestStrategy)
            }

            _isTesting.value = false
            _progress.value = 1f
            _currentStrategy.value = "Finished"
        }
    }

    fun stopTesting() {
        testJob?.cancel()
        testJob = null
        CoroutineScope(Dispatchers.IO).launch {
            byeDpiManager.stop()
        }
        _isTesting.value = false
    }

    private fun prepareArgs(strategy: String, ip: String, port: Int): Array<String> {
        val baseArgs = listOf("ciadpi", "--ip", ip, "--port", port.toString())
        val flags = strategy.split(" ").filter { it.isNotEmpty() }
        return (baseArgs + flags).toTypedArray()
    }
}
