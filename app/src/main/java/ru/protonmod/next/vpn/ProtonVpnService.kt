/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.utils.ProtonLogger
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Android VPN service backed by amnezia-box (sing-box + AWG/AWG2).
 *
 * The service intentionally keeps the public Intent/broadcast contract stable so the rest of the
 * app can migrate independently from the old wg-quick/GoBackend implementation.
 */
@AndroidEntryPoint
class ProtonVpnService : VpnService(), CommandServerHandler {
    @Inject lateinit var connectedServerState: ConnectedServerState
    @Inject lateinit var trafficStatsRecorder: TrafficStatsRecorder

    companion object {
        private const val TAG = "ProtonVpnService"
        const val ACTION_CONNECT = "ru.protonmod.next.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.protonmod.next.vpn.DISCONNECT"
        const val ACTION_STATE_CHANGED = "ru.protonmod.next.vpn.STATE_CHANGED"
        const val ACTION_UPDATE_SETTINGS = "ru.protonmod.next.vpn.UPDATE_SETTINGS"
        const val ACTION_STATS_UPDATED = "ru.protonmod.next.vpn.STATS_UPDATED"
        const val ACTION_SET_VERIFIED = "ru.protonmod.next.vpn.SET_VERIFIED"
        const val ACTION_QUERY_STATE = "ru.protonmod.next.vpn.QUERY_STATE"

        const val EXTRA_CONFIG = "config_string"
        const val EXTRA_EXCLUDED_APPS = "excluded_apps"
        const val EXTRA_EXCLUDED_IPS = "excluded_ips"
        const val EXTRA_STATE = "state"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_TRAFFIC_RX = "traffic_rx"
        const val EXTRA_TRAFFIC_TX = "traffic_tx"
        const val EXTRA_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val EXTRA_KILL_SWITCH_ENABLED = "kill_switch_enabled"
        const val EXTRA_NON_FATAL_ENABLED = "non_fatal_enabled"
        const val EXTRA_ANALYTICS_ENABLED = "analytics_enabled"
        const val EXTRA_LOGICAL_SERVER_ID = "logical_server_id"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_IS_RECONNECTING = "is_reconnecting"
        const val EXTRA_VERIFIED = "verified"
        const val STATE_CONNECTING = "CONNECTING"
        const val TUNNEL_NAME = "proton_awgbox"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_status_channel"
        private const val CHANNEL_SILENT_ID = "vpn_status_channel_silent"
        private const val TRANSPORT_FAILURE_THRESHOLD = 2
        private const val TRANSPORT_FAILURE_WINDOW_MS = 15_000L
        private const val HEALTH_RECONNECT_COOLDOWN_MS = 15_000L
        private const val FULL_CONFIG_LOG_TAG = "ProtonVpnConfig"
        private const val LOGCAT_CHUNK_SIZE = 3_500
        private val libboxInitialized = AtomicBoolean(false)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var platform: AwgBoxPlatform
    private var commandServer: CommandServer? = null
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var reconnectJob: Job? = null
    private var state = VpnTunnelState.DOWN
    private var connecting = false
    private var verified = false
    private var manualDisconnect = false
    private var notificationsEnabled = true
    private var killSwitchEnabled = false
    private var logicalServerId: String? = null
    private var lastConfig: String? = null
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastSpeed: String? = null
    private var transportFailureCount = 0
    private var lastTransportFailureAt = 0L
    private var lastHealthReconnectAt = 0L

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_DISCONNECT -> stopTunnel(manual = true)
                ACTION_UPDATE_SETTINGS -> applySettings(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initializeLibbox()
        platform = AwgBoxPlatform(this) { descriptor ->
            tunDescriptor?.close()
            tunDescriptor = descriptor
        }
        ContextCompat.registerReceiver(
            this,
            settingsReceiver,
            IntentFilter().apply {
                addAction(ACTION_DISCONNECT)
                addAction(ACTION_UPDATE_SETTINGS)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ProtonLogger.i(TAG, "amnezia-box ${Libbox.version()} initialized")
    }

    private fun initializeLibbox() {
        if (!libboxInitialized.compareAndSet(false, true)) return
        val workingDir = getExternalFilesDir(null) ?: filesDir
        val options = SetupOptions().apply {
            basePath = filesDir.absolutePath
            workingPath = workingDir.absolutePath
            tempPath = cacheDir.absolutePath
            logMaxLines = 2_000
            debug = BuildConfig.DEBUG
            fixAndroidStack = true
        }
        Libbox.setup(options)
        Libbox.setLocale(Locale.getDefault().toLanguageTag().replace('-', '_'))
        runCatching { Libbox.redirectStderr(File(workingDir, "awgbox-stderr.log").absolutePath) }
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startTunnel(intent)
            ACTION_DISCONNECT -> stopTunnel(manual = true)
            ACTION_UPDATE_SETTINGS -> applySettings(intent)
            ACTION_SET_VERIFIED -> {
                if (!verified) {
                    verified = true
                    connecting = false
                    updateNotification(VpnTunnelState.UP.name)
                }
            }
            ACTION_QUERY_STATE -> sendState(if (connecting) null else state)
            else -> return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startTunnel(intent: Intent) {
        val config = intent.getStringExtra(EXTRA_CONFIG) ?: run {
            ProtonLogger.e(TAG, "Missing awgbox configuration")
            return
        }
        logicalServerId = intent.getStringExtra(EXTRA_LOGICAL_SERVER_ID)
        notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, true)
        killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, false)
        lastConfig = config
        logFullConfigToLogcat(config)
        manualDisconnect = false
        verified = false
        connecting = true
        updateNotification(STATE_CONNECTING)
        sendState(null)

        reconnectJob?.cancel()
        scope.launch(Dispatchers.IO) {
            runCatching {
                closeEngine()
                val server = CommandServer(this@ProtonVpnService, platform).also { it.start() }
                server.checkConfig(config)
                server.startOrReloadService(config, OverrideOptions())
                commandServer = server
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    state = VpnTunnelState.UP
                    connecting = false
                    resetTransportFailures()
                    sendState(VpnTunnelState.UP)
                    updateNotification(VpnTunnelState.UP.name)
                    startTrafficUpdates()
                }
            }.onFailure { error ->
                ProtonLogger.e(TAG, "Failed to start amnezia-box tunnel", error)
                withContext(Dispatchers.Main) { handleEngineFailure() }
            }
        }
    }

    /**
     * Emits the exact generated sing-box configuration to local Logcat only.
     *
     * This deliberately bypasses ProtonLogger so the private key, proxy UUIDs and other
     * credentials can never become Sentry breadcrumbs or Sentry logs. Full configuration
     * logging is restricted to debug builds because Logcat is not an appropriate secret store.
     */
    private fun logFullConfigToLogcat(config: String) {
        if (!BuildConfig.DEBUG) return

        val chunks = config.chunked(LOGCAT_CHUNK_SIZE).ifEmpty { listOf("") }
        Log.d(FULL_CONFIG_LOG_TAG, "----- BEGIN AWGBOX CONFIG (${config.length} chars) -----")
        chunks.forEachIndexed { index, chunk ->
            Log.d(FULL_CONFIG_LOG_TAG, "[${index + 1}/${chunks.size}] $chunk")
        }
        Log.d(FULL_CONFIG_LOG_TAG, "----- END AWGBOX CONFIG -----")
    }

    private fun handleEngineFailure() {
        state = VpnTunnelState.DOWN
        connecting = false
        verified = false
        sendState(VpnTunnelState.DOWN)
        updateNotification(VpnTunnelState.DOWN.name)
        if (killSwitchEnabled && !manualDisconnect && !lastConfig.isNullOrBlank()) {
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(3.seconds)
                val retry = Intent(this@ProtonVpnService, ProtonVpnService::class.java).apply {
                    action = ACTION_CONNECT
                    putExtra(EXTRA_CONFIG, lastConfig)
                    putExtra(EXTRA_LOGICAL_SERVER_ID, logicalServerId)
                    putExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
                    putExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
                }
                startTunnel(retry)
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopTunnel(manual: Boolean) {
        manualDisconnect = manual
        reconnectJob?.cancel()
        connecting = false
        verified = false
        scope.launch(Dispatchers.IO) {
            closeEngine()
            withContext(Dispatchers.Main) {
                state = VpnTunnelState.DOWN
                sendState(VpnTunnelState.DOWN)
                stopTrafficUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (manual || !killSwitchEnabled) stopSelf()
            }
        }
    }

    private fun closeEngine() {
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
    }

    private fun applySettings(intent: Intent) {
        notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
        killSwitchEnabled = intent.getBooleanExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
        if (intent.hasExtra(EXTRA_NON_FATAL_ENABLED)) {
            ProtonLogger.isNonFatalEnabled = intent.getBooleanExtra(EXTRA_NON_FATAL_ENABLED, true)
        }
        if (intent.hasExtra(EXTRA_ANALYTICS_ENABLED)) {
            ProtonLogger.isAnalyticsEnabled = intent.getBooleanExtra(EXTRA_ANALYTICS_ENABLED, true)
        }
        updateNotification(if (connecting) STATE_CONNECTING else state.name)
    }

    private fun sendState(explicitState: VpnTunnelState?) {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, explicitState?.name ?: STATE_CONNECTING)
            putExtra(EXTRA_LOGICAL_SERVER_ID, logicalServerId)
            putExtra(EXTRA_IS_RECONNECTING, reconnectJob?.isActive == true)
            putExtra(EXTRA_VERIFIED, verified)
            setPackage(packageName)
        })
    }

