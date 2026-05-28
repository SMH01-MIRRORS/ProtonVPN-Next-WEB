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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IpSubnetCalculatorInstrumentedTest {

    private val calculator = IpSubnetCalculatorImpl()

    @Test
    fun testIsValidIpOrCidr() {
        assertTrue(calculator.isValidIpOrCidr("1.1.1.1"))
        assertTrue(calculator.isValidIpOrCidr("1.1.1.1/32"))
        assertTrue(calculator.isValidIpOrCidr("0.0.0.0/0"))
        assertFalse(calculator.isValidIpOrCidr(""))
    }

    @Test
    fun testComplementOfExcluded_NoHang() {
        // This test case previously caused an infinite loop
        val excluded = listOf("8.8.8.8/32")
        val result = calculator.complementOfExcluded(excluded)
        
        assertTrue(result.isNotEmpty())
        assertFalse(result.contains("8.8.8.8/32"))
        
        // Verify it covers most of the space
        assertTrue(result.any { it.startsWith("0.0.0.0/") })
    }
    
    @Test
    fun testComplementOfExcluded_Multiple() {
        val excluded = listOf("1.1.1.1/32", "8.8.8.8/32")
        val result = calculator.complementOfExcluded(excluded)
        
        assertTrue(result.isNotEmpty())
        assertFalse(result.contains("1.1.1.1/32"))
        assertFalse(result.contains("8.8.8.8/32"))
    }
}
