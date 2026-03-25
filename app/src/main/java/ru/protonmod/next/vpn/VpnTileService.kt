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

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.MainActivity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.di.ApplicationScope
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject

@AndroidEntryPoint
class VpnTileService : TileService() {

    @Inject
    lateinit var amneziaVpnManager: AmneziaVpnManager

    @Inject
    lateinit var vpnRepository: VpnRepository

    @Inject
    lateinit var sessionDao: SessionDao

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var observationJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observationJob?.cancel()
        observationJob = applicationScope.launch(Dispatchers.Main) {
            amneziaVpnManager.tunnelState.collect { state ->
                updateTile(state)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        observationJob?.cancel()
        observationJob = null
    }

    override fun onClick() {
        super.onClick()
        val currentState = amneziaVpnManager.tunnelState.value
        
        if (currentState == Tunnel.State.UP) {
            amneziaVpnManager.disconnect()
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                // Need permission, open app
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        this, 0, intent, 
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            } else {
                applicationScope.launch {
                    performQuickConnect()
                }
            }
        }
    }

    private suspend fun performQuickConnect() {
        sessionDao.getSession() ?: return
        val strategy = settingsManager.quickConnectStrategy.first()
        
        val servers = vpnRepository.getCachedServers()
        if (servers.isEmpty()) return

        when (strategy) {
            "recent" -> {
                // For simplicity in Tile, let's use the same logic as DashboardViewModel but adapted.
                val bestServer = servers.minByOrNull { it.averageLoad }
                if (bestServer != null) {
                    initiateConnection(bestServer)
                }
            }
            "profile" -> {
                val bestServer = servers.minByOrNull { it.averageLoad }
                if (bestServer != null) {
                    initiateConnection(bestServer)
                }
            }
            else -> {
                val bestServer = servers.minByOrNull { it.averageLoad }
                if (bestServer != null) {
                    initiateConnection(bestServer)
                }
            }
        }
    }

    private suspend fun initiateConnection(server: LogicalServer) {
        val session = sessionDao.getSession() ?: return
        val physicalServer = server.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: server.servers.minByOrNull { it.load } ?: return

        amneziaVpnManager.connect(server.id, physicalServer, session)
    }

    private fun updateTile(state: Tunnel.State) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            Tunnel.State.UP -> Tile.STATE_ACTIVE
            Tunnel.State.DOWN -> Tile.STATE_INACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
