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

package ru.protonmod.next.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.data.model.ota.UpdateInfo
import ru.protonmod.next.data.model.ota.UpdateResponse
import ru.protonmod.next.data.network.ota.UpdateApi
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import ru.protonmod.next.data.local.SettingsManager

private val json = Json { ignoreUnknownKeys = true }

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

    /**
     * Fetches and parses update metadata from the given URL.
     * Validates that the response Content-Type is JSON before attempting deserialization.
     */
    private suspend fun fetchUpdateResponse(url: String): UpdateResponse {
        val response = updateApi.getUpdateMetadata(url)
        val body = response.body()
        val contentType = response.headers()["Content-Type"] ?: ""

        if (!response.isSuccessful || body == null) {
            val errorBody = response.errorBody()?.string()?.take(200)
            ProtonLogger.crashReporter?.captureMessage(
                "OTA update fetch failed: HTTP ${response.code()} from $url",
                "ERROR",
                mapOf(
                    "ota_url" to url,
                    "http_status" to response.code().toString(),
                    "error_body_preview" to (errorBody ?: "(empty)")
                )
            )
            throw HttpException(response)
        }

        if (!contentType.contains("application/json", ignoreCase = true)) {
            val bodyPreview = body.string().take(200)
            ProtonLogger.crashReporter?.captureMessage(
                "OTA endpoint returned non-JSON response (Content-Type: '$contentType'). " +
                "URL may have been tampered. URL: $url",
                "ERROR",
                mapOf(
                    "ota_url" to url,
                    "content_type" to contentType,
                    "response_body_preview" to bodyPreview
                )
            )
            throw IllegalStateException(
                "OTA update URL '$url' returned non-JSON content (Content-Type: '$contentType'). " +
                "Expected 'application/json'. Response starts with: ${bodyPreview.take(80)}"
            )
        }

        return json.decodeFromString(UpdateResponse.serializer(), body.string())
    }


    suspend fun checkForUpdates(): UpdateInfo? {
        if (BuildConfig.IS_PRIVACY_BUILD) return null

        val frequency = settingsManager.otaUpdateFrequency.first()
        if (frequency == "disabled") return null

        val selectedChannel = BuildConfig.UPDATE_CHANNEL
        var bestUpdate: UpdateInfo? = null
        for (url in updateUrls) {
            try {
                val urlWithCacheBuster = if (url.contains("?")) {
                    "$url&t=${System.currentTimeMillis()}"
                } else {
                    "$url?t=${System.currentTimeMillis()}"
                }

                val response = fetchUpdateResponse(urlWithCacheBuster)
                
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
                    if (updateInfo.versionCode > BuildConfig.VERSION_CODE) {
                        if (bestUpdate == null || updateInfo.versionCode > bestUpdate.versionCode) {
                            bestUpdate = updateInfo
                        }
                    }
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                ProtonLogger.e("UpdateRepository", "HTTP ${e.code()} from $url: $errorBody", e)
            } catch (e: Exception) {
                ProtonLogger.e("UpdateRepository", "Failed to fetch updates from $url", e)
            }
        }
        return bestUpdate
    }
}
