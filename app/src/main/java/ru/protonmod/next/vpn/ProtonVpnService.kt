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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.AbstractBackend
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.backend.TunnelActionHandler
import org.amnezia.awg.config.Config
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.state.ConnectedServerState
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.inject.Inject

/**
 * Intermediate base class to help Hilt/KSP resolve the Service inheritance
 * from the library's nested class.
 */
open class AmneziaVpnServiceBase : AbstractBackend.VpnService()

/**
 * Service implementation for AmneziaWG tunnel used in Proton VPN-Next.
 * Manages the VPN lifecycle, foreground notifications, and network traffic statistics.
 */
@AndroidEntryPoint
class ProtonVpnService : AmneziaVpnServiceBase() {

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var connectedServerState: ConnectedServerState

    // SupervisorJob ensures that if one child coroutine fails, it doesn't crash the whole scope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var statsJob: Job? = null
    private var lastRx: Long = 0L
    private var lastTx: Long = 0L
    private var lastSpeedText: String? = null

    private var notificationsEnabled: Boolean = true
    private var killSwitchEnabled: Boolean = false
    private var isManualDisconnect: Boolean = false

    companion object {
        private const val TAG = "ProtonVpnService"

        // Intent Actions
        const val ACTION_CONNECT = "ru.protonmod.next.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.protonmod.next.vpn.DISCONNECT"
        const val ACTION_STATE_CHANGED = "ru.protonmod.next.vpn.STATE_CHANGED"
        const val ACTION_UPDATE_SETTINGS = "ru.protonmod.next.vpn.UPDATE_SETTINGS"

        // Intent Extras
        const val EXTRA_CONFIG = "config_string"
        const val EXTRA_EXCLUDED_APPS = "excluded_apps"
        const val EXTRA_EXCLUDED_IPS = "excluded_ips"
        const val EXTRA_STATE = "state"
        const val EXTRA_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val EXTRA_KILL_SWITCH_ENABLED = "kill_switch_enabled"

        const val TUNNEL_NAME = "proton_awg"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_status_channel"
        private const val CHANNEL_SILENT_ID = "vpn_status_channel_silent"
        
        const val STATE_CONNECTING = "CONNECTING"
    }

    private lateinit var backend: GoBackend
    private var currentTunnelState: Tunnel.State = Tunnel.State.DOWN
    private var isCurrentlyConnecting: Boolean = false
    private var isForegroundServiceStarted: Boolean = false

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Tunnel callback interface to monitor VPN states and preferences.
     */
    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        
        override fun onStateChange(newState: Tunnel.State) {
            if (currentTunnelState == newState) return
            currentTunnelState = newState
            isCurrentlyConnecting = false

            Log.d(TAG, "VPN State changed to $newState")

            // Broadcast the new state to the rest of the application
            val broadcast = Intent(ACTION_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, newState.name)
                setPackage(packageName)
            }
            sendBroadcast(broadcast)

            // Handle traffic updates based on the current state
            if (newState == Tunnel.State.DOWN) {
                stopTrafficUpdates()
            }
            
            updateNotification(newState.name)
            
