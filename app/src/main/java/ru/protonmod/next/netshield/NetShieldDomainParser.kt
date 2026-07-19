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

import java.util.Locale

internal object NetShieldDomainParser {
    private val domainRegex = Regex("^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$")
    private val hostsRegex = Regex("^(?:0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1)\\s+([^\\s#]+)")

    fun parse(content: String): Set<String> = content.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        val candidate = when {
            line.isBlank() || line.startsWith('!') || line.startsWith('#') || line.startsWith("@@") -> null
            line.startsWith("||") -> line.removePrefix("||").substringBefore('^').substringBefore('$')
            else -> hostsRegex.find(line)?.groupValues?.get(1) ?: line.takeIf(domainRegex::matches)
        }
        candidate
            ?.lowercase(Locale.ROOT)
            ?.trimEnd('.')
            ?.takeIf { domainRegex.matches(it) && it != "localhost" }
    }.toSet()
}