    private fun startTrafficUpdates() {
        stopTrafficUpdates()
        val uid = applicationInfo.uid
        lastRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
        lastTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
        statsJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1.seconds)
                val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(lastRx)
                val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(lastTx)
                val deltaRx = rx - lastRx
                val deltaTx = tx - lastTx
                lastRx = rx
                lastTx = tx
                trafficStatsRecorder.record(deltaRx, deltaTx, 1)
                lastSpeed = getString(R.string.vpn_speed_format, formatBytes(deltaTx, true), formatBytes(deltaRx, true))
                sendBroadcast(Intent(ACTION_STATS_UPDATED).apply {
                    putExtra(EXTRA_SPEED, lastSpeed)
                    putExtra(EXTRA_TRAFFIC_RX, formatBytes(rx, false))
                    putExtra(EXTRA_TRAFFIC_TX, formatBytes(tx, false))
                    putExtra(EXTRA_LOGICAL_SERVER_ID, logicalServerId)
                    setPackage(packageName)
                })
                if (state == VpnTunnelState.UP) updateNotification(state.name)
            }
        }
    }

    private fun stopTrafficUpdates() {
        statsJob?.cancel()
        statsJob = null
        scope.launch(Dispatchers.IO) { trafficStatsRecorder.flush() }
    }

    private fun formatBytes(bytes: Long, speed: Boolean): String {
        val value = bytes.coerceAtLeast(0).toDouble()
        val (scaled, unit) = when {
            value >= 1024 * 1024 * 1024 -> value / (1024 * 1024 * 1024) to if (speed) R.string.unit_gb_s else R.string.unit_gb
            value >= 1024 * 1024 -> value / (1024 * 1024) to if (speed) R.string.unit_mb_s else R.string.unit_mb
            value >= 1024 -> value / 1024 to if (speed) R.string.unit_kb_s else R.string.unit_kb
            else -> value to if (speed) R.string.unit_b_s else R.string.unit_b
        }
        return String.format(Locale.US, if (scaled >= 1024) "%.0f %s" else "%.1f %s", scaled, getString(unit))
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val name = getString(R.string.notification_channel_name)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SILENT_ID, getString(R.string.notification_channel_silent_name), NotificationManager.IMPORTANCE_MIN))
    }

    private fun createNotification(stateName: String): Notification {
        val serverName = connectedServerState.connectedServer.value?.name ?: getString(R.string.app_name)
        val title = when {
            stateName == VpnTunnelState.UP.name && verified -> getString(R.string.notification_title_connected, serverName)
            stateName == VpnTunnelState.UP.name -> getString(R.string.notification_title_verifying)
            stateName == STATE_CONNECTING -> getString(R.string.notification_title_connecting)
            else -> getString(R.string.notification_title_disconnected)
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ProtonVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, if (notificationsEnabled) CHANNEL_ID else CHANNEL_SILENT_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lastSpeed)
            .setContentIntent(contentIntent)
            .setOngoing(stateName != VpnTunnelState.DOWN.name)
            .setShowWhen(false)
            .addAction(0, getString(R.string.notification_action_disconnect), disconnectIntent)
            .build()
    }

    private fun updateNotification(stateName: String) {
        val notification = createNotification(stateName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun serviceStop() { stopTunnel(manual = false) }
    override fun serviceReload() = Unit
    override fun getSystemProxyStatus() = SystemProxyStatus().apply { available = false; enabled = false }
    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit
    override fun writeDebugMessage(message: String?) {
        val logMessage = message.orEmpty()
        ProtonLogger.d("awgbox", logMessage)
        observeTransportHealth(logMessage)
    }

    private fun observeTransportHealth(message: String) {
        val normalized = message.lowercase(Locale.ROOT)
        when {
            isSuccessfulTransportActivity(normalized) -> scope.launch { resetTransportFailures() }
            isTransportFailure(normalized) -> scope.launch { recordTransportFailure(normalized) }
        }
    }

    private fun isSuccessfulTransportActivity(message: String): Boolean {
        return ("dns: exchanged " in message && "exchange failed" !in message) ||
            "received handshake response" in message
    }

    private fun isTransportFailure(message: String): Boolean {
        val timedOut = "context deadline exceeded" in message ||
            "i/o timeout" in message ||
            "tls handshake timeout" in message
        val transportError = "connection reset by peer" in message ||
            "broken pipe" in message ||
            "network is unreachable" in message
        val relevantPath = "dns: exchange failed" in message ||
            "outbound/vless" in message ||
            "outbound/vmess" in message ||
            "endpoint/awg" in message
        return relevantPath && (timedOut || transportError)
    }

    private fun recordTransportFailure(message: String) {
        if (state != VpnTunnelState.UP || connecting || manualDisconnect) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastTransportFailureAt > TRANSPORT_FAILURE_WINDOW_MS) {
            transportFailureCount = 0
        }
        lastTransportFailureAt = now
        transportFailureCount++
        ProtonLogger.w(
            TAG,
            "Tunnel transport health failure $transportFailureCount/$TRANSPORT_FAILURE_THRESHOLD"
        )

        if (transportFailureCount < TRANSPORT_FAILURE_THRESHOLD) return
        if (lastHealthReconnectAt != 0L && now - lastHealthReconnectAt < HEALTH_RECONNECT_COOLDOWN_MS) return

        lastHealthReconnectAt = now
        transportFailureCount = 0
        restartTunnelAfterHealthFailure(message)
    }

    private fun restartTunnelAfterHealthFailure(reason: String) {
        val config = lastConfig ?: return
        if (connecting || manualDisconnect) return

        ProtonLogger.w(TAG, "Tunnel transport is unresponsive; reconnecting")
        ProtonLogger.addSentryBreadcrumb(
            TAG,
            "Automatic reconnect after transport health failure: ${reason.take(160)}",
            "WARNING",
            "vpn.health"
        )
        val retry = Intent(this, ProtonVpnService::class.java).apply {
            action = ACTION_CONNECT
            putExtra(EXTRA_CONFIG, config)
            putExtra(EXTRA_LOGICAL_SERVER_ID, logicalServerId)
            putExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
            putExtra(EXTRA_KILL_SWITCH_ENABLED, killSwitchEnabled)
        }
        startTunnel(retry)
    }

    private fun resetTransportFailures() {
        transportFailureCount = 0
        lastTransportFailureAt = 0L
    }

    override fun onRevoke() {
        stopTunnel(manual = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(settingsReceiver) }
        reconnectJob?.cancel()
        stopTrafficUpdates()
        closeEngine()
        scope.cancel()
        super.onDestroy()
    }
}
