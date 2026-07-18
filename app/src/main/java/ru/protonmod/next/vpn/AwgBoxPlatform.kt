/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.vpn

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.OsConstants
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.KeyStore
import java.util.Base64
import io.nekohasekai.libbox.NetworkInterface as BoxNetworkInterface

/** Android platform bridge required by libbox. */
internal class AwgBoxPlatform(
    private val service: VpnService,
    private val onTunOpened: (ParcelFileDescriptor) -> Unit
) : PlatformInterface {
    private val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun autoDetectInterfaceControl(fd: Int) { service.protect(fd) }
    override fun useProcFS() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun clearDNSCache() = Unit
    override fun readWIFIState(): WIFIState? = null
    override fun sendNotification(notification: Notification) = Unit

    override fun openTun(options: TunOptions): Int {
        check(VpnService.prepare(service) == null) { "VPN permission was revoked" }
        val builder = service.Builder()
            .setSession(service.getString(ru.protonmod.next.R.string.vpn_session_name))
            .setMtu(options.mtu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        options.inet4Address.forEachRemaining { builder.addAddress(it.address(), it.prefix()) }
        options.inet6Address.forEachRemaining { builder.addAddress(it.address(), it.prefix()) }
        options.dnsServerAddress?.value?.takeIf { it.isNotBlank() }?.let(builder::addDnsServer)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            options.inet4RouteAddress.forEachRemaining { builder.addRoute(android.net.IpPrefix(java.net.InetAddress.getByName(it.address()), it.prefix())) }
            options.inet6RouteAddress.forEachRemaining { builder.addRoute(android.net.IpPrefix(java.net.InetAddress.getByName(it.address()), it.prefix())) }
            options.inet4RouteExcludeAddress.forEachRemaining { builder.excludeRoute(android.net.IpPrefix(java.net.InetAddress.getByName(it.address()), it.prefix())) }
            options.inet6RouteExcludeAddress.forEachRemaining { builder.excludeRoute(android.net.IpPrefix(java.net.InetAddress.getByName(it.address()), it.prefix())) }
        } else {
            options.inet4RouteRange.forEachRemaining { builder.addRoute(it.address(), it.prefix()) }
            options.inet6RouteRange.forEachRemaining { builder.addRoute(it.address(), it.prefix()) }
        }

        options.includePackage.forEachRemaining { packageName ->
            try { builder.addAllowedApplication(packageName) } catch (_: PackageManager.NameNotFoundException) { }
        }
        options.excludePackage.forEachRemaining { packageName ->
            try { builder.addDisallowedApplication(packageName) } catch (_: PackageManager.NameNotFoundException) { }
        }

        val descriptor = builder.establish() ?: error("Failed to establish Android TUN")
        onTunOpened(descriptor)
        return descriptor.fd
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) error("Connection owner API unavailable")
        val uid = connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort)
        )
        check(uid != Process.INVALID_UID) { "Connection owner not found" }
        return ConnectionOwner().apply {
            userId = uid
            val packages = service.packageManager.getPackagesForUid(uid).orEmpty().toList()
            userName = packages.firstOrNull().orEmpty()
            setAndroidPackageNames(BoxStringIterator(packages))
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        closeDefaultInterfaceMonitor(listener)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publishDefaultNetwork(listener, network)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publishDefaultNetwork(listener, network)
            override fun onLost(network: Network) {
                if (connectivity.activeNetwork == null) listener.updateDefaultInterface("", -1, false, false)
            }
        }
        networkCallback = callback
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            callback
        )
        connectivity.activeNetwork?.let { publishDefaultNetwork(listener, it) }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun publishDefaultNetwork(listener: InterfaceUpdateListener, network: Network) {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return
        // The underlying physical network must be published; selecting our own VPN TUN
        // would route the AWG endpoint back into itself.
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
        val properties = connectivity.getLinkProperties(network) ?: return
        val name = properties.interfaceName ?: return
        val index = runCatching { NetworkInterface.getByName(name)?.index ?: -1 }.getOrDefault(-1)
        listener.updateDefaultInterface(
            name,
            index,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            false
        )
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val result = mutableListOf<BoxNetworkInterface>()
        for (network in connectivity.allNetworks) {
            val properties = connectivity.getLinkProperties(network) ?: continue
            val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
            val name = properties.interfaceName ?: continue
            val javaInterface = runCatching { NetworkInterface.getByName(name) }.getOrNull() ?: continue
            result += BoxNetworkInterface().apply {
                this.name = name
                index = javaInterface.index
                mtu = runCatching { javaInterface.mtu }.getOrDefault(1500)
                addresses = BoxStringIterator(javaInterface.interfaceAddresses.map { address ->
                    val host = if (address.address is Inet6Address) {
                        Inet6Address.getByAddress(address.address.address).hostAddress
                    } else address.address.hostAddress
                    "$host/${address.networkPrefixLength}"
                })
                dnsServer = BoxStringIterator(properties.dnsServers.mapNotNull { it.hostAddress })
                type = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }
        return BoxNetworkIterator(result)
    }

    override fun systemCertificates(): StringIterator {
        val certificates = mutableListOf<String>()
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val aliases = store.aliases()
        while (aliases.hasMoreElements()) {
            val certificate = store.getCertificate(aliases.nextElement()) ?: continue
            certificates += "-----BEGIN CERTIFICATE-----\n" +
                Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded) +
                "\n-----END CERTIFICATE-----"
        }
        return BoxStringIterator(certificates)
    }
}

internal class BoxStringIterator(values: Collection<String>) : StringIterator {
    private val iterator = values.iterator()
    override fun hasNext() = iterator.hasNext()
    override fun next() = iterator.next()
    override fun len() = 0
}

private class BoxNetworkIterator(values: Collection<BoxNetworkInterface>) : NetworkInterfaceIterator {
    private val iterator = values.iterator()
    override fun hasNext() = iterator.hasNext()
    override fun next() = iterator.next()
}

private inline fun io.nekohasekai.libbox.RoutePrefixIterator.forEachRemaining(block: (io.nekohasekai.libbox.RoutePrefix) -> Unit) {
    while (hasNext()) block(next())
}

private inline fun StringIterator.forEachRemaining(block: (String) -> Unit) {
    while (hasNext()) block(next())
}
