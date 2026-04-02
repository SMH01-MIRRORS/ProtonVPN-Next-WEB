package ru.protonmod.next.ota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.protonmod.next.data.model.ota.UpdateInfo
import ru.protonmod.next.data.repository.UpdateRepository
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
    private val updateRepository: UpdateRepository,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState = _uiState.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch {
            val update = updateRepository.checkForUpdates()
            _uiState.value = _uiState.value.copy(updateInfo = update)
        }
    }

    fun startDownload(context: android.content.Context, updateInfo: UpdateInfo) {
        if (_uiState.value.isDownloading) return

        _uiState.value = _uiState.value.copy(isDownloading = true, downloadProgress = 0f, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(updateInfo.url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) throw Exception("Failed to download file")

                val body = response.body ?: throw Exception("Response body is empty")
                val totalBytes = body.contentLength()
                val updateDir = File(context.cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()
                
                val apkFile = File(updateDir, "update_${updateInfo.versionCode}.apk")
                
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
                
                // Trigger installation
                viewModelScope.launch(Dispatchers.Main) {
                    APKInstaller.install(context, apkFile)
                }

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
