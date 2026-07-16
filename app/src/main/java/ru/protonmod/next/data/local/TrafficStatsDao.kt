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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficStatsDao {

    /** The whole table is small (one row per day), so observing it fully is cheap. */
    @Query("SELECT * FROM traffic_stats ORDER BY day ASC")
    fun observeAll(): Flow<List<TrafficStatsEntity>>

    @Query("SELECT * FROM traffic_stats WHERE day = :day")
    suspend fun getDay(day: String): TrafficStatsEntity?

    @Upsert
    suspend fun upsert(entity: TrafficStatsEntity)

    /**
     * Atomically accumulates deltas into the given day's row.
     * Only one writer exists (TrafficStatsRecorder), the transaction guards
     * against races with backup/restore style bulk writes.
     */
    @Transaction
    suspend fun addDelta(day: String, rxBytes: Long, txBytes: Long, usageSeconds: Long) {
        val existing = getDay(day)
        upsert(
            TrafficStatsEntity(
                day = day,
                rxBytes = (existing?.rxBytes ?: 0L) + rxBytes,
                txBytes = (existing?.txBytes ?: 0L) + txBytes,
                usageSeconds = (existing?.usageSeconds ?: 0L) + usageSeconds,
            )
        )
    }

    @Query("DELETE FROM traffic_stats")
    suspend fun clearAll()
}
