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
import ru.protonmod.next.utils.ShellUtils
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

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    private val _totalSteps = MutableStateFlow(0)
    val totalSteps = _totalSteps.asStateFlow()

    data class TestResult(val strategy: String, val successCount: Int, val totalSites: Int)
    
    private val _testResults = MutableStateFlow<List<TestResult>>(emptyList())
    val testResults = _testResults.asStateFlow()

    private var testJob: Job? = null

    fun startTesting(mode: String, sites: List<String>) {
        if (_isTesting.value) return

        val allStrategies = try {
            context.assets.open("proxytest_strategies.list").bufferedReader().readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
        } catch (e: Exception) {
            emptyList<String>()
        }

        val strategies = when (mode) {
            "fast" -> allStrategies.take(10)
            "medium" -> allStrategies.take(30)
            else -> allStrategies
        }

        testJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                _isTesting.value = true
                _progress.value = 0f
                _currentStep.value = 0
                _totalSteps.value = strategies.size
                _testResults.value = emptyList()
                
                // Disable auto-management during testing
                byeDpiManager.isAutoManagementEnabled = false
                byeDpiManager.stop()
                delay(500)
                
                var bestStrategy = ""
                var maxSuccess = -1

                val proxyIp = "127.0.0.1"
                val proxyPort = 1080
                val siteChecker = SiteCheckUtils(proxyIp, proxyPort)
                val sni = settingsManager.getByeDpiSniSync()

                for ((index, strategy) in strategies.withIndex()) {
                    if (!isActive) {
                        ProtonLogger.i("ByeDpiStrategyTester", "Test cancelled")
                        break
                    }
                    
                    _currentStep.value = index + 1
                    val processedStrategy = strategy.replace("{sni}", sni)
                    _currentStrategy.value = processedStrategy
                    _progress.value = index.toFloat() / strategies.size
                    ProtonLogger.i("ByeDpiStrategyTester", "Testing strategy [$index/${strategies.size}]: $processedStrategy")

                    val args = prepareArgs(processedStrategy, sni, proxyIp, proxyPort)
                    byeDpiManager.start(args)
                    
                    // Wait for proxy to stabilize
                    delay(2000)

                    var totalSuccess = 0
                    if (byeDpiManager.isRunning.value) {
                        val results = siteChecker.checkSitesAsync(
                            sites = sites,
                            requestsCount = 1,
                            requestTimeoutSeconds = 5,
                            concurrentRequests = 20
                        )
                        
                        totalSuccess = results.sumOf { it.second }
                        ProtonLogger.i("ByeDpiStrategyTester", "Strategy results: $totalSuccess/${sites.size}")

                        if (totalSuccess > maxSuccess && totalSuccess > 0) {
                            maxSuccess = totalSuccess
                            bestStrategy = processedStrategy
                        }
                    } else {
                        ProtonLogger.w("ByeDpiStrategyTester", "Proxy failed to start for strategy: $processedStrategy")
                    }

                    val result = TestResult(processedStrategy, totalSuccess, sites.size)
                    _testResults.value = _testResults.value + result
                    ProtonLogger.i("ByeDpiStrategyTester", "Added result: $result")
                    
                    // Optimization: if all sites are reachable, we can stop
                    if (totalSuccess == sites.size) {
                        ProtonLogger.i("ByeDpiStrategyTester", "Found perfect strategy: $bestStrategy")
                        break
                    }

                    ProtonLogger.i("ByeDpiStrategyTester", "Stopping proxy for next strategy...")
                    byeDpiManager.stop()
                    delay(500)
                }

                if (bestStrategy.isNotEmpty() && maxSuccess > 0) {
                    ProtonLogger.i("ByeDpiStrategyTester", "Best strategy found: $bestStrategy with $maxSuccess successes")
                    settingsManager.setApiProxyHost("127.0.0.1")
                    settingsManager.setApiProxyPort(proxyPort)
                    settingsManager.setByeDpiFlags(bestStrategy)
                } else {
                    ProtonLogger.w("ByeDpiStrategyTester", "No successful strategy found")
                }
            } finally {
                _isTesting.value = false
                _progress.value = 1f
                _currentStrategy.value = "Finished"
                // Re-enable auto-management. This will restart ByeDPI with the best (or current) strategy.
                byeDpiManager.isAutoManagementEnabled = true
            }
        }
    }

    fun stopTesting() {
        testJob?.cancel()
        testJob = null
        // Re-enabling auto-management will happen in the finally block of the testJob
    }

    private fun prepareArgs(strategy: String, sni: String, ip: String, port: Int): Array<String> {
        val baseArgs = listOf("ciadpi", "--ip", ip, "--port", port.toString())
        val flags = ShellUtils.shellSplit(strategy)
        return (baseArgs + flags).toTypedArray()
    }
}
