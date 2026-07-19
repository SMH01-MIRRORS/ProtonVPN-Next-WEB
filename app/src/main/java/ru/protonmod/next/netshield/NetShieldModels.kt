/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.netshield

enum class NetShieldLevel {
    DISABLED,
    MALWARE,
    ADS_TRACKERS,
    ADS_TRACKERS_ADULT;

    val enabled: Boolean get() = this != DISABLED
}

enum class NetShieldCategory { MALWARE, ADS, TRACKERS, ADULT }

data class NetShieldStats(
    val malwareBlocked: Long = 0,
    val adsBlocked: Long = 0,
    val trackersBlocked: Long = 0,
    val savedBytes: Long = 0,
)

data class NetShieldRuleSet(
    val tag: String,
    val path: String,
    val category: NetShieldCategory,
)

data class NetShieldListState(
    val isUpdating: Boolean = false,
    val lastUpdatedAt: Long = 0,
    val domainCount: Int = 0,
    val error: String? = null,
)
