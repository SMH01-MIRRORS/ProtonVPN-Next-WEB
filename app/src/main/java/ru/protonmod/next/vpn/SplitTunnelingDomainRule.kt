/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.vpn

import java.util.Locale

/** Normalizes exact and leading-wildcard domain rules used by split tunneling. */
internal object SplitTunnelingDomainRule {
    private val labelPattern = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    fun normalize(input: String): String? {
        val trimmed = input.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return null

        val wildcard = trimmed.startsWith("*.")
        val domain = if (wildcard) trimmed.removePrefix("*.") else trimmed
        if ('*' in domain || domain.isEmpty() || domain.length > 253) return null

        val labels = domain.split('.')
        if ((!wildcard && labels.size < 2) || labels.any { !labelPattern.matches(it) }) return null

        val topLevelDomain = labels.last()
        if (topLevelDomain.length < 2 && !topLevelDomain.startsWith("xn--")) return null

        return if (wildcard) "*.$domain" else domain
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
