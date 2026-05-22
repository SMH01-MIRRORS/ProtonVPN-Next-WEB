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

import java.util.regex.Pattern

/**
 * Utility to scrub Personally Identifiable Information (PII) from logs and events
 * before they are sent to external services like Sentry.
 */
object PiiScrubber {
    
    // IPv4 Address regex
    private val IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    
    // IPv6 Address regex (simplified but covers most cases)
    private val IPV6_PATTERN = Pattern.compile(
        "\\b(?:[A-Fa-f0-9]{1,4}:){2,7}[A-Fa-f0-9]{1,4}\\b|" +
        "\\b(?:[A-Fa-f0-9]{1,4}:){1,7}:\\b|" +
        "\\b:(?::[A-Fa-f0-9]{1,4}){1,7}\\b"
    )
    
    // Sensitive key-value pairs in logs (e.g., "accessToken=...", "sessionId: ...")
    private val SENSITIVE_KV_REGEX = Regex(
        "(?i)[\"']?\\b(accessToken|refreshToken|sessionId|captchaToken|token|privateKey|presharedKey|pass|secret|auth|nonce|wgPrivateKey|wgCertificate)\\b[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9._\\-+=/]{10,})[\"']?"
    )
    
    // URL query parameters that might contain tokens
    private val URL_QUERY_TOKEN_REGEX = Regex(
        "(?i)\\b(token|sessionId|access_token|refresh_token|captchaToken)=([^&\\s]+)"
    )
    
    // Config markers to detect VPN configuration blocks
    private val CONFIG_MARKERS = listOf(
        "[Interface]", "[Peer]", "PrivateKey", "PresharedKey", "PublicKey", 
        "Address", "DNS", "AllowedIPs", "Endpoint", "Jc =", "Jmin ="
    )

    /**
     * Scrubs PII from the given input string.
     */
    fun scrub(input: String?): String {
        if (input == null) return ""
        var result = input

        // 1. Detect and redact whole configuration blocks
        if (isConfigBlock(result)) {
            return "[VPN_CONFIG_REDACTED]"
        }

        // 2. Redact IP Addresses
        result = IPV4_PATTERN.matcher(result).replaceAll("[IPv4]")
        result = IPV6_PATTERN.matcher(result).replaceAll("[IPv6]")

        // 3. Redact Sensitive Key-Value pairs
        result = SENSITIVE_KV_REGEX.replace(result) { match ->
            val group2 = match.groups[2] ?: return@replace match.value
            val startInMatch = group2.range.first - match.range.first
            val endInMatch = group2.range.last + 1 - match.range.first
            
            val prefix = match.value.substring(0, startInMatch)
            val suffix = match.value.substring(endInMatch)
            prefix + "[REDACTED]" + suffix
        }

        // 4. Redact Tokens in URL query parameters
        result = URL_QUERY_TOKEN_REGEX.replace(result) { match ->
            val group2 = match.groups[2] ?: return@replace match.value
            val startInMatch = group2.range.first - match.range.first
            val endInMatch = group2.range.last + 1 - match.range.first
            
            val prefix = match.value.substring(0, startInMatch)
            val suffix = match.value.substring(endInMatch)
            prefix + "[REDACTED]" + suffix
        }

        return result
    }

    /**
     * Checks if the string looks like a VPN configuration block.
     */
    private fun isConfigBlock(input: String): Boolean {
        // A config block usually has multiple markers and is multi-line
        val markersFound = CONFIG_MARKERS.count { input.contains(it, ignoreCase = true) }
        return markersFound >= 3 && (input.contains("\n") || input.length > 200)
    }
}
