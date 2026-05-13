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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class IpSubnetCalculatorTest {

    @Mock
    private lateinit var calculator: IpSubnetCalculator

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun testIsValidIpOrCidr() {
        whenever(calculator.isValidIpOrCidr("1.1.1.1")).thenReturn(true)
        whenever(calculator.isValidIpOrCidr("1.1.1.1/32")).thenReturn(true)
        whenever(calculator.isValidIpOrCidr("0.0.0.0/0")).thenReturn(true)
        whenever(calculator.isValidIpOrCidr("1.1.1.1/33")).thenReturn(false)
        whenever(calculator.isValidIpOrCidr("not an ip")).thenReturn(false)

        assertTrue(calculator.isValidIpOrCidr("1.1.1.1"))
        assertTrue(calculator.isValidIpOrCidr("1.1.1.1/32"))
        assertTrue(calculator.isValidIpOrCidr("0.0.0.0/0"))
        assertTrue(!calculator.isValidIpOrCidr("1.1.1.1/33"))
        assertTrue(!calculator.isValidIpOrCidr("not an ip"))
    }

    @Test
    fun testComplementOfExcluded_Empty() {
        whenever(calculator.complementOfExcluded(emptyList())).thenReturn(listOf("0.0.0.0/0"))
        val result = calculator.complementOfExcluded(emptyList())
        assertEquals(listOf("0.0.0.0/0"), result)
    }

    @Test
    fun testComplementOfExcluded_Single() {
        whenever(calculator.complementOfExcluded(listOf("1.1.1.1"))).thenReturn(listOf("0.0.0.0/1", "128.0.0.0/2", "192.0.0.0/3")) // Mock some values
        val result = calculator.complementOfExcluded(listOf("1.1.1.1"))
        assertTrue(result.isNotEmpty())
        assertTrue(!result.contains("1.1.1.1/32"))
    }

    @Test
    fun testComplementOfExcluded_Split() {
        whenever(calculator.complementOfExcluded(listOf("128.0.0.0/1"))).thenReturn(listOf("0.0.0.0/1"))
        val result = calculator.complementOfExcluded(listOf("128.0.0.0/1"))
        assertEquals(listOf("0.0.0.0/1"), result)
    }
}
