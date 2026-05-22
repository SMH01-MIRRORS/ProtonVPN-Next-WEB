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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.ui.components.*
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.utils.ProtonLogger

enum class SetupStep {
    WELCOME,
    LOGIN_EMAIL,
    LOGIN_PASSWORD,
    LOGIN_2FA,
    LOADING, // "Please Wait" screen
    CONFIG_PORT,
    CONFIG_OBFUSCATION,
    CONFIG_SERVER_LOAD,
    CONFIG_THEME,
    COMPLETE
}

@Composable
fun WelcomeScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isWarpLoading by viewModel.isWarpLoading.collectAsStateWithLifecycle()
    val colors = ProtonNextTheme.colors

    val savedUsername by viewModel.username.collectAsStateWithLifecycle()

    LaunchedEffect(currentStep) {
        ProtonLogger.d("WelcomeScreen", "Current Step changed to: $currentStep")
    }

    LaunchedEffect(uiState) {
        ProtonLogger.d("WelcomeScreen", "UiState changed to: $uiState")
        when (uiState) {
            is LoginUiState.Loading -> {
                ProtonLogger.i("WelcomeScreen", "Transitioning to LOADING step")
                currentStep = SetupStep.LOADING
            }
            is LoginUiState.Success -> {
                currentStep = SetupStep.CONFIG_PORT
            }
            is LoginUiState.Requires2FA -> {
                currentStep = SetupStep.LOGIN_2FA
            }
            is LoginUiState.Error -> {
                // If we were loading, go back to appropriate login step on error
                if (currentStep == SetupStep.LOADING) {
                    currentStep = if (savedUsername.isBlank()) SetupStep.WELCOME else SetupStep.LOGIN_PASSWORD
                }
            }
            else -> {}
        }
    }

    BackHandler(currentStep != SetupStep.WELCOME && currentStep != SetupStep.LOADING) {
        currentStep = when (currentStep) {
            SetupStep.LOGIN_EMAIL -> SetupStep.WELCOME
            SetupStep.LOGIN_PASSWORD -> SetupStep.LOGIN_EMAIL
            SetupStep.LOGIN_2FA -> SetupStep.LOGIN_PASSWORD
            SetupStep.CONFIG_PORT -> SetupStep.WELCOME
            SetupStep.CONFIG_OBFUSCATION -> SetupStep.CONFIG_PORT
            SetupStep.CONFIG_SERVER_LOAD -> SetupStep.CONFIG_OBFUSCATION
            SetupStep.CONFIG_THEME -> SetupStep.CONFIG_SERVER_LOAD
            SetupStep.COMPLETE -> SetupStep.CONFIG_THEME
            else -> SetupStep.WELCOME
        }
        viewModel.resetError()
    }

    Box(modifier = modifier.fillMaxSize().background(colors.backgroundNorm)) {
        ExpressiveBackground()

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "setup_wizard",
            modifier = Modifier.fillMaxSize()
        ) { step ->
            when (step) {
                SetupStep.WELCOME -> StepWelcome(
                    onLogin = { currentStep = SetupStep.LOGIN_EMAIL },
                    onGuest = { viewModel.loginAnonymous() }
                )
                SetupStep.LOGIN_EMAIL -> StepLoginEmail(
                    initialEmail = savedUsername,
                    onNext = { email ->
                        viewModel.setUsername(email)
                        currentStep = SetupStep.LOGIN_PASSWORD
                    },
                    onBack = { currentStep = SetupStep.WELCOME }
                )
                SetupStep.LOGIN_PASSWORD -> StepLoginPassword(
                    email = savedUsername,
                    uiState = uiState,
                    onLogin = { pass -> viewModel.login(savedUsername, pass) },
                    onBack = { currentStep = SetupStep.LOGIN_EMAIL }
                )
                SetupStep.LOGIN_2FA -> StepLogin2FA(
                    uiState = uiState,
                    onVerify = { code ->
                        val state = uiState as? LoginUiState.Requires2FA
                        if (state != null) {
                            viewModel.submit2FA(state.sessionId, state.tempAccessToken, state.refreshToken, code)
                        }
                    },
                    onBack = { currentStep = SetupStep.LOGIN_PASSWORD }
                )
                SetupStep.LOADING -> SetupLoadingScreen(
                    message = stringResource(R.string.setup_please_wait)
                )
                SetupStep.CONFIG_PORT -> StepConfigPort(
                    onNext = { port ->
                        viewModel.setVpnPort(port)
                        currentStep = SetupStep.CONFIG_OBFUSCATION
                    },
                    onBack = { currentStep = SetupStep.WELCOME }
                )
                SetupStep.CONFIG_OBFUSCATION -> StepConfigObfuscation(
                    onNext = { enabled ->
                        viewModel.setObfuscationEnabled(enabled)
                        currentStep = SetupStep.CONFIG_SERVER_LOAD
                    },
                    onBack = { currentStep = SetupStep.CONFIG_PORT }
                )
                SetupStep.CONFIG_SERVER_LOAD -> StepConfigServerLoad(
                    onNext = { mode ->
                        viewModel.setServerLoadDisplayMode(mode)
                        currentStep = SetupStep.CONFIG_THEME
                    },
                    onBack = { currentStep = SetupStep.CONFIG_OBFUSCATION }
                )
                SetupStep.CONFIG_THEME -> StepConfigTheme(
                    onNext = { theme ->
                        viewModel.setAppTheme(theme)
                        currentStep = SetupStep.COMPLETE
                    },
                    onBack = { currentStep = SetupStep.CONFIG_SERVER_LOAD }
                )
                SetupStep.COMPLETE -> StepComplete(
                    onFinish = onNavigateToHome
                )
            }
        }

        if (isWarpLoading) {
            WarpLoadingOverlay()
        }
    }
}

