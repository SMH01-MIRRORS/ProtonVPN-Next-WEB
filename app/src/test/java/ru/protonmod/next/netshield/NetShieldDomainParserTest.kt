/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.netshield

import org.junit.Assert.assertEquals
import org.junit.Test

class NetShieldDomainParserTest {
    @Test
    fun `parses whitespace-separated hosts files including URLhaus tabs`() {
        val content = listOf(
            "# comment",
            "127.0.0.1\tmalware.example",
            "0.0.0.0 ad.example # inline comment",
            "::1 ipv6-host.example",
            "127.0.0.1 localhost",
        ).joinToString("\n")

        assertEquals(
            setOf("malware.example", "ad.example", "ipv6-host.example"),
            NetShieldDomainParser.parse(content),
        )
    }

    @Test
    fun `parses adblock and plain domain formats`() {
        val content = listOf(
            "||tracker.example^",
            "||ads.example^\$third-party",
            "plain.example",
            "@@||allowed.example^",
        ).joinToString("\n")

        assertEquals(
            setOf("tracker.example", "ads.example", "plain.example"),
            NetShieldDomainParser.parse(content),
        )
    }
}
