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

package ru.protonmod.next.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aggregated VPN traffic statistics for a single calendar day (device local time).
 * Mirrors the daily rx/tx/usage aggregation used by the desktop dashboard.
 */
@Entity(tableName = "traffic_stats")
data class TrafficStatsEntity(
    /** ISO local date, e.g. "2026-07-16". Lexicographic order == chronological order. */
    @PrimaryKey val day: String,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    /** Seconds the VPN tunnel was up during this day. */
    val usageSeconds: Long = 0L,
)
