/*
 * Copyright (c) 2024 Proton Technologies AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

@Composable
fun WelcomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()

    var isVisible by remember { mutableStateOf(false) }

    // Start entry animations
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    // Handle successful login/guest auth
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onNavigateToHome()
        }
    }

    val captchaState = uiState as? LoginUiState.RequiresCaptcha

    AnimatedContent(
        targetState = captchaState,
        label = "welcome_to_captcha_transition"
    ) { currentCaptcha ->
        if (currentCaptcha != null) {
            CaptchaScreen(
                webUrl = currentCaptcha.webUrl,
                sessionId = currentCaptcha.sessionId,
                onDismiss = { viewModel.resetError() },
                onCaptchaSolved = { verifiedToken ->
                    viewModel.retryWithCaptcha(currentCaptcha, verifiedToken)
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.backgroundNorm)
            ) {
                // Top section matching the exact behavior of XML (vpn_gradient_bg + vpn_welcome_globe)
                // Убрали AnimatedVisibility для глобуса и градиента
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f), // Takes all available space above the text/buttons
                    contentAlignment = Alignment.Center // Center the globe perfectly in the top area
                ) {
                    // 1. Native Compose Gradient (Replaces vpn_gradient_bg.xml)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x6611D8CC), // startColor from XML
                                        Color(0x006E4BFF)  // endColor from XML
                                    )
                                )
                            )
                    )

                    // 2. The Globe
                    // Using ContentScale.Fit and fillMaxSize(0.9f) makes it adaptively large
                    // whether you use a vector (.xml) or a raster image (.webp)
                    Image(
                        painter = painterResource(id = R.drawable.vpn_welcome_globe),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(0.9f)
                    )
                }

                // Bottom section with text and buttons, wrapped in a scroll view for smaller screens
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(800, delayMillis = 200)) + slideInVertically(tween(800, delayMillis = 200)) { it / 4 }
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textNorm,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(800, delayMillis = 400)) + slideInVertically(tween(800, delayMillis = 400)) { it / 4 }
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textWeak,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(800, delayMillis = 600)) + slideInVertically(tween(800, delayMillis = 600)) { it / 4 }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Primary Action: Continue as Guest (Matching CredentialLessWelcome behavior)
                            Button(
                                onClick = { viewModel.loginAnonymous() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                enabled = uiState !is LoginUiState.Loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.brandNorm,
                                    contentColor = colors.textInverted
                                )
                            ) {
                                if (uiState is LoginUiState.Loading) {
                                    CircularProgressIndicator(
                                        color = colors.textInverted,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.btn_continue_guest),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Create Account
                            Button(
                                onClick = onNavigateToRegister,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                enabled = uiState !is LoginUiState.Loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.interactionNorm,
                                    contentColor = colors.textInverted
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_create_account),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Secondary Action: Sign In
                            OutlinedButton(
                                onClick = onNavigateToLogin,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                enabled = uiState !is LoginUiState.Loading,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.textNorm
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(colors.separatorNorm)
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_login),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Error Display
                            if (uiState is LoginUiState.Error) {
                                Text(
                                    text = (uiState as LoginUiState.Error).message,
                                    color = colors.notificationError,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}