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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import ru.protonmod.next.utils.ProtonLogger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Android VPN networks independently from the default network.
 *
 * A verification cycle snapshots existing VPN network handles while the old tunnel is still being
 * torn down. Validation then waits for the new VPN network and requires it to remain validated for
 * a short stability window. This prevents a stale `VALIDATED` value from completing a new
 * connection immediately and prevents capability callback bursts from restarting verification.
 */
@Singleton
class VpnNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    data class VerificationCycle internal constructor(
        internal val id: Long,
        internal val baselineHandles: Set<Long>
    )

    private data class Snapshot(
        val version: Long = 0,
        val networks: Map<Long, Boolean> = emptyMap()
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
                Snapshot(current.version + 1, current.networks - handle)
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
     * Waits for the cycle's new Android VPN network to be validated and stable.
     * Returns false on timeout; cancellation is propagated normally without error logging.
     */
    suspend fun awaitValidated(
        cycle: VerificationCycle,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        stabilityMs: Long = DEFAULT_STABILITY_MS
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        var observedVersion = -1L
        while (true) {
            val current = snapshot.value
            val candidate = current.networks.entries.firstOrNull { (handle, validated) ->
                handle !in cycle.baselineHandles && validated
            }

            if (candidate != null) {
                val handle = candidate.key
                val stableVersion = current.version
                delay(stabilityMs)
                val stable = snapshot.value
                if (stable.networks[handle] == true && stable.version == stableVersion) {
                    ProtonLogger.d(TAG, "VPN network validated for cycle ${cycle.id}")
                    return@withTimeoutOrNull true
                }
            }

            observedVersion = current.version
            snapshot.first { it.version != observedVersion }
        }
        @Suppress("UNREACHABLE_CODE")
        false
    } ?: false

    private fun refreshNetwork(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
        updateNetwork(network, capabilities)
    }

    private fun updateNetwork(network: Network, capabilities: NetworkCapabilities) {
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
        val handle = network.networkHandle
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        snapshot.update { current ->
            if (current.networks[handle] == validated) current
            else Snapshot(current.version + 1, current.networks + (handle to validated))
        }
    }

    private companion object {
        const val TAG = "VpnNetworkMonitor"
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val DEFAULT_STABILITY_MS = 750L
    }
}
