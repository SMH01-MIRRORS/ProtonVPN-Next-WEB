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

import org.amnezia.awg.config.Config
import org.amnezia.awg.config.Interface
import org.amnezia.awg.config.Peer
import javax.inject.Inject
import javax.inject.Singleton

interface AmneziaConfigGenerator {
    fun buildConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        isIncludeMode: Boolean = false,
        selectedApps: Set<String> = emptySet(),
        selectedIps: Set<String> = emptySet(),
        port: Int = 1194,
        certificate: String? = null,
        obfuscationParams: AmneziaVpnManager.ObfuscationParams
    ): String
}

@Singleton
class AmneziaConfigGeneratorImpl @Inject constructor() : AmneziaConfigGenerator {
    override fun buildConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        isIncludeMode: Boolean,
        selectedApps: Set<String>,
        selectedIps: Set<String>,
        port: Int,
        certificate: String?,
        obfuscationParams: AmneziaVpnManager.ObfuscationParams
    ): String {
        val allowedIpsList = when {
            isIncludeMode -> if (selectedIps.isEmpty()) listOf("0.0.0.0/0") else selectedIps.toList()
            else -> if (selectedIps.isEmpty()) listOf("0.0.0.0/0") else IpSubnetCalculator.complementOfExcluded(selectedIps)
        }
        
        val peer = Peer.Builder()
            .parsePublicKey(serverPublicKey)
            .parseEndpoint("$targetIp:$port")
            .apply {
                allowedIpsList.forEach { parseAllowedIPs(it) }
            }
            .setPersistentKeepalive(60)
            .build()

        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(privateKey)
            .parseAddresses("$localIp/32")
            .parseDnsServers(dnsServer)
            .setMtu(1280)
            .setJunkPacketCount(obfuscationParams.jc)
            .setJunkPacketMinSize(obfuscationParams.jmin)
            .setJunkPacketMaxSize(obfuscationParams.jmax)
            .setInitPacketJunkSize(obfuscationParams.s1)
            .setResponsePacketJunkSize(obfuscationParams.s2)
            .setCookieReplyPacketJunkSize(obfuscationParams.s3)
            .setTransportPacketJunkSize(obfuscationParams.s4)
            .apply {
                if (obfuscationParams.h1.isNotEmpty()) setInitPacketMagicHeader(obfuscationParams.h1)
                if (obfuscationParams.h2.isNotEmpty()) setResponsePacketMagicHeader(obfuscationParams.h2)
                if (obfuscationParams.h3.isNotEmpty()) setUnderloadPacketMagicHeader(obfuscationParams.h3)
                if (obfuscationParams.h4.isNotEmpty()) setTransportPacketMagicHeader(obfuscationParams.h4)
            }

        if (obfuscationParams.i1.isNotEmpty()) ifaceBuilder.parseSpecialJunkI1(obfuscationParams.i1)
        if (obfuscationParams.i2.isNotEmpty()) ifaceBuilder.parseSpecialJunkI2(obfuscationParams.i2)
        if (obfuscationParams.i3.isNotEmpty()) ifaceBuilder.parseSpecialJunkI3(obfuscationParams.i3)
        if (obfuscationParams.i4.isNotEmpty()) ifaceBuilder.parseSpecialJunkI4(obfuscationParams.i4)
        if (obfuscationParams.i5.isNotEmpty()) ifaceBuilder.parseSpecialJunkI5(obfuscationParams.i5)
        if (obfuscationParams.i2.isNotEmpty()) ifaceBuilder.parseSpecialJunkI2(obfuscationParams.i2)
        if (obfuscationParams.i3.isNotEmpty()) ifaceBuilder.parseSpecialJunkI3(obfuscationParams.i3)
        if (obfuscationParams.i4.isNotEmpty()) ifaceBuilder.parseSpecialJunkI4(obfuscationParams.i4)
        if (obfuscationParams.i5.isNotEmpty()) ifaceBuilder.parseSpecialJunkI5(obfuscationParams.i5)

        if (selectedApps.isNotEmpty()) {
            if (isIncludeMode) {
                // If the library doesn't support parseIncludedApplications, 
                // we might need a different approach, but let's try if it exists.
                // Note: Standard WG Android uses 'IncludedApplications' in config
                ifaceBuilder.parseIncludedApplications(selectedApps.joinToString(","))
            } else {
                ifaceBuilder.parseExcludedApplications(selectedApps.joinToString(","))
            }
        }

        val config = Config.Builder().setInterface(ifaceBuilder.build()).addPeer(peer).build()
        return config.toAwgQuickString(false, false)
    }
}
