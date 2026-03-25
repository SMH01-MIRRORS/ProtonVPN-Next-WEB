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

package ru.protonmod.next.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.MainActivity
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.di.ApplicationScope
import ru.protonmod.next.vpn.AmneziaVpnManager
import ru.protonmod.next.vpn.ProtonVpnService
import javax.inject.Inject

@AndroidEntryPoint
class VpnWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var amneziaVpnManager: AmneziaVpnManager

    @Inject
    lateinit var vpnRepository: VpnRepository

    @Inject
    lateinit var sessionDao: SessionDao

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var connectedServerState: ConnectedServerState

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    companion object {
        const val ACTION_WIDGET_CLICK = "ru.protonmod.next.WIDGET_CLICK"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CLICK) {
            val currentState = amneziaVpnManager.tunnelState.value
            if (currentState == Tunnel.State.UP) {
                amneziaVpnManager.disconnect()
            } else {
                applicationScope.launch {
                    performQuickConnect()
                }
            }
        } else if (intent.action == ProtonVpnService.ACTION_STATE_CHANGED) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, VpnWidgetProvider::class.java))
            onUpdate(context, mgr, ids)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_vpn)
        
        val currentState = amneziaVpnManager.tunnelState.value
        val isConnected = currentState == Tunnel.State.UP
        val connectedServer = connectedServerState.connectedServer.value
        
        views.setImageViewResource(R.id.widget_icon, if (isConnected) R.drawable.ic_proton_lock_filled else R.drawable.ic_proton_lock_open_filled_2)
        
        val statusText = if (isConnected && connectedServer != null) {
            connectedServer.name
        } else {
            context.getString(if (isConnected) R.string.status_connected else R.string.status_disconnected)
        }
        views.setTextViewText(R.id.widget_status, statusText)
        
        val intent = Intent(context, VpnWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_CLICK
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private suspend fun performQuickConnect() {
        sessionDao.getSession() ?: return
        val servers = vpnRepository.getCachedServers()
        if (servers.isEmpty()) return

        val bestServer = servers.minByOrNull { it.averageLoad }
        if (bestServer != null) {
            initiateConnection(bestServer)
        }
    }

    private suspend fun initiateConnection(server: LogicalServer) {
        val session = sessionDao.getSession() ?: return
        val physicalServer = server.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: server.servers.minByOrNull { it.load } ?: return

        amneziaVpnManager.connect(server.id, physicalServer, session, logicalServer = server)
    }
}
