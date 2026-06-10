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

import android.content.Context
import ru.protonmod.next.utils.ProtonLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.local.RecentConnectionDao
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnAutomationManager @Inject constructor(
    private val amneziaVpnManager: AmneziaVpnManager,
    private val settingsManager: SettingsManager,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val recentConnectionDao: RecentConnectionDao,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val TAG = "VpnAutomationManager"

    init {
        applicationScope.launch {
            // Monitor Pause state with a real timer
            settingsManager.pauseEndTime.collectLatest { endTime ->
                val now = System.currentTimeMillis()
                if (endTime > now) {
                    val delayMs = endTime - now
                    ProtonLogger.d(TAG, "VPN is paused. Waiting ${delayMs}ms to auto-resume.")
                    delay(delayMs)
                    
                    // Final check: did someone manually resume or change the timer?
                    val currentEndTime = settingsManager.pauseEndTime.first()
                    if (currentEndTime != 0L && currentEndTime <= System.currentTimeMillis()) {
                        ProtonLogger.i(TAG, "Pause expired, auto-resuming...")
                        amneziaVpnManager.resumeVpn()
                        triggerAutoConnect()
                    }
                } else if (endTime > 0) {
                    // Already expired but not cleared (e.g. app just started)
                    // Add a small safety delay to avoid race condition on immediate pause calls
                    delay(500)
                    val currentEndTime = settingsManager.pauseEndTime.first()
                    if (currentEndTime > 0 && currentEndTime <= System.currentTimeMillis()) {
                        ProtonLogger.i(TAG, "Detected expired pause, clearing and resuming...")
                        amneziaVpnManager.resumeVpn()
                        triggerAutoConnect()
                    }
                }
            }
        }
    }

    suspend fun resumeVpn() {
        ProtonLogger.i(TAG, "resumeVpn: Manually requested resumption.")
        amneziaVpnManager.resumeVpn()
        triggerAutoConnect()
    }

    private suspend fun triggerAutoConnect() {
        // Don't auto-connect if paused
        val pauseEndTime = settingsManager.pauseEndTime.first()
        if (pauseEndTime > System.currentTimeMillis()) {
            ProtonLogger.d(TAG, "triggerAutoConnect: Still paused until $pauseEndTime. Skipping.")
            return
        }

        val session = sessionDao.getSession() ?: run {
            ProtonLogger.w(TAG, "triggerAutoConnect: No active session found.")
            return
        }
        
        val servers = vpnRepository.getCachedServers()
        if (servers.isEmpty()) {
            ProtonLogger.w(TAG, "triggerAutoConnect: Server cache is empty.")
            return
        }

        val recent = recentConnectionDao.getRecentConnections().first().firstOrNull()
        val targetServer = if (recent != null) {
            servers.find { it.id == recent.serverId } ?: servers.minByOrNull { it.averageLoad }
        } else {
            servers.minByOrNull { it.averageLoad }
        }

        if (targetServer == null) {
            ProtonLogger.w(TAG, "triggerAutoConnect: Could not determine target server.")
            return
        }

        val physicalServer = targetServer.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: targetServer.servers.minByOrNull { it.load }

        if (physicalServer == null) {
            ProtonLogger.w(TAG, "triggerAutoConnect: No physical servers available for ${targetServer.name}")
            return
        }

        ProtonLogger.i(TAG, "triggerAutoConnect: Initiating connection to ${targetServer.name}")
        amneziaVpnManager.connect(targetServer.id, physicalServer, session, logicalServer = targetServer)
    }
}
