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

package ru.protonmod.next.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import io.sentry.Sentry
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.repository.AuthRepository
import javax.inject.Inject

// --- API Error Models ---

@Serializable
data class ProtonErrorResponse(
    @SerialName("Code") val code: Int,
    @SerialName("Details") val details: ProtonErrorDetails? = null
)

@Serializable
data class ProtonErrorDetails(
    @SerialName("WebUrl") val webUrl: String? = null,
    @SerialName("HumanVerificationToken") val humanVerificationToken: String? = null
)

/**
 * Custom exception to trigger the Captcha WebView in the UI.
 * Carries the sessionId to ensure the WebView binds the token properly.
 */
class CaptchaRequiredException(val webUrl: String, val token: String, val sessionId: String?) : Exception("Human Verification Required")

// --- UI State ---

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()

    /**
     * State triggered when Proton requires a Captcha.
     */
    data class RequiresCaptcha(
        val webUrl: String,
        val username: String,
        val passwordRaw: String,
        val captchaToken: String,
        val isAnonymous: Boolean = false,
        val sessionId: String? = null // Passed to WebView headers
    ) : LoginUiState()

    data class Requires2FA(
        val sessionId: String,
        val tempAccessToken: String,
        val refreshToken: String
    ) : LoginUiState()

    data class Success(
        val accessToken: String,
        val userId: String
    ) : LoginUiState()

    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val isApiBypassEnabled = settingsManager.apiBypassEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun login(username: String, passwordRaw: String, captchaToken: String? = null) {
        if (username.isBlank() || passwordRaw.isBlank()) return

        val startTime = System.currentTimeMillis()
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.login(username, passwordRaw, captchaToken)
                .onSuccess { response ->
                    // Metrics
                    val duration = System.currentTimeMillis() - startTime
                    Sentry.metrics().distribution("login_latency", duration.toDouble())
                    Sentry.metrics().count("login_success", 1.0)

                    val scopes = response.scopes
                    if (scopes.contains("twofactor")) {
                        _uiState.value = LoginUiState.Requires2FA(
                            sessionId = response.sessionId ?: "",
                            tempAccessToken = response.accessToken ?: "",
                            refreshToken = response.refreshToken ?: ""
                        )
                    } else {
                        _uiState.value = LoginUiState.Success(
                            accessToken = response.accessToken ?: "",
                            userId = response.userId ?: ""
                        )
                    }
                }
                .onFailure { exception ->
                    // Metrics
                    Sentry.metrics().count("login_error", 1.0)

                    if (exception is CaptchaRequiredException) {
                        _uiState.value = LoginUiState.RequiresCaptcha(
                            webUrl = exception.webUrl,
                            username = username,
                            passwordRaw = passwordRaw,
                            captchaToken = exception.token,
                            isAnonymous = false,
                            sessionId = exception.sessionId
                        )
                    } else {
                        _uiState.value = LoginUiState.Error(
                            exception.localizedMessage ?: "An unexpected authentication error occurred"
                        )
                    }
                }
        }
    }

    fun submit2FA(sessionId: String, tempAccessToken: String, refreshToken: String, totpCode: String) {
        if (totpCode.isBlank()) return

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.verify2FA(sessionId, tempAccessToken, refreshToken, totpCode)
                .onSuccess { response ->
                    _uiState.value = LoginUiState.Success(
                        accessToken = response.accessToken ?: "",
                        userId = response.userId ?: ""
                    )
                }
                .onFailure { exception ->
                    _uiState.value = LoginUiState.Error(exception.localizedMessage ?: "Two-factor verification failed")
                }
        }
    }

    fun loginAnonymous(captchaToken: String? = null) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            authRepository.loginAnonymous(captchaToken)
                .onSuccess { response ->
                    _uiState.value = LoginUiState.Success(
                        accessToken = response.accessToken ?: "",
                        userId = response.userId ?: ""
                    )
                }
                .onFailure { exception ->
                    if (exception is CaptchaRequiredException) {
                        _uiState.value = LoginUiState.RequiresCaptcha(
                            webUrl = exception.webUrl,
                            username = "",
                            passwordRaw = "",
                            captchaToken = exception.token,
                            isAnonymous = true,
                            sessionId = exception.sessionId
                        )
                    } else {
                        _uiState.value = LoginUiState.Error(exception.localizedMessage ?: "Guest login failed")
                    }
                }
        }
    }

    fun retryWithCaptcha(state: LoginUiState.RequiresCaptcha, verifiedToken: String) {
        if (state.isAnonymous) {
            loginAnonymous(verifiedToken)
        } else {
            login(state.username, state.passwordRaw, verifiedToken)
        }
    }

    fun resetError() {
        if (_uiState.value is LoginUiState.Error || _uiState.value is LoginUiState.RequiresCaptcha) {
            _uiState.value = LoginUiState.Idle
        }
    }
}