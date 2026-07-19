/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.vpn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.protonmod.next.netshield.NetShieldCategory
import ru.protonmod.next.netshield.NetShieldRuleSet

class NetShieldConfigTest {
    private val generator = AwgBoxConfigGeneratorImpl(object : IpSubnetCalculator {
        override fun isValidIpOrCidr(input: String) = true
        override fun normalizeIp(ip: String) = if ('/' in ip) ip else "$ip/32"
        override fun complementOfExcluded(excludedCidrs: Collection<String>) = listOf("0.0.0.0/0")
    })

    @Test
    fun `adds local rule sets and rejecting DNS rules in category order`() {
        val config = generator.buildConfig(
            serverPublicKey = "public",
            privateKey = "private",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "192.0.2.1",
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0,
                h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""
            ),
            netShieldRuleSets = listOf(
                NetShieldRuleSet("netshield-malware", "/rules/malware.json", NetShieldCategory.MALWARE),
                NetShieldRuleSet("netshield-ads", "/rules/ads.json", NetShieldCategory.ADS),
            )
        )

        val root = Json.parseToJsonElement(config).jsonObject
        val dnsRules = root.getValue("dns").jsonObject.getValue("rules").jsonArray
        val ruleSets = root.getValue("route").jsonObject.getValue("rule_set").jsonArray

        assertEquals(2, dnsRules.size)
        assertTrue(dnsRules.all { it.jsonObject.getValue("action").jsonPrimitive.content == "reject" })
        assertEquals("netshield-malware", dnsRules.first().jsonObject.getValue("rule_set").jsonArray.single().jsonPrimitive.content)
        assertEquals("local", ruleSets.first().jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("/rules/malware.json", ruleSets.first().jsonObject.getValue("path").jsonPrimitive.content)
    }
}
