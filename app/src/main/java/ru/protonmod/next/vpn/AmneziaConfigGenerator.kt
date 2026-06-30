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

import dagger.Lazy
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
        allowLan: Boolean = false,
        selectedApps: Set<String> = emptySet(),
        selectedIps: Set<String> = emptySet(),
        port: Int = 1194,
        certificate: String? = null,
        obfuscationParams: AmneziaVpnManager.ObfuscationParams
    ): String
}

@Singleton
class AmneziaConfigGeneratorImpl @Inject constructor(
    private val nextConfigGenerator: Lazy<NextConfigGenerator>,
    private val ipSubnetCalculator: IpSubnetCalculator
) : AmneziaConfigGenerator {
    override fun buildConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        isIncludeMode: Boolean,
        allowLan: Boolean,
        selectedApps: Set<String>,
        selectedIps: Set<String>,
        port: Int,
        certificate: String?,
        obfuscationParams: AmneziaVpnManager.ObfuscationParams
    ): String {
        val baseAllowedIps = when {
            isIncludeMode -> if (selectedIps.isEmpty()) listOf("0.0.0.0/0") else selectedIps.toList()
            allowLan -> {
                if (selectedIps.isEmpty()) {
                    LanExclusionUtils.REFINED_ALLOWED_IPS
                } else {
                    ipSubnetCalculator.complementOfExcluded(selectedIps + LanExclusionUtils.EXCLUDED_RANGES)
                }
            }
            else -> if (selectedIps.isEmpty()) listOf("0.0.0.0/0") else ipSubnetCalculator.complementOfExcluded(selectedIps)
        }

        // Fix: Explicitly re-include local IP and DNS server in AllowedIPs.
        // This ensures that even if broad private ranges (like 10.0.0.0/8) are excluded for LAN bypass,
        // the VPN's internal routing and DNS still function through the tunnel.
        val finalAllowedIps = baseAllowedIps.toMutableSet().apply {
            add(ipSubnetCalculator.normalizeIp(localIp))
            add(ipSubnetCalculator.normalizeIp(dnsServer))
        }

        return nextConfigGenerator.get().buildConfig(
            serverPublicKey = serverPublicKey,
            privateKey = privateKey,
            localIp = localIp,
            dnsServer = dnsServer,
            targetIp = targetIp,
            isIncludeMode = isIncludeMode,
            selectedApps = selectedApps,
            selectedIps = finalAllowedIps,
            port = port,
            certificate = certificate,
            obfuscationParams = obfuscationParams
        )
    }
}
