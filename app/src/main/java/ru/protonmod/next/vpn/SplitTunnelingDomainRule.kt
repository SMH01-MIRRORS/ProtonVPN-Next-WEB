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

import java.net.IDN
import java.util.Locale

/** Normalizes exact and leading-wildcard domain rules used by split tunneling. */
internal object SplitTunnelingDomainRule {
    private val labelPattern = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    fun normalize(input: String): String? {
        val trimmed = input.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return null

        val wildcard = trimmed.startsWith("*.") || trimmed.startsWith('.')
        val unicodeDomain = when {
            trimmed.startsWith("*.") -> trimmed.removePrefix("*.")
            trimmed.startsWith('.') -> trimmed.removePrefix(".")
            else -> trimmed
        }
        if ('*' in unicodeDomain || unicodeDomain.isEmpty()) return null

        val domain = runCatching {
            IDN.toASCII(unicodeDomain, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        }.getOrNull() ?: return null
        if (domain.isEmpty() || domain.length > 253) return null

        val labels = domain.split('.')
        if ((!wildcard && labels.size < 2) || labels.any { !labelPattern.matches(it) }) return null

        val topLevelDomain = labels.last()
        if (topLevelDomain.length < 2 && !topLevelDomain.startsWith("xn--")) return null

        return if (wildcard) "*.$domain" else domain
    }

    fun toDisplay(rule: String): String {
        val normalized = normalize(rule) ?: return rule
        val wildcard = normalized.startsWith("*.")
        val domain = normalized.removePrefix("*.")
        val unicodeDomain = IDN.toUnicode(domain)
        return if (wildcard) "*.$unicodeDomain" else unicodeDomain
    }

    fun exactDomains(rules: Collection<String>): List<String> = rules
        .mapNotNull(::normalize)
        .filterNot { it.startsWith("*.") }
        .distinct()
        .sorted()

    fun domainSuffixes(rules: Collection<String>): List<String> = rules
        .mapNotNull(::normalize)
        .filter { it.startsWith("*.") }
        .map { it.removePrefix("*.") }
        .distinct()
        .sorted()
}
