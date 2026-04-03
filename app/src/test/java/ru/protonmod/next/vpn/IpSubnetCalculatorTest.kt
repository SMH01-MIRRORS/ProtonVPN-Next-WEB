package ru.protonmod.next.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpSubnetCalculatorTest {

    @Test
    fun testIsValidIpOrCidr() {
        assertTrue(IpSubnetCalculator.isValidIpOrCidr("1.1.1.1"))
        assertTrue(IpSubnetCalculator.isValidIpOrCidr("1.1.1.1/32"))
        assertTrue(IpSubnetCalculator.isValidIpOrCidr("0.0.0.0/0")) // Fix 6: Allow /0
        assertTrue(!IpSubnetCalculator.isValidIpOrCidr("1.1.1.1/33"))
        assertTrue(!IpSubnetCalculator.isValidIpOrCidr("not an ip"))
    }

    @Test
    fun testComplementOfExcluded_Empty() {
        val result = IpSubnetCalculator.complementOfExcluded(emptyList())
        assertEquals(listOf("0.0.0.0/0"), result)
    }

    @Test
    fun testComplementOfExcluded_Single() {
        val result = IpSubnetCalculator.complementOfExcluded(listOf("1.1.1.1"))
        // Complement of 1.1.1.1/32 should be a list of CIDRs covering everything else
        assertTrue(result.isNotEmpty())
        assertTrue(!result.contains("1.1.1.1/32"))
    }

    @Test
    fun testRangeToCidrs_FullSpace() {
        // Fix 2: Handle start = 0 correctly
        // Accessing private method via reflection or just testing public complementOfExcluded
        // which uses it.
        val result = IpSubnetCalculator.complementOfExcluded(emptyList())
        assertEquals(listOf("0.0.0.0/0"), result)
    }

    @Test
    fun testComplementOfExcluded_Split() {
        // Exclude everything from 128.0.0.0 to 255.255.255.255
        val result = IpSubnetCalculator.complementOfExcluded(listOf("128.0.0.0/1"))
        assertEquals(listOf("0.0.0.0/1"), result)
    }
}
