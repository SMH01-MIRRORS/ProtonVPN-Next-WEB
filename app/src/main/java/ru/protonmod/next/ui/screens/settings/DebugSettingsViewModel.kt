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

package ru.protonmod.next.ui.screens.settings

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.protonmod.next.ProtonNextApp
import ru.protonmod.next.data.local.AppDatabase
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServerMapper
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.vpn.AmneziaConfigGenerator
import ru.protonmod.next.vpn.AmneziaVpnManager
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.system.exitProcess

data class DebugUiState(
    val session: SessionEntity? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val servers: List<LogicalServer> = emptyList()
)

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionDao: SessionDao,
    private val serverDao: ServerDao,
    private val settingsManager: SettingsManager,
    private val vpnManager: AmneziaVpnManager,
    private val configGenerator: AmneziaConfigGenerator,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            val session = sessionDao.getSession()
            val servers = serverDao.getAllServers().map { ServerMapper.toDomain(it) }
            _uiState.value = _uiState.value.copy(session = session, servers = servers)
        }
    }

    fun forceRefreshCertificate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = vpnManager.forceRefreshCertificate()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = if (result.isSuccess) "Certificate refreshed" else "Failed: ${result.exceptionOrNull()?.message}"
            )
            refreshData()
        }
    }

    fun exportLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "proton_logs_$timestamp.txt"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                val process = Runtime.getRuntime().exec("logcat -d")
                process.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Logs exported to Downloads/$fileName"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun exportConfig(server: LogicalServer) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val session = sessionDao.getSession() ?: throw Exception("No active session")
                val physicalServer = server.servers.firstOrNull() ?: throw Exception("No physical servers available")
                
                // Get real settings for more accurate export
                val isObfuscationEnabled = settingsManager.obfuscationEnabled.first()
                val params = if (isObfuscationEnabled) {
                    AmneziaVpnManager.ObfuscationParams(
                        jc = settingsManager.awgJc.first(), jmin = settingsManager.awgJmin.first(), jmax = settingsManager.awgJmax.first(),
                        s1 = settingsManager.awgS1.first(), s2 = settingsManager.awgS2.first(),
                        h1 = settingsManager.awgH1.first(), h2 = settingsManager.awgH2.first(), h3 = settingsManager.awgH3.first(), h4 = settingsManager.awgH4.first(),
                        i1 = settingsManager.awgI1.first(), i2 = settingsManager.awgI2.first(), i3 = settingsManager.awgI3.first(), i4 = settingsManager.awgI4.first(), i5 = settingsManager.awgI5.first(),
                        customId = settingsManager.awgId.first(), ip = settingsManager.awgIp.first(), ib = settingsManager.awgIb.first()
                    )
                } else {
                    AmneziaVpnManager.ObfuscationParams(0, 0, 0, 0, 0, "", "", "", "", "", "", "", "", "", "", "", "")
                }
                
                val userDns = settingsManager.customDns.first().trim()
                val activeDns = if (userDns.isNotEmpty()) userDns else "10.2.0.1"
                
                val selectedPort = settingsManager.vpnPort.first().let { port ->
                    if (port == 0) 1194 else port
                }

                // Resolve domain to IP if possible, fallback to domain
                val targetIp = try {
                    InetAddress.getByName(physicalServer.domain).hostAddress ?: physicalServer.domain
                } catch (_: Exception) {
                    physicalServer.domain
                }

                // Generating a standard config for debugging export
                val config = configGenerator.buildConfig(
                    serverPublicKey = physicalServer.wgPublicKey ?: "",
                    privateKey = session.wgPrivateKey ?: "",
                    localIp = "10.2.0.2",
                    dnsServer = activeDns,
                    targetIp = targetIp,
                    port = selectedPort,
                    obfuscationParams = params
                )

                val fileName = "proton_${server.name.replace(" ", "_")}.conf"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                
                file.writeText(config)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Config exported to Downloads/$fileName"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun nukeEverything() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                vpnManager.disconnect()
                database.clearAllTables()
                settingsManager.clearAll()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "All data cleared. Restarting app..."
                )
                
                withContext(Dispatchers.Main) {
                    // Force restart app
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                    exitProcess(0)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Reset failed: ${e.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun getDeviceInfo(): String {
        return """
            Model: ${Build.MODEL}
            Brand: ${Build.BRAND}
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            App Version: ${ru.protonmod.next.BuildConfig.VERSION_NAME} (${ru.protonmod.next.BuildConfig.VERSION_CODE})
        """.trimIndent()
    }
}
