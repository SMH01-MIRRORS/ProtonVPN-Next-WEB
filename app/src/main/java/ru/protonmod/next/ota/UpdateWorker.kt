package ru.protonmod.next.ota

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.protonmod.next.data.repository.UpdateRepository
import ru.protonmod.next.utils.ProtonLogger

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updateRepository: UpdateRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val update = updateRepository.checkForUpdates()
            if (update != null) {
                // TODO: Show notification or trigger UI state
                ProtonLogger.i("UpdateWorker", "New update available: ${update.versionName}")
            }
            Result.success()
        } catch (e: Exception) {
            ProtonLogger.e("UpdateWorker", "Update check failed", e)
            Result.retry()
        }
    }
}