            if (newState == Tunnel.State.UP) {
                startTrafficUpdates()
            }
        }

        override fun isIpv4ResolutionPreferred(): Boolean = true

        override fun isMetered(): Boolean = false
    }

    /**
     * BroadcastReceiver to dynamically update settings (like notifications/kill switch)
     * without restarting the VPN service.
     */
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_UPDATE_SETTINGS) {
                notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
                killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
                Log.d(TAG, "Settings updated via broadcast: notifications=$notificationsEnabled, killSwitch=$killSwitchEnabled")
                
                val label = when {
                    isCurrentlyConnecting -> STATE_CONNECTING
                    else -> currentTunnelState.name
                }
                
                updateNotification(label)
            }
        }
    }

    override fun onCreate() {
        Log.d(TAG, "VPN Service creating in isolated :vpn process")

        // Set environment variables required for the Go backend (WireGuard/AmneziaWG)
        try {
            Os.setenv("TMPDIR", cacheDir.absolutePath, true)
            Os.setenv("WG_TUN_DIR", cacheDir.absolutePath, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set environment variables for the backend", e)
        }

        super.onCreate()
        createNotificationChannels()

        // Register the dynamic settings receiver
        val filter = IntentFilter(ACTION_UPDATE_SETTINGS)
        ContextCompat.registerReceiver(this, settingsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Initialize the Go backend
        backend = GoBackend(this, object : TunnelActionHandler {
            override fun runPreUp(scripts: Collection<String>) {}
            override fun runPostUp(scripts: Collection<String>) {}
            override fun runPreDown(scripts: Collection<String>) {}
            override fun runPostDown(scripts: Collection<String>) {}
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VPN Service start command received: ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECT -> {
                isManualDisconnect = false
                val configStr = intent.getStringExtra(EXTRA_CONFIG)
                notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, true)
                killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, false)

                // Important: Show connecting notification immediately to satisfy
                // Android's Foreground Service requirements and prevent exceptions.
                isCurrentlyConnecting = true
                updateNotification(STATE_CONNECTING)

                if (configStr != null) {
                    Log.d(TAG, "Received connection config:\n$configStr")
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val configStream = ByteArrayInputStream(configStr.toByteArray())
                            val config = Config.parse(configStream)

                            // Broadcast connecting state to UI
                            val broadcast = Intent(ACTION_STATE_CHANGED).apply {
                                putExtra(EXTRA_STATE, STATE_CONNECTING)
                                setPackage(packageName)
                            }
                            sendBroadcast(broadcast)

                            // Bring the tunnel up
                            backend.setState(tunnel, Tunnel.State.UP, config)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start VPN tunnel", e)
                            tunnel.onStateChange(Tunnel.State.DOWN)
                        }
                    }
                }
            }
            ACTION_DISCONNECT -> {
                isManualDisconnect = true
                isCurrentlyConnecting = false
                try {
                    // Bring the tunnel down gracefully
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop VPN tunnel", e)
                    stopForegroundOrService()
                }
            }
            ACTION_UPDATE_SETTINGS -> {
                // Keep for backward compatibility if settings are updated via startService
                notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
                killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
                
                val label = when {
                    isCurrentlyConnecting -> STATE_CONNECTING
                    else -> currentTunnelState.name
                }
                
                updateNotification(label)
            }
            else -> {
                return super.onStartCommand(intent, flags, startId)
            }
        }
        return START_STICKY
    }

    /**
     * Creates notification channels required for Android O and above.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            val name = getString(R.string.notification_channel_name)

            // Standard channel for visible VPN status
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)

            // Silent channel for background operation without disturbing the user
            val silentChannel = NotificationChannel(CHANNEL_SILENT_ID, "$name (Silent)", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(silentChannel)
        }
    }

    /**
     * Periodically queries the backend for network statistics and updates the notification.
     */
    private fun startTrafficUpdates() {
        stopTrafficUpdates()
        lastRx = 0L
        lastTx = 0L
        statsJob = serviceScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    try {
                        val stats = backend.getStatistics(tunnel)
                        val totalRx = stats.totalRx()
                        val totalTx = stats.totalTx()

                        val deltaRx = if (lastRx == 0L) 0L else (totalRx - lastRx)
                        val deltaTx = if (lastTx == 0L) 0L else (totalTx - lastTx)

                        lastRx = totalRx
                        lastTx = totalTx

                        val upStr = formatSpeed(deltaTx)
                        val downStr = formatSpeed(deltaRx)
                        lastSpeedText = "↑ $upStr ↓ $downStr"

                        if (notificationsEnabled && currentTunnelState == Tunnel.State.UP) {
                            // Update UI on the main thread
                            launch(Dispatchers.Main) {
                                updateNotification(Tunnel.State.UP.name, isSpeedUpdateOnly = true)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error while fetching traffic statistics", e)
                    }
                    delay(1000) // Update frequency
                }
            } finally {
                Log.d(TAG, "Traffic updates coroutine finished")
            }
        }
    }

    private fun stopTrafficUpdates() {
        statsJob?.cancel()
        statsJob = null
        lastSpeedText = null
    }

    /**
     * Formats bytes into a human-readable speed string.
     */
    private fun formatSpeed(bytesPerSec: Long): String {
        val b = bytesPerSec.toDouble()
        if (b <= 0.0) return "0 B/s"
        val kib = 1024.0
        val mib = kib * 1024.0
        val gib = mib * 1024.0
        return when {
            b >= gib -> String.format(Locale.US, "%.2f GiB/s", b / gib)
            b >= mib -> String.format(Locale.US, "%.2f MiB/s", b / mib)
            b >= kib -> String.format(Locale.US, "%.1f KiB/s", b / kib)
            else -> String.format(Locale.US, "%.0f B/s", b)
        }
    }

    /**
     * Builds the notification object based on the current VPN state.
     */
    private fun createNotification(stateName: String, speedText: String? = null): Notification {
        val serverName = connectedServerState.connectedServer.value?.name ?: "Proton VPN"

        val title = when (stateName) {
            Tunnel.State.UP.name -> getString(R.string.notification_title_connected, serverName)
            STATE_CONNECTING -> getString(R.string.notification_title_connecting)
            else -> getString(R.string.notification_title_disconnected)
        }

        // Intent for manual disconnection via notification action
        val disconnectIntent = Intent(this, ProtonVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to launch the application when the notification is tapped
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val activeChannelId = if (notificationsEnabled) CHANNEL_ID else CHANNEL_SILENT_ID

        val builder = NotificationCompat.Builder(this, activeChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setPriority(if (notificationsEnabled) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setOngoing(stateName != Tunnel.State.DOWN.name)
            .setContentIntent(contentPendingIntent)
            .setShowWhen(false)

        if (stateName == Tunnel.State.UP.name) {
            builder.addAction(
                0,
                getString(R.string.notification_action_disconnect),
                disconnectPendingIntent
            )
            if (!speedText.isNullOrEmpty() && notificationsEnabled) {
                builder.setContentText(speedText)
            }
        }

        return builder.build()
    }

    /**
     * Updates the foreground service notification or removes it if appropriate.
     */
    private fun updateNotification(stateName: String, isSpeedUpdateOnly: Boolean = false) {
        val isDown = stateName == Tunnel.State.DOWN.name
        val isConnecting = isCurrentlyConnecting || stateName == STATE_CONNECTING

        // Decide if we should show a foreground notification.
        // It must be shown during connection, and kept alive if kill switch is active.
        val shouldShow = when {
            isConnecting -> true
            isDown -> killSwitchEnabled && !isManualDisconnect
            else -> notificationsEnabled
        }

        if (shouldShow) {
            val notification = createNotification(stateName, lastSpeedText)

            if (!isForegroundServiceStarted || !isSpeedUpdateOnly) {
                // Compat layer to handle Android 14+ Foreground Service types safely
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundServiceStarted = true
            } else {
                // Update existing notification without re-registering the foreground service
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            stopForegroundOrService(isDown)
        }
    }

    /**
     * Helper to correctly stop the foreground service across different Android versions.
     */
    private fun stopForegroundOrService(stopSelf: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        isForegroundServiceStarted = false

        if (stopSelf) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "VPN Service destroyed")
        try {
            unregisterReceiver(settingsReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered", e)
        }

        // Cancel all ongoing coroutines (like stats job)
        serviceScope.cancel()

        // Ensure the tunnel is cleanly shut down
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN on service destroy", e)
        }
        super.onDestroy()
    }
}
