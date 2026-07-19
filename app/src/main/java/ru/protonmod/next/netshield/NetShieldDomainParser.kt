/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
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
