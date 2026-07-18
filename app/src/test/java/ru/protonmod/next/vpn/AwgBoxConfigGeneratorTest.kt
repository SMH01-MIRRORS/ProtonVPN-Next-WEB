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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwgBoxConfigGeneratorTest {
    private val generator = AwgBoxConfigGeneratorImpl(object : IpSubnetCalculator {
        override fun isValidIpOrCidr(input: String) = true
        override fun normalizeIp(ip: String) = if ('/' in ip) ip else "$ip/32"
        override fun complementOfExcluded(excludedCidrs: Collection<String>) = listOf("0.0.0.0/0")
    })

    @Test
    fun `builds awg2 endpoint and sing-box tun topology`() {
        val config = generator.buildConfig(
            serverPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            privateKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "192.0.2.10",
            selectedApps = setOf("org.example.exclude"),
            selectedIps = setOf("203.0.113.0/24"),
            port = 443,
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 4,
                jmin = 40,
                jmax = 70,
                s1 = 20,
                s2 = 30,
                s3 = 40,
                s4 = 50,
                h1 = "1-3",
                h2 = "4",
                h3 = "5",
                h4 = "6",
                i1 = "<b 0x01>",
                i2 = "<r 8>",
                i3 = "",
                i4 = "",
                i5 = ""
            )
        )

        val root = Json.parseToJsonElement(config).jsonObject
        val tun = root.getValue("inbounds").jsonArray.single().jsonObject
        val awg = root.getValue("endpoints").jsonArray.single().jsonObject
        val peer = awg.getValue("peers").jsonArray.single().jsonObject

        assertEquals("tun", tun.getValue("type").jsonPrimitive.content)
        assertEquals("system", tun.getValue("stack").jsonPrimitive.content)
        assertTrue(tun.getValue("strict_route").jsonPrimitive.content.toBoolean())
        assertEquals("awg", awg.getValue("type").jsonPrimitive.content)
        assertEquals("10.2.0.2/32", awg.getValue("address").jsonArray.single().jsonPrimitive.content)
        assertEquals("4", awg.getValue("jc").jsonPrimitive.content)
        assertEquals("50", awg.getValue("s4").jsonPrimitive.content)
        assertEquals("<r 8>", awg.getValue("i2").jsonPrimitive.content)
        assertEquals("192.0.2.10", peer.getValue("address").jsonPrimitive.content)
        assertEquals("443", peer.getValue("port").jsonPrimitive.content)
        assertEquals("proton-awg", root.getValue("route").jsonObject.getValue("final").jsonPrimitive.content)
    }

    @Test
    fun `include mode limits routes and does not leak private key to unrelated fields`() {
        val privateKey = "PRIVATE_KEY_TEST_VALUE"
        val config = generator.buildConfig(
            serverPublicKey = "PUBLIC_KEY",
            privateKey = privateKey,
            localIp = "10.2.0.2/32",
            dnsServer = "10.2.0.1",
            targetIp = "198.51.100.1",
            isIncludeMode = true,
            selectedApps = setOf("org.example.only"),
            selectedIps = setOf("8.8.8.8/32"),
            port = 51820,
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0,
                h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""
            )
        )
        val root = Json.parseToJsonElement(config).jsonObject
        val tun = root.getValue("inbounds").jsonArray.single().jsonObject
        val routes = tun.getValue("route_address").jsonArray.map { it.jsonPrimitive.content }

        assertEquals(listOf("8.8.8.8/32"), routes)
        assertTrue("include_package" in tun)
        assertFalse("exclude_package" in tun)
        assertEquals(1, Regex(Regex.escape(privateKey)).findAll(config).count())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid endpoint port`() {
        generator.buildConfig(
            serverPublicKey = "public",
            privateKey = "private",
            localIp = "10.2.0.2",
            dnsServer = "10.2.0.1",
            targetIp = "192.0.2.1",
            port = 0,
            obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0,
                h1 = "", h2 = "", h3 = "", h4 = "", i1 = ""
            )
        )
    }
}
