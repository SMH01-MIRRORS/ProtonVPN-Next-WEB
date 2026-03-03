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

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = ProtonNextTheme.colors

    // User credentials state
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 2FA code state
    var totpCode by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    AnimatedContent(
        targetState = uiState,
        label = "login_to_captcha_transition"
    ) { state ->
        when (state) {
            is LoginUiState.RequiresCaptcha -> {
                // --- CAPTCHA Verification View ---
                CaptchaScreen(
                    webUrl = state.webUrl,
                    sessionId = state.sessionId,
                    onDismiss = { viewModel.resetError() },
                    onCaptchaSolved = { verifiedToken ->
                        viewModel.retryWithCaptcha(state, verifiedToken)
                    }
                )
            }

            is LoginUiState.Requires2FA -> {
                // --- 2FA Input View ---
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.backgroundNorm),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.title_2fa),
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textNorm,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.msg_2fa_instruction),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textWeak
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        OutlinedTextField(
                            value = totpCode,
                            onValueChange = { totpCode = it },
                            label = { Text(stringResource(R.string.hint_2fa_code)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.brandNorm,
                                unfocusedBorderColor = colors.shade20,
                                focusedTextColor = colors.textNorm,
                                unfocusedTextColor = colors.textNorm
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                viewModel.submit2FA(state.sessionId, state.tempAccessToken, state.refreshToken, totpCode)
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                            enabled = totpCode.isNotBlank()
                        ) {
                            Text(stringResource(R.string.btn_verify), fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = onBackClick,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text(stringResource(R.string.btn_cancel), color = colors.textWeak)
                        }
                    }
                }
            }

            else -> {
                // --- Standard Login View ---
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.backgroundNorm),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textNorm,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.login_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textWeak
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.hint_username)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.brandNorm,
                                unfocusedBorderColor = colors.shade20,
                                focusedTextColor = colors.textNorm,
                                unfocusedTextColor = colors.textNorm
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.hint_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = stringResource(R.string.desc_toggle_password), tint = colors.iconWeak)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.brandNorm,
                                unfocusedBorderColor = colors.shade20,
                                focusedTextColor = colors.textNorm,
                                unfocusedTextColor = colors.textNorm
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Display error message if any
                        if (uiState is LoginUiState.Error) {
                            Text(
                                text = (uiState as LoginUiState.Error).message,
                                color = colors.notificationError,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        Button(
                            onClick = { viewModel.login(username, password) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                            enabled = uiState !is LoginUiState.Loading && username.isNotBlank() && password.isNotBlank()
                        ) {
                            if (uiState is LoginUiState.Loading) {
                                CircularProgressIndicator(color = colors.textInverted, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.btn_login), fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = onBackClick,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = uiState !is LoginUiState.Loading
                        ) {
                            Text(stringResource(R.string.btn_cancel), color = colors.textWeak)
                        }
                    }
                }
            }
        }
    }
}