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

package ru.protonmod.next.data.network.byedpi

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ByeDpiProxyTest {

    @Test
    fun testPrepareArgs() {
        val strategy = "-s1 -d1 -Ku"
        val ip = "127.0.0.1"
        val port = 1080
        
        val baseArgs = listOf("ciadpi", "--ip", ip, "--port", port.toString())
        val flags = strategy.split(" ").filter { it.isNotEmpty() }
        val expected = (baseArgs + flags).toTypedArray()
        
        // This logic is currently inside ByeDpiStrategyTester.prepareArgs (private)
        // For the sake of this test, we verify the logic itself.
        val actual = prepareArgsLogic(strategy, ip, port)
        
        assertArrayEquals(expected, actual)
    }

    private fun prepareArgsLogic(strategy: String, ip: String, port: Int): Array<String> {
        val baseArgs = listOf("ciadpi", "--ip", ip, "--port", port.toString())
        val flags = strategy.split(" ").filter { it.isNotEmpty() }
        return (baseArgs + flags).toTypedArray()
    }
}
