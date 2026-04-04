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

import kotlinx.coroutines.flow.first
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.model.ota.UpdateResponse

@Singleton
class UpdateRepository @Inject constructor(
    private val updateApi: UpdateApi,
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context
) {
    private val updateUrls = listOf(
        context.getString(R.string.url_ota_mirror_1),
        context.getString(R.string.url_ota_mirror_2)
    )

    suspend fun getAvailableChannels(): Map<String, Boolean> {
        val result = mutableMapOf("stable" to false, "nightly" to false)
        for (url in updateUrls) {
            try {
                val response = updateApi.getUpdateMetadata(url)
                if (response.stable != null) result["stable"] = true
                if (response.nightly != null) result["nightly"] = true
                if (result["stable"] == true && result["nightly"] == true) break
            } catch (e: Exception) {
                // Silently ignore
            }
        }
        return result
    }

    suspend fun checkForUpdates(): UpdateInfo? {
        val selectedChannel = settingsManager.otaUpdateChannel.first()
        var bestUpdate: UpdateInfo? = null
        for (url in updateUrls) {
            try {
                val response = updateApi.getUpdateMetadata(url)
                
                val channelUpdates = if (selectedChannel == "nightly") {
                    response.nightly
                } else {
                    response.stable
                }

                val updateInfo = if (BuildConfig.DEBUG) {
                    channelUpdates?.debug
                } else {
                    channelUpdates?.release
                }
                
                if (updateInfo != null) {
                    val isHigherVersion = updateInfo.versionCode > BuildConfig.VERSION_CODE
                    
                    // Allow switching from Nightly to Stable if versions are equal (e.g. after a release tag)
                    val isSwitchingToStable = selectedChannel == "stable" && 
                                              BuildConfig.UPDATE_CHANNEL == "nightly" && 
                                              updateInfo.versionCode == BuildConfig.VERSION_CODE

                    if (isHigherVersion || isSwitchingToStable) {
                        if (bestUpdate == null || updateInfo.versionCode > bestUpdate.versionCode) {
                            bestUpdate = updateInfo
                        }
                    }
                }
            } catch (e: Exception) {
                ProtonLogger.e("UpdateRepository", "Failed to fetch updates from $url", e)
            }
        }
        return bestUpdate
    }
}
