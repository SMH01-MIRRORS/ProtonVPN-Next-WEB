package ru.protonmod.next.data.repository

import ru.protonmod.next.BuildConfig
import ru.protonmod.next.data.model.ota.UpdateInfo
import ru.protonmod.next.data.network.ota.UpdateApi
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val updateApi: UpdateApi
) {
    private val updateUrls = listOf(
        "https://protonnext.dpdns.org/update.json",
        "https://protonnext.qzz.io/update.json"
    )

    suspend fun checkForUpdates(): UpdateInfo? {
        for (url in updateUrls) {
            try {
                val response = updateApi.getUpdateMetadata(url)
                val updateInfo = if (BuildConfig.DEBUG) response.debug else response.release
                
                if (updateInfo != null && updateInfo.versionCode > BuildConfig.VERSION_CODE) {
                    return updateInfo
                }
                return null // Success but no update
            } catch (e: Exception) {
                ProtonLogger.e("UpdateRepository", "Failed to fetch updates from $url", e)
            }
        }
        return null
    }
}
