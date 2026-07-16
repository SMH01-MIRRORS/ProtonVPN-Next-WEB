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

package ru.protonmod.next.ui.screens.dashboard

import androidx.compose.runtime.Immutable
import java.util.Locale

/** Aggregated rx/tx/usage for a calendar period (today / month / year). */
@Immutable
data class TrafficPeriodSummary(
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val usageSeconds: Long = 0L,
)

/** One point of a traffic chart (a day, a month or a year). */
@Immutable
data class TrafficChartPoint(
    val label: String,
    val totalBytes: Long,
)

/** State for the dashboard statistics card (port of the desktop stats slider). */
@Immutable
data class TrafficStatsUiState(
    val enabled: Boolean = true,
    val today: TrafficPeriodSummary = TrafficPeriodSummary(),
    val month: TrafficPeriodSummary = TrafficPeriodSummary(),
    val year: TrafficPeriodSummary = TrafficPeriodSummary(),
    /** Last 30 days, oldest first, missing days filled with zeroes. */
    val dailyChart: List<TrafficChartPoint> = emptyList(),
    /** Last 12 months, oldest first, missing months filled with zeroes. */
    val monthlyChart: List<TrafficChartPoint> = emptyList(),
    /** All known years, oldest first. */
    val yearlyChart: List<TrafficChartPoint> = emptyList(),
)

/** Formats a byte count the same way the desktop dashboard does (B/KB/MB/GB/TB). */
fun formatStatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    var value = bytes.toDouble()
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

/** Formats a duration like the desktop dashboard: "3h 25m", "12m", "45s". */
fun formatStatDuration(totalSeconds: Long): String {
    if (totalSeconds < 60L) return "${totalSeconds}s"
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    return when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
