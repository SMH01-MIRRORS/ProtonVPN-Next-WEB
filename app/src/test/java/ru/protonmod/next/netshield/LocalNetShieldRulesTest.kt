/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.netshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetShieldRulesTest {
    @Test
    fun `recognizes ads and tracker reject logs emitted by dns router`() {
        assertEquals(
            NetShieldCategory.ADS,
            LocalNetShield.categoryFromRuleSetLog(
                "dns: match[0] rule_set=netshield-ads => reject"
            )
        )
        assertEquals(
            NetShieldCategory.TRACKERS,
            LocalNetShield.categoryFromRuleSetLog(
                "+0300 2026-07-19 10:42:11 DEBUG [184729103 0ms] dns: " +
                    "match[3] rule_set=netshield-trackers => reject"
            )
        )
    }

    @Test
    fun `does not count unrelated reject rules`() {
        assertEquals(
            null,
            LocalNetShield.categoryFromRuleSetLog("router: match[0] ip_version=6 => reject")
        )
    }

    @Test
    fun `protects Play Store and Gemini dependencies from compatibility lists`() {
        assertTrue(LocalNetShield.blocksProtectedDomain("googleapis.com"))
        assertTrue(LocalNetShield.blocksProtectedDomain("play.googleapis.com"))
        assertTrue(LocalNetShield.blocksProtectedDomain("google.com"))
        assertFalse(LocalNetShield.blocksProtectedDomain("ads.google.com"))
        assertFalse(LocalNetShield.blocksProtectedDomain("doubleclick.net"))
    }
}
