package ru.protonmod.next.ota

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.model.ota.UpdateInfo
import ru.protonmod.next.data.repository.UpdateRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OTAUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val updateRepository: UpdateRepository
) {
    companion object {
        private const val WORK_NAME = "ota_update_check"
    }

    private val _latestUpdate = MutableStateFlow<UpdateInfo?>(null)
    val latestUpdate = _latestUpdate.asStateFlow()

    suspend fun scheduleUpdateCheck() {
        if (ru.protonmod.next.BuildConfig.IS_PRIVACY_BUILD) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val frequency = settingsManager.otaUpdateFrequency.first()
        if (frequency == "disabled") {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }

        val repeatInterval = when (frequency) {
            "hourly" -> 1L to TimeUnit.HOURS
            "daily" -> 1L to TimeUnit.DAYS
            "weekly" -> 7L to TimeUnit.DAYS
            "monthly" -> 30L to TimeUnit.DAYS
            else -> 1L to TimeUnit.DAYS
        }

        val request = PeriodicWorkRequest.Builder(
            UpdateWorker::class.java,
            repeatInterval.first,
            repeatInterval.second
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    suspend fun checkForUpdatesNow(): UpdateInfo? {
        val update = updateRepository.checkForUpdates()
        settingsManager.setOtaLastCheckTime(System.currentTimeMillis())
        _latestUpdate.value = update
        return update
    }
}
