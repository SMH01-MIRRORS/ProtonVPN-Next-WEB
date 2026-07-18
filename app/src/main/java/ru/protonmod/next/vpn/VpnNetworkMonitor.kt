/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package ru.protonmod.next.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.protonmod.next.utils.ProtonLogger
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Android VPN networks independently from the default network.
 *
 * Android's NET_CAPABILITY_VALIDATED is driven by system captive-portal probes and can arrive many
 * seconds after applications already exchange traffic through a working tunnel. A verification
 * cycle therefore accepts either the system capability or a successful TCP probe explicitly bound
 * to the newly-created VPN network. Binding the socket to that Network ensures a direct underlying
 * connection cannot produce a false positive.
 */
@Singleton
class VpnNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    data class VerificationCycle internal constructor(
        internal val id: Long,
        internal val baselineHandles: Set<Long>
    )

    private data class TrackedNetwork(
        val network: Network,
        val systemValidated: Boolean
    )

    private data class Snapshot(
        val networks: Map<Long, TrackedNetwork> = emptyMap()
    )

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val cycleIds = AtomicLong(0)
    private val snapshot = MutableStateFlow(Snapshot())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetwork(network)

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateNetwork(network, capabilities)
        }

        override fun onLost(network: Network) {
            val handle = network.networkHandle
            snapshot.update { current ->
                Snapshot(current.networks - handle)
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (error: Exception) {
            ProtonLogger.e(TAG, "Failed to register VPN network callback", error)
        }
    }

    /** Call once when a fresh connection attempt enters CONNECTING. */
    fun beginVerificationCycle(): VerificationCycle {
        return VerificationCycle(
            id = cycleIds.incrementAndGet(),
            baselineHandles = snapshot.value.networks.keys
        )
    }

    /**
     * Waits until the cycle's new VPN network is actually usable.
     *
     * Android validation wins immediately when available. Otherwise a short TCP connection is made
     * through the VPN Network itself. This reflects real tunnel usability instead of waiting for
     * Android's delayed captive-portal validation. Returns false on timeout; cancellation propagates.
     */
    suspend fun awaitUsable(
        cycle: VerificationCycle,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        while (true) {
            val candidate = snapshot.value.networks.entries.firstOrNull { (handle, _) ->
                handle !in cycle.baselineHandles
            }

            if (candidate == null) {
                delay(retryDelayMs)
                continue
            }

            val handle = candidate.key
            val tracked = candidate.value
            if (tracked.systemValidated) {
                ProtonLogger.d(TAG, "VPN network system-validated for cycle ${cycle.id}")
                return@withTimeoutOrNull true
            }

            if (probeVpnNetwork(tracked.network)) {
                ProtonLogger.d(TAG, "VPN network passed active traffic probe for cycle ${cycle.id}")
                return@withTimeoutOrNull true
            }

            // The network may have disappeared or changed capabilities while the probe was running.
            if (snapshot.value.networks[handle]?.systemValidated == true) {
                ProtonLogger.d(TAG, "VPN network system-validated during probe for cycle ${cycle.id}")
                return@withTimeoutOrNull true
            }
            delay(retryDelayMs)
        }
        @Suppress("UNREACHABLE_CODE")
        false
    } ?: false

    private suspend fun probeVpnNetwork(network: Network): Boolean = withContext(Dispatchers.IO) {
        PROBE_TARGETS.any { target ->
            runCatching {
                network.socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(target, PROBE_PORT), PROBE_CONNECT_TIMEOUT_MS)
                }
                true
            }.getOrDefault(false)
        }
    }

    private fun refreshNetwork(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
        updateNetwork(network, capabilities)
    }

    private fun updateNetwork(network: Network, capabilities: NetworkCapabilities) {
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
        val handle = network.networkHandle
        val tracked = TrackedNetwork(
            network = network,
            systemValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        )
        snapshot.update { current ->
            if (current.networks[handle] == tracked) current
            else Snapshot(current.networks + (handle to tracked))
        }
    }

    private companion object {
        const val TAG = "VpnNetworkMonitor"
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val DEFAULT_RETRY_DELAY_MS = 200L
        const val PROBE_PORT = 443
        const val PROBE_CONNECT_TIMEOUT_MS = 750
        val PROBE_TARGETS = listOf("1.1.1.1", "8.8.8.8")
    }
}
