/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.vpn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.protonmod.next.netshield.NetShieldRuleSet
import javax.inject.Inject
import javax.inject.Singleton

/** Builds an amnezia-box/sing-box configuration instead of a wg-quick config. */
interface AwgBoxConfigGenerator {
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
        obfuscationParams: AmneziaVpnManager.ObfuscationParams,
        proxyChainConfig: String? = null,
        netShieldRuleSets: List<NetShieldRuleSet> = emptyList()
    ): String
}

@Singleton
class AwgBoxConfigGeneratorImpl @Inject constructor(
    private val ipSubnetCalculator: IpSubnetCalculator
) : AwgBoxConfigGenerator {
    private companion object {
        val IPV4_LITERAL = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")
    }

    private val json = Json { prettyPrint = true; encodeDefaults = false }

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
        obfuscationParams: AmneziaVpnManager.ObfuscationParams,
        proxyChainConfig: String?,
        netShieldRuleSets: List<NetShieldRuleSet>
    ): String {
        require(port in 1..65535) { "Invalid AWG port: $port" }
        require(targetIp.isNotBlank()) { "AWG endpoint is empty" }
        require(IPV4_LITERAL.matches(targetIp)) { "AWG endpoint must be an IPv4 address" }

        val localPrefix = ipSubnetCalculator.normalizeIp(localIp)
        val proxyChain = proxyChainConfig?.takeIf(String::isNotBlank)
            ?.let(ProxyLinkParser::parseChain)
            .orEmpty()
        val routeAddresses = when {
            isIncludeMode && selectedIps.isNotEmpty() -> selectedIps.sorted()
            else -> listOf("0.0.0.0/0")
        }
        val routeExcludes = when {
            isIncludeMode -> emptyList()
            allowLan -> (selectedIps + LanExclusionUtils.EXCLUDED_RANGES).sorted()
            else -> selectedIps.sorted()
        }

        fun strings(values: Collection<String>) = JsonArray(values.map(::JsonPrimitive))
        fun awgValue(value: String): JsonPrimitive? = value.takeIf(String::isNotBlank)?.let(::JsonPrimitive)

        val tun = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "type" to JsonPrimitive("tun"),
            "tag" to JsonPrimitive("proton-tun"),
            "address" to strings(listOf("172.19.0.1/30")),
            "mtu" to JsonPrimitive(1400),
            "auto_route" to JsonPrimitive(true),
            "strict_route" to JsonPrimitive(true),
            "stack" to JsonPrimitive("system"),
            "route_address" to strings(routeAddresses)
        ).apply {
            if (routeExcludes.isNotEmpty()) put("route_exclude_address", strings(routeExcludes))
            if (selectedApps.isNotEmpty()) {
                put(if (isIncludeMode) "include_package" else "exclude_package", strings(selectedApps.sorted()))
            }
        }

        val awg = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "type" to JsonPrimitive("awg"),
            "tag" to JsonPrimitive("proton-awg"),
            "useIntegratedTun" to JsonPrimitive(false),
            "address" to strings(listOf(localPrefix)),
            "private_key" to JsonPrimitive(privateKey),
            "mtu" to JsonPrimitive(1408),
            "peers" to JsonArray(listOf(JsonObject(mapOf(
                "address" to JsonPrimitive(targetIp),
                "port" to JsonPrimitive(port),
                "public_key" to JsonPrimitive(serverPublicKey),
                "allowed_ips" to strings(listOf("0.0.0.0/0")),
                "persistent_keepalive_interval" to JsonPrimitive(25)
            ))))
        ).apply {
            if (proxyChain.isNotEmpty()) {
                put("detour", JsonPrimitive(proxyChain.first().outbound.getValue("tag").let { (it as JsonPrimitive).content }))
            } else {
                put("jc", JsonPrimitive(obfuscationParams.jc))
                put("jmin", JsonPrimitive(obfuscationParams.jmin))
                put("jmax", JsonPrimitive(obfuscationParams.jmax))
                put("s1", JsonPrimitive(obfuscationParams.s1))
                put("s2", JsonPrimitive(obfuscationParams.s2))
                put("s3", JsonPrimitive(obfuscationParams.s3))
                put("s4", JsonPrimitive(obfuscationParams.s4))
                awgValue(obfuscationParams.h1)?.let { put("h1", it) }
                awgValue(obfuscationParams.h2)?.let { put("h2", it) }
                awgValue(obfuscationParams.h3)?.let { put("h3", it) }
                awgValue(obfuscationParams.h4)?.let { put("h4", it) }
                awgValue(obfuscationParams.i1)?.let { put("i1", it) }
                awgValue(obfuscationParams.i2)?.let { put("i2", it) }
                awgValue(obfuscationParams.i3)?.let { put("i3", it) }
                awgValue(obfuscationParams.i4)?.let { put("i4", it) }
                awgValue(obfuscationParams.i5)?.let { put("i5", it) }
            }
        }

        val config = JsonObject(mapOf(
            "log" to JsonObject(mapOf(
                "level" to JsonPrimitive("info"),
                "timestamp" to JsonPrimitive(true)
            )),
            "dns" to JsonObject(buildMap {
                put("servers", JsonArray(buildList {
                    if (proxyChain.isNotEmpty()) add(JsonObject(mapOf(
                        "type" to JsonPrimitive("udp"),
                        "tag" to JsonPrimitive("bootstrap-dns"),
                        "server" to JsonPrimitive("1.1.1.1"),
                        "server_port" to JsonPrimitive(53)
                    )))
                    add(JsonObject(mapOf(
                        "type" to JsonPrimitive("udp"),
                        "tag" to JsonPrimitive("proton-dns"),
                        "server" to JsonPrimitive(dnsServer),
                        "server_port" to JsonPrimitive(53),
                        "detour" to JsonPrimitive("proton-awg")
                    )))
                }))
                if (netShieldRuleSets.isNotEmpty()) {
                    put("rules", JsonArray(netShieldRuleSets.map { ruleSet ->
                        JsonObject(mapOf(
                            "rule_set" to strings(listOf(ruleSet.tag)),
                            "action" to JsonPrimitive("reject")
                        ))
                    }))
                }
                put("strategy", JsonPrimitive("ipv4_only"))
            }),
            "inbounds" to JsonArray(listOf(JsonObject(tun))),
            "endpoints" to JsonArray(listOf(JsonObject(awg))),
            "outbounds" to JsonArray(proxyChain.map(ProxyLinkParser.ParsedProxy::outbound)),
            "route" to JsonObject(buildMap {
                put("auto_detect_interface", JsonPrimitive(true))
                put("rules", JsonArray(listOf(
                    JsonObject(mapOf(
                        "ip_version" to JsonPrimitive(6),
                        "action" to JsonPrimitive("reject")
                    )),
                    JsonObject(mapOf("action" to JsonPrimitive("sniff"))),
                    JsonObject(mapOf("protocol" to strings(listOf("dns")), "action" to JsonPrimitive("hijack-dns")))
                )))
                if (netShieldRuleSets.isNotEmpty()) {
                    put("rule_set", JsonArray(netShieldRuleSets.map { ruleSet ->
                        JsonObject(mapOf(
                            "type" to JsonPrimitive("local"),
                            "tag" to JsonPrimitive(ruleSet.tag),
                            "format" to JsonPrimitive("source"),
                            "path" to JsonPrimitive(ruleSet.path)
                        ))
                    }))
                }
                put("final", JsonPrimitive("proton-awg"))
            })
        ))
        return json.encodeToString(JsonObject.serializer(), config)
    }
}
