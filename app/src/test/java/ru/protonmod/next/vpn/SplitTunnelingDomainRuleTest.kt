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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitTunnelingDomainRuleTest {
    @Test
    fun `normalizes exact and wildcard domain rules`() {
        assertEquals("example.com", SplitTunnelingDomainRule.normalize(" Example.COM. "))
        assertEquals("*.ru", SplitTunnelingDomainRule.normalize(" *.RU "))
        assertEquals("*.example.com", SplitTunnelingDomainRule.normalize("*.Example.COM."))
        assertEquals("*.xn--p1ai", SplitTunnelingDomainRule.normalize("*.РФ"))
        assertEquals("*.xn--p1ai", SplitTunnelingDomainRule.normalize(".рф"))
        assertEquals("xn--e1afmkfd.xn--p1ai", SplitTunnelingDomainRule.normalize("пример.рф"))
        assertEquals("*.рф", SplitTunnelingDomainRule.toDisplay("*.xn--p1ai"))
    }

    @Test
    fun `rejects unsupported wildcard and invalid domains`() {
        assertNull(SplitTunnelingDomainRule.normalize("*"))
        assertNull(SplitTunnelingDomainRule.normalize("foo.*.ru"))
        assertNull(SplitTunnelingDomainRule.normalize("localhost"))
        assertNull(SplitTunnelingDomainRule.normalize("-example.com"))
        assertNull(SplitTunnelingDomainRule.normalize("192.0.2.1"))
    }

    @Test
    fun `separates exact domains from wildcard suffixes`() {
        val rules = setOf("example.com", "*.ru", "*.example.org", "invalid")

        assertEquals(listOf("example.com"), SplitTunnelingDomainRule.exactDomains(rules))
        assertEquals(listOf("example.org", "ru"), SplitTunnelingDomainRule.domainSuffixes(rules))
    }
}
