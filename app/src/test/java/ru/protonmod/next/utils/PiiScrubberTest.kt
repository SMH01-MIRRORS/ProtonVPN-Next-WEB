/*
 * Copyright (C) 2026 SMH01
 */

package ru.protonmod.next.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PiiScrubberTest {

    @Test
    fun testScrubIpAddresses() {
        val input = "Connected to 192.168.1.1 and 2001:0db8:85a3:0000:0000:8a2e:0370:7334"
        val expected = "Connected to [IPv4] and [IPv6]"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun testScrubTokens() {
        val input = "Login successful: accessToken=abc123def456ghi789, sessionId: 'sess_987654321'"
        val expected = "Login successful: accessToken=[REDACTED], sessionId: '[REDACTED]'"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun testScrubJsonTokens() {
        val input = "{\"accessToken\": \"very-long-token-value\", \"userId\": \"123\"}"
        val expected = "{\"accessToken\": \"[REDACTED]\", \"userId\": \"123\"}"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun testScrubUrlTokens() {
        val input = "https://example.com/captcha?token=secret123&other=val"
        val expected = "https://example.com/captcha?token=[REDACTED]&other=val"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun testScrubConfigBlock() {
        val input = """
            [Interface]
            PrivateKey = some-private-key
            Address = 10.0.0.1/32
            DNS = 1.1.1.1
            
            [Peer]
            PublicKey = some-peer-key
            Endpoint = 1.2.3.4:51820
        """.trimIndent()
        val expected = "[VPN_CONFIG_REDACTED]"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun testKeepNormalLogs() {
        val input = "User clicked Login button"
        assertEquals(input, PiiScrubber.scrub(input))
    }

    @Test
    fun testScrubSessionRefreshWorkerLog() {
        val input = "[SessionRefreshWorker] Starting background session keep-alive for lJ0eTa_CGsnneVR-2grzilAwAxmjAfC6MHzk-2JmOXstP5_QzCPXjPa7jnrhflctBcAlCTJ5XZxpqZcj5Gc9eg=="
        val expected = "[SessionRefreshWorker] Starting background session keep-alive for [REDACTED]"
        assertEquals(expected, PiiScrubber.scrub(input))
    }
}
