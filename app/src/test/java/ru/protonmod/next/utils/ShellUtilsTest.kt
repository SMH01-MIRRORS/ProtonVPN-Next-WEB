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

package ru.protonmod.next.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellUtilsTest {

    @Test
    fun testShellSplit() {
        val input = "-Ku -a3 -l:\"\\xe3\\x00\\x06\\xec\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\" -An -f64+se -t5 -n google.com"
        val result = ShellUtils.shellSplit(input)
        
        assertEquals(8, result.size)
        assertEquals("-Ku", result[0])
        assertEquals("-a3", result[1])
        assertEquals("-l:\\xe3\\x00\\x06\\xec\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00", result[2])
        assertEquals("-An", result[3])
        assertEquals("-f64+se", result[4])
        assertEquals("-t5", result[5])
        assertEquals("-n", result[6])
        assertEquals("google.com", result[7])
    }

    @Test
    fun testShellSplitWithSpacesAndQuotes() {
        val input = "-n \"google.com\" -l:\"foo bar\""
        val result = ShellUtils.shellSplit(input)
        assertEquals(3, result.size)
        assertEquals("-n", result[0])
        assertEquals("google.com", result[1])
        assertEquals("-l:foo bar", result[2])
    }
}
