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

object ShellUtils {
    /**
     * Splits a string into tokens as a shell would, handling quotes and escaping.
     * Based on https://gist.github.com/raymyers/8077031
     */
    fun shellSplit(string: CharSequence): List<String> {
        val tokens: MutableList<String> = ArrayList()
        var quoteChar = ' '
        var escaping = false
        var quoting = false
        var lastCloseQuoteIndex = Int.MIN_VALUE
        var current = StringBuilder()

        for (i in string.indices) {
            val c = string[i]

            if (escaping) {
                current.append(c)
                escaping = false
            } else if (c == '\\' && quoting) {
                if (i + 1 < string.length && string[i + 1] == quoteChar) {
                    escaping = true
                } else {
                    current.append(c)
                }
            } else if (quoting && c == quoteChar) {
                quoting = false
                lastCloseQuoteIndex = i
            } else if (!quoting && (c == '\'' || c == '"')) {
                quoting = true
                quoteChar = c
            } else if (!quoting && Character.isWhitespace(c)) {
                if (current.isNotEmpty() || lastCloseQuoteIndex == i - 1) {
                    tokens.add(current.toString())
                    current = StringBuilder()
                }
            } else {
                current.append(c)
            }
        }

        if (current.isNotEmpty() || lastCloseQuoteIndex == string.length - 1) {
            tokens.add(current.toString())
        }

        return tokens
    }
}
