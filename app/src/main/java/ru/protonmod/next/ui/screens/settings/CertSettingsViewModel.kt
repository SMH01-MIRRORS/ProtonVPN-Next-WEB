package ru.protonmod.next.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.utils.ProtonLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class CertSettingsUiState(
    val isExtendedCertEnabled: Boolean = false,
    val certId: String = "",
    val certExpires: String? = null,
    val certIssued: String? = null,
    val errorLog: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class CertSettingsViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val vpnRepository: VpnRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CertSettingsUiState())
    val uiState: StateFlow<CertSettingsUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        loadSessionData()
    }

    private fun loadSessionData() {
        viewModelScope.launch {
            sessionDao.getSessionFlow().collect { session ->
                if (session != null) {
                    val certStart = session.wgCertificate?.substringAfter("-----BEGIN CERTIFICATE-----\n")
                        ?.substringBefore("\n")?.take(16) ?: "Unknown"
                    
                    val expiresStr = if (session.certExpiresAt > 0) {
                        dateFormat.format(Date(session.certExpiresAt * 1000))
                    } else null

                    val issuedStr = if (session.certExpiresAt > 0) {
                        val certDurationMillis = if (session.isExtendedCertEnabled) {
                            365L * 24 * 60 * 60 * 1000
                        } else {
                            24L * 60 * 60 * 1000
                        }
                        val issuedMillis = (session.certExpiresAt * 1000) - certDurationMillis
                        dateFormat.format(Date(issuedMillis))
                    } else null

                    _uiState.update {
                        it.copy(
                            isExtendedCertEnabled = session.isExtendedCertEnabled,
                            certId = certStart,
                            certExpires = expiresStr,
                            certIssued = issuedStr
                        )
                    }
                }
            }
        }
    }

    fun setExtendedCertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorLog = null) }
            val session = sessionDao.getSession()
            if (session != null) {
                val result = vpnRepository.setExtendedCertEnabled(
                    enabled,
                    session.accessToken,
                    session.sessionId
                )
                if (result.isFailure) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            errorLog = "Failed to switch certificate mode: ${result.exceptionOrNull()?.message}"
                        ) 
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorLog = "No active session or public key found.") }
            }
        }
    }

    fun forceRefreshCertificate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorLog = null) }
            val session = sessionDao.getSession()
            if (session != null) {
                val result = vpnRepository.forceRefreshCertificate(
                    session.accessToken,
                    session.sessionId
                )
                if (result.isFailure) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorLog = "Force refresh failed: ${result.exceptionOrNull()?.message}"
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorLog = "No active session") }
            }
        }
    }
}
