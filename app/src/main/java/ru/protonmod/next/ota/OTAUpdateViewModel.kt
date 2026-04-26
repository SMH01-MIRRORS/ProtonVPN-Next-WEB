package ru.protonmod.next.ota

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.protonmod.next.data.model.ota.UpdateInfo
import ru.protonmod.next.utils.APKInstaller
import ru.protonmod.next.utils.ProtonLogger
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class UpdateUiState(
    val updateInfo: UpdateInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val error: String? = null,
    val downloadedFile: File? = null
)

@HiltViewModel
class OTAUpdateViewModel @Inject constructor(
    private val otaUpdateManager: OTAUpdateManager,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            otaUpdateManager.latestUpdate.collect { update ->
                if (update != null) {
                    val apkFile = getUpdateFile(update.versionCode)
                    cleanOldUpdates(update.versionCode)
                    _uiState.update { it.copy(
                        updateInfo = update,
                        downloadedFile = if (apkFile.exists()) apkFile else null,
                        downloadProgress = if (apkFile.exists()) 1f else 0f
                    ) }
                }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            otaUpdateManager.checkForUpdatesNow()
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateInfo = null) }
    }

    private fun getUpdateFile(versionCode: Int): File {
        val updateDir = File(context.cacheDir, "updates")
        if (!updateDir.exists()) updateDir.mkdirs()
        return File(updateDir, "update_$versionCode.apk")
    }

    private fun cleanOldUpdates(currentVersionCode: Int) {
        val updateDir = File(context.cacheDir, "updates")
        if (updateDir.exists()) {
            updateDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("update_") && !file.name.contains(currentVersionCode.toString())) {
                    file.delete()
                }
            }
        }
    }

    fun installUpdate(context: Context) {
        _uiState.value.downloadedFile?.let { apkFile ->
            APKInstaller.install(context, apkFile)
        }
    }

    fun startDownload(context: Context, updateInfo: UpdateInfo) {
        if (_uiState.value.isDownloading) return

        _uiState.value = _uiState.value.copy(isDownloading = true, downloadProgress = 0f, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(updateInfo.url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) throw Exception("Failed to download file")

                val body = response.body
                val totalBytes = body.contentLength()
                val apkFile = getUpdateFile(updateInfo.versionCode)
                
                body.source().use { source ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        
                        while (source.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) {
                                _uiState.value = _uiState.value.copy(
                                    downloadProgress = totalRead.toFloat() / totalBytes
                                )
                            }
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isDownloading = false, 
                    downloadProgress = 1f,
                    downloadedFile = apkFile
                )
                
                // We no longer trigger installation automatically as per user request.
                // The UI should now show an "Install" button.

            } catch (e: Exception) {
                ProtonLogger.e("OTAUpdateViewModel", "Download failed", e)
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
}
