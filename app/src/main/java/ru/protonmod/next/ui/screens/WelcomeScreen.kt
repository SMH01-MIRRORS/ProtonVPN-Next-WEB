/*
 * Copyright (c) 2024 Proton Technologies AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
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
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@Composable
fun WelcomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToApiBypassSettings: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isWarpLoading by viewModel.isWarpLoading.collectAsStateWithLifecycle()
    val isApiBypassEnabled by viewModel.isApiBypassEnabled.collectAsStateWithLifecycle()
    val apiBypassStrategy by viewModel.apiBypassStrategy.collectAsStateWithLifecycle()
    val isTablet = isTablet()
    val context = LocalContext.current

    var isVisible by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.loginAnonymous()
        }
    }

    val checkVpnAndLoginAnonymous: () -> Unit = {
        if (isApiBypassEnabled && apiBypassStrategy == SettingsManager.STRATEGY_WARP) {
            try {
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    viewModel.loginAnonymous()
                }
            } catch (_: SecurityException) {
                viewModel.loginAnonymous()
            }
        } else {
            viewModel.loginAnonymous()
        }
    }

    // Start entry animations
    LaunchedEffect(viewModel) {
        delay(100)
        isVisible = true
    }

    // Handle successful login/guest auth
    LaunchedEffect(uiState, onNavigateToHome) {
        if (uiState is LoginUiState.Success) {
            onNavigateToHome()
        }
    }

    val captchaState = uiState as? LoginUiState.RequiresCaptcha

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = captchaState,
            label = "welcome_to_captcha_transition"
        ) { currentCaptcha ->
            if (currentCaptcha != null) {
                CaptchaScreen(
                    webUrl = currentCaptcha.webUrl,
                    sessionId = currentCaptcha.sessionId,
                    isApiBypassEnabled = isApiBypassEnabled,
                    apiBypassStrategy = apiBypassStrategy,
                    onDismiss = { viewModel.resetError() },
                    onCaptchaSolve = { verifiedToken ->
                        viewModel.retryWithCaptcha(currentCaptcha, verifiedToken)
                    }
                )
            } else {
                if (isTablet) {
                    WelcomeTabletContent(
                        isVisible = isVisible,
                        uiState = uiState,
                        onLoginAnonymous = checkVpnAndLoginAnonymous,
                        onNavigateToRegister = onNavigateToRegister,
                        onNavigateToLogin = onNavigateToLogin,
                        onNavigateToApiBypassSettings = onNavigateToApiBypassSettings,
                        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy
                    )
                } else {
                    WelcomePhoneContent(
                        isVisible = isVisible,
                        uiState = uiState,
                        onLoginAnonymous = checkVpnAndLoginAnonymous,
                        onNavigateToRegister = onNavigateToRegister,
                        onNavigateToLogin = onNavigateToLogin,
                        onNavigateToApiBypassSettings = onNavigateToApiBypassSettings,
                        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy
                    )
                }
            }
        }

        // WARP Loading Overlay
        if (isWarpLoading) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colors.backgroundSecondary,
                    tonalElevation = 8.dp
                ) {
                    val colors = ProtonNextTheme.colors
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ExpressiveCircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = colors.brandNorm
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.warp_fetching_config),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textNorm,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePhoneContent(
    isVisible: Boolean,
    uiState: LoginUiState,
    onLoginAnonymous: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToApiBypassSettings: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundNorm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Background gradient
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Globe Image
            Image(
                painter = painterResource(id = R.drawable.vpn_welcome_globe),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.6f)
            )

            // Top Right Action Buttons
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp, end = 16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                ApiBypassButton(
                    isVisible = isVisible,
                    onClick = onNavigateToApiBypassSettings
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                    WelcomeButtons(
                        uiState = uiState,
                        onLoginAnonymous = onLoginAnonymous,
                        onNavigateToRegister = onNavigateToRegister,
                        onNavigateToLogin = onNavigateToLogin,
                        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeTabletContent(
    isVisible: Boolean,
    uiState: LoginUiState,
    onLoginAnonymous: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToApiBypassSettings: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundNorm)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Image(
                painter = painterResource(id = R.drawable.vpn_welcome_globe),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.8f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                ApiBypassButton(isVisible = isVisible, onClick = onNavigateToApiBypassSettings)
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800, delayMillis = 200)) + slideInVertically(tween(800, delayMillis = 200)) { it / 4 }
            ) {
                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800, delayMillis = 400)) + slideInVertically(tween(800, delayMillis = 400)) { it / 4 }
            ) {
                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textWeak
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800, delayMillis = 600)) + slideInVertically(tween(800, delayMillis = 600)) { it / 4 }
            ) {
                Box(modifier = Modifier.widthIn(max = 400.dp)) {
                    WelcomeButtons(
                        uiState = uiState,
                        onLoginAnonymous = onLoginAnonymous,
                        onNavigateToRegister = onNavigateToRegister,
                        onNavigateToLogin = onNavigateToLogin,
                        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WelcomeButtons(
    uiState: LoginUiState,
    onLoginAnonymous: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val isLoading = uiState is LoginUiState.Loading
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onLoginAnonymous,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm, contentColor = colors.textInverted)
        ) {
            if (isLoading) {
                ExpressiveCircularProgressIndicator(color = colors.textInverted, modifier = Modifier.size(24.dp))
            } else {
                Text(text = stringResource(R.string.btn_continue_guest), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onNavigateToLogin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textNorm),
            border = ButtonDefaults.outlinedButtonBorder(!isLoading).copy(brush = androidx.compose.ui.graphics.SolidColor(colors.separatorNorm))
        ) {
            Text(text = stringResource(R.string.btn_login), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        AgreementText(onClick = onNavigateToPrivacyPolicy)

        if (uiState is LoginUiState.Error) {
            Text(
                text = uiState.message,
                color = colors.notificationError,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AgreementText(onClick: () -> Unit) {
    val colors = ProtonNextTheme.colors
    val policy = stringResource(R.string.settings_privacy_policy)
    val disclaimer = stringResource(R.string.settings_disclaimer)
    
    val annotatedString = buildAnnotatedString {
        val fullText = stringResource(R.string.welcome_agreement_text, policy, disclaimer)
        val policyIndex = fullText.indexOf(policy)
        val disclaimerIndex = fullText.indexOf(disclaimer)

        append(fullText)

        if (policyIndex != -1) {
            addStyle(
                style = SpanStyle(color = colors.brandNorm, fontWeight = FontWeight.Bold),
                start = policyIndex,
                end = policyIndex + policy.length
            )
            addStringAnnotation(
                tag = "policy",
                annotation = "policy",
                start = policyIndex,
                end = policyIndex + policy.length
            )
        }

        if (disclaimerIndex != -1) {
            addStyle(
                style = SpanStyle(color = colors.brandNorm, fontWeight = FontWeight.Bold),
                start = disclaimerIndex,
                end = disclaimerIndex + disclaimer.length
            )
            addStringAnnotation(
                tag = "disclaimer",
                annotation = "disclaimer",
                start = disclaimerIndex,
                end = disclaimerIndex + disclaimer.length
            )
        }
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodySmall.copy(
            color = colors.textWeak,
            textAlign = TextAlign.Center
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "policy", start = offset, end = offset)
                .firstOrNull()?.let { onClick() }
            annotatedString.getStringAnnotations(tag = "disclaimer", start = offset, end = offset)
                .firstOrNull()?.let { onClick() }
        }
    )
}

@Composable
private fun ApiBypassButton(
    isVisible: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(800))
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.liquidGlass(shape = CircleShape, alpha = 0.4f, shadowElevation = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudSync,
                contentDescription = stringResource(R.string.settings_api_bypass),
                tint = colors.textNorm
            )
        }
    }
}
