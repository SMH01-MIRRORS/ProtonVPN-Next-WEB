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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetShieldRulesTest {
    @Test
    fun `recognizes block logs emitted by dns router`() {
        assertEquals(
            NetShieldCategory.MALWARE,
            LocalNetShield.categoryFromRuleSetLog(
                "DEBUG[0293] [970217929 25ms] dns: " +
                    "match[1] rule_set=netshield-malware => reject"
            )
        )
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
