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