@Composable
private fun StepWelcome(
    onLogin: () -> Unit,
    onGuest: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.vpn_welcome_globe),
            contentDescription = null,
            modifier = Modifier.size(180.dp)
        )
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colors.textNorm
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colors.textWeak
        )
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
        ) {
            Text(stringResource(R.string.btn_login), style = MaterialTheme.typography.labelLarge)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onGuest,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape
        ) {
            Text(stringResource(R.string.btn_continue_guest), color = colors.textNorm)
        }
    }
}

@Composable
private fun StepLoginEmail(
    initialEmail: String,
    onNext: (String) -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Person, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        SmoothOutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.hint_username)) },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Rounded.Mail, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { if (email.isNotBlank()) onNext(email) })
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(email) },
            nextEnabled = email.isNotBlank(),
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun StepLoginPassword(
    email: String,
    uiState: LoginUiState,
    onLogin: (String) -> Unit,
    onBack: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Person, null, tint = colors.brandNorm)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = email, style = MaterialTheme.typography.titleMedium, color = colors.textNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SmoothOutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.hint_password)) },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Rounded.Lock, null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotBlank()) onLogin(password) })
        )
        
        if (uiState is LoginUiState.Error) {
            Text(
                text = uiState.message,
                color = colors.notificationError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onLogin(password) },
            nextEnabled = password.isNotBlank(),
            nextText = stringResource(R.string.btn_login)
        )
    }
}

@Composable
private fun StepLogin2FA(
    uiState: LoginUiState,
    onVerify: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.title_2fa),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.msg_2fa_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SmoothOutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it },
            label = { Text(stringResource(R.string.hint_2fa_code)) },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onVerify(code) })
        )
        
        if (uiState is LoginUiState.Error) {
            Text(
                text = uiState.message,
                color = colors.notificationError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onVerify(code) },
            nextEnabled = code.length == 6,
            nextText = stringResource(R.string.btn_verify)
        )
    }
}


