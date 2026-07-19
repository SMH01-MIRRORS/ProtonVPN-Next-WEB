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

    @Test
    fun `extracts percent decoded vless name and endpoint for chain UI`() {
        val link = "vless://123e4567-e89b-12d3-a456-426614174000@se.example.com:8443" +
            "?encryption=none&type=tcp#%F0%9F%87%B8%F0%9F%87%AA%20Sweden%2C%20Stockholm%20%7C%20%5BBL%5D%20%283%29"

        val info = ProxyLinkParser.inspectLink(link)

        assertEquals("🇸🇪 Sweden, Stockholm | [BL] (3)", info.name)
        assertEquals("vless", info.protocol)
        assertEquals("se.example.com", info.server)
        assertEquals(8443, info.port)
    }

    @Test
    fun `accepts xray raw transport as tcp stream`() {
        val link = "vless://7dd08a29-44df-0bb8-9b67-e56607500009@89.208.231.191:8443" +
            "?encryption=none&flow=xtls-rprx-vision&fp=qq" +
            "&pbk=4CH3o5zOMcFNMbnwXnkAg0FFepmsc0QzhahXkUzb1ik" +
            "&security=reality&sid=d8c6b58bcbb0c323&sni=max.ru&type=raw" +
            "#%F0%9F%87%AB%F0%9F%87%AE%20Finland%20%E2%80%94%20%23118"

        val proxy = ProxyLinkParser.parseChain(link).single()
        val info = ProxyLinkParser.inspectLink(link)

        assertEquals("🇫🇮 Finland — #118", info.name)
        assertEquals("vless", info.protocol)
        assertEquals("89.208.231.191", info.server)
        assertEquals(8443, info.port)
        assertFalse("transport" in proxy.outbound)
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
