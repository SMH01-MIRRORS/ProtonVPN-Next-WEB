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

package ru.protonmod.next.data.network

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.utils.ProtonLogger
import java.util.concurrent.TimeUnit

@HiltWorker
class SessionRefreshWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val sessionDao: SessionDao,
    private val authApi: ProtonAuthApi
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SessionRefreshWorker"
        private const val WORK_NAME = "session_keep_alive"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SessionRefreshWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = sessionDao.getSession()
        if (session == null) {
            ProtonLogger.d(TAG, "No active session, skipping background refresh")
            return@withContext Result.success()
        }

        ProtonLogger.i(TAG, "Starting background session keep-alive for ${session.userId}")

        try {
            // Making a simple authenticated request to trigger TokenAuthenticator if needed
            val response = authApi.getUser("Bearer ${session.accessToken}", session.sessionId)
            if (response.code == 1000) {
                ProtonLogger.i(TAG, "Background session check successful")
                Result.success()
            } else {
                ProtonLogger.w(TAG, "Background session check returned code ${response.code}")
                Result.retry()
            }
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Background session check failed", e)
            Result.retry()
        }
    }
}