@Composable
private fun StepConfigPort(onNext: (Int) -> Unit, onBack: () -> Unit) {
    var selectedPort by remember { mutableIntStateOf(0) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Lan, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.setup_initial_config),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.setup_choose_connection),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        OptionCard(
            title = stringResource(R.string.settings_port_auto),
            subtitle = "Optimal performance",
            selected = selectedPort == 0,
            onClick = { selectedPort = 0 },
            icon = Icons.Rounded.AutoAwesome
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OptionCard(
            title = "Manual Port",
            subtitle = "Custom configuration",
            selected = selectedPort != 0,
            onClick = { selectedPort = 443 },
            icon = Icons.Rounded.SettingsInputComponent
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(selectedPort) },
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun StepConfigObfuscation(onNext: (Boolean) -> Unit, onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(true) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Security, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.obfuscation_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.setup_hide_traffic),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        OptionCard(
            title = stringResource(R.string.settings_on),
            subtitle = stringResource(R.string.obfuscation_enable_desc),
            selected = enabled,
            onClick = { enabled = true },
            icon = Icons.Rounded.VisibilityOff
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OptionCard(
            title = stringResource(R.string.settings_off),
            subtitle = "Standard connection",
            selected = !enabled,
            onClick = { enabled = false },
            icon = Icons.Rounded.Visibility
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(enabled) },
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun StepConfigServerLoad(onNext: (ServerLoadDisplayMode) -> Unit, onBack: () -> Unit) {
    var selectedMode by remember { mutableStateOf(ServerLoadDisplayMode.ALL) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ShowChart, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.settings_load_display_mode),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ServerLoadDisplayMode.entries.forEach { mode ->
                OptionCard(
                    title = when(mode) {
                        ServerLoadDisplayMode.ALL -> stringResource(R.string.load_mode_all)
                        ServerLoadDisplayMode.LINE -> stringResource(R.string.load_mode_line)
                        ServerLoadDisplayMode.PERCENT -> stringResource(R.string.load_mode_percent)
                        ServerLoadDisplayMode.HIDDEN -> stringResource(R.string.load_mode_hidden)
                    },
                    subtitle = "",
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    icon = when(mode) {
                        ServerLoadDisplayMode.ALL -> Icons.Rounded.BarChart
                        ServerLoadDisplayMode.LINE -> Icons.Rounded.HorizontalRule
                        ServerLoadDisplayMode.PERCENT -> Icons.Rounded.Percent
                        ServerLoadDisplayMode.HIDDEN -> Icons.Rounded.Block
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(selectedMode) },
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun StepConfigTheme(onNext: (AppTheme) -> Unit, onBack: () -> Unit) {
    var selectedTheme by remember { mutableStateOf(AppTheme.DARK) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.settings_app_theme),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(AppTheme.entries) { theme ->
                ThemeOptionCard(
                    theme = theme,
                    selected = selectedTheme == theme,
                    onClick = { selectedTheme = theme }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(selectedTheme) },
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun StepComplete(onFinish: () -> Unit) {
    val colors = ProtonNextTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()).background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(80.dp), tint = colors.brandNorm)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.setup_complete_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.setup_complete_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textWeak,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        ShowcaseCard(stringResource(R.string.setup_showcase_speed_title), stringResource(R.string.setup_showcase_speed_desc), Icons.Rounded.Speed)
        ShowcaseCard(stringResource(R.string.setup_showcase_privacy_title), stringResource(R.string.setup_showcase_privacy_desc), Icons.Rounded.Fingerprint)
        ShowcaseCard(stringResource(R.string.setup_showcase_security_title), stringResource(R.string.setup_showcase_security_desc), Icons.Rounded.Security)
        ShowcaseCard(stringResource(R.string.setup_showcase_bypass_title), stringResource(R.string.setup_showcase_bypass_desc), Icons.Rounded.Public)
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
        ) {
            Text(stringResource(R.string.setup_btn_finish))
        }
    }
}

@Composable
private fun WizardNavigation(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean = true,
    nextText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.desc_back), color = ProtonNextTheme.colors.textWeak)
        }
        
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            shape = CircleShape,
            modifier = Modifier.height(56.dp).widthIn(min = 120.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProtonNextTheme.colors.brandNorm)
        ) {
            Text(nextText)
        }
    }
}

@Composable
private fun OptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                alpha = if (selected) 0.15f else 0.05f,
                shadowElevation = 0.dp
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) colors.brandNorm else colors.iconWeak)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textNorm)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
            }
        }
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun ThemeOptionCard(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .liquidGlass(
                shape = RoundedCornerShape(20.dp),
                alpha = if (selected) 0.2f else 0.05f,
                shadowElevation = 0.dp
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) colors.brandNorm else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = colors.textNorm
        )
    }
}

@Composable
private fun ShowcaseCard(
    title: String,
    desc: String,
    icon: ImageVector
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                alpha = 0.05f,
                shadowElevation = 0.dp
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textNorm)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
        }
    }
}

@Composable
private fun WarpLoadingOverlay() {
    val colors = ProtonNextTheme.colors
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.backgroundSecondary,
            tonalElevation = 8.dp
        ) {
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
