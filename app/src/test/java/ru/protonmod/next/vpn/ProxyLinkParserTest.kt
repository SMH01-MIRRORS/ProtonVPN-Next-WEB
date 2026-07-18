/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.vpn

import java.util.Base64
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyLinkParserTest {
    @Test
    fun `parses vless reality websocket link`() {
        val link = "vless://123e4567-e89b-12d3-a456-426614174000@example.com:443" +
            "?encryption=none&security=reality&sni=cdn.example.com&fp=chrome" +
            "&pbk=public-key&sid=abcd&type=ws&host=front.example.com&path=%2Fproxy#Primary"

        val proxy = ProxyLinkParser.parseChain(link).single()
        val outbound = proxy.outbound
        val tls = outbound.getValue("tls").jsonObject
        val transport = outbound.getValue("transport").jsonObject

        assertEquals("Primary", proxy.name)
        assertEquals("vless", outbound.getValue("type").jsonPrimitive.content)
        assertEquals("xudp", outbound.getValue("packet_encoding").jsonPrimitive.content)
        assertEquals("bootstrap-dns", outbound.getValue("domain_resolver").jsonPrimitive.content)
        assertEquals("public-key", tls.getValue("reality").jsonObject.getValue("public_key").jsonPrimitive.content)
        assertEquals("ws", transport.getValue("type").jsonPrimitive.content)
        assertEquals("/proxy", transport.getValue("path").jsonPrimitive.content)
    }

    @Test
    fun `parses vmess link and chains entries in order`() {
        val vmessJson = """{"v":"2","ps":"Second","add":"192.0.2.20","port":"443","id":"123e4567-e89b-12d3-a456-426614174001","aid":"0","scy":"auto","net":"tcp","tls":"tls","sni":"vmess.example.com"}"""
        val vmess = "vmess://" + Base64.getEncoder().withoutPadding()
            .encodeToString(vmessJson.toByteArray())
        val vless = "vless://123e4567-e89b-12d3-a456-426614174000@192.0.2.10:8443?encryption=none"

        val chain = ProxyLinkParser.parseChain("$vless\n$vmess")

        assertEquals(2, chain.size)
        assertEquals("proxy-2", chain[0].outbound.getValue("detour").jsonPrimitive.content)
        assertFalse("detour" in chain[1].outbound)
        assertEquals("vmess", chain[1].outbound.getValue("type").jsonPrimitive.content)
        assertTrue(ProxyLinkParser.isValid(vmess))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported proxy scheme`() {
        ProxyLinkParser.parseChain("trojan://secret@example.com:443")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported grpc transport from minimal core`() {
        ProxyLinkParser.parseChain(
            "vless://123e4567-e89b-12d3-a456-426614174000@example.com:443" +
                "?encryption=none&type=grpc"
        )
    }
}
