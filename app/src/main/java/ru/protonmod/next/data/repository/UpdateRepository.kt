package ru.protonmod.next.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.data.model.ota.UpdateInfo
import ru.protonmod.next.data.network.ota.UpdateApi
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val updateApi: UpdateApi,
    @ApplicationContext private val context: Context
) {
    private val updateUrls = listOf(
        context.getString(R.string.url_ota_mirror_1),
        context.getString(R.string.url_ota_mirror_2),
        context.getString(R.string.url_ota_mirror_3)
    )

    suspend fun checkForUpdates(): UpdateInfo? {
        var bestUpdate: UpdateInfo? = null
        for (url in updateUrls) {
            try {
                val response = updateApi.getUpdateMetadata(url)
                
                val channelUpdates = if (BuildConfig.UPDATE_CHANNEL == "nightly") {
                    response.nightly
                } else {
                    response.stable
                }

                val updateInfo = if (BuildConfig.DEBUG) {
                    channelUpdates?.debug
                } else {
                    channelUpdates?.release
                }
                
                if (updateInfo != null && updateInfo.versionCode > BuildConfig.VERSION_CODE) {
                    if (bestUpdate == null || updateInfo.versionCode > bestUpdate.versionCode) {
                        bestUpdate = updateInfo
                    }
                }
            } catch (e: Exception) {
                ProtonLogger.e("UpdateRepository", "Failed to fetch updates from $url", e)
            }
        }
        return bestUpdate
    }
}
