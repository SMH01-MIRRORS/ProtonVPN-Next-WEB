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

package ru.protonmod.next.data.model

import kotlinx.serialization.Serializable
import ru.protonmod.next.data.local.RecentConnectionEntity
import ru.protonmod.next.data.local.VpnProfileEntity

@Serializable
enum class BackupCategory {
    GENERAL_SETTINGS,
    OBFUSCATION,
    API_BYPASS,
    PROFILES,
    RECENT_CONNECTIONS,
    QUICK_CONNECT,
    SPLIT_TUNNELING,
    VPN_PORT,
    DNS,
    SPOOF_COUNTRY,
    OTA_UPDATES,
    SENTRY_ANALYTICS
}

@Serializable
data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val settings: Map<String, String>? = null,
    val profiles: List<VpnProfileEntity>? = null,
    val recentConnections: List<RecentConnectionEntity>? = null
)
