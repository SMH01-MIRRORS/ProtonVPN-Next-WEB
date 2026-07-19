/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
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
