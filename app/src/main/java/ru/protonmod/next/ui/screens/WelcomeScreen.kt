/*
 * Copyright (c) 2024 Proton Technologies AG
 * This file is part of Proton AG and ProtonCore.
 */

package ru.protonmod.next.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.delay
import ru.protonmod.next.R
import ru.protonmod.next.data.network.byedpi.ByeDpiStrategyTester
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.ExpressiveLinearProgressIndicator
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

enum class SetupStep {
    WELCOME,
    LOGIN,
    TROUBLESHOOT,
    BYEDPI_TESTER
}

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
    val tester = viewModel.byeDpiStrategyTester
    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isWarpLoading by viewModel.isWarpLoading.collectAsStateWithLifecycle()
    val colors = ProtonNextTheme.colors

    // Handle successful login
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onNavigateToHome()
        }
    }

    // Handle back navigation within wizard
    BackHandler(currentStep != SetupStep.WELCOME) {
        currentStep = when (currentStep) {
            SetupStep.LOGIN -> SetupStep.WELCOME
            SetupStep.TROUBLESHOOT -> SetupStep.LOGIN
            SetupStep.BYEDPI_TESTER -> SetupStep.TROUBLESHOOT
            else -> SetupStep.WELCOME
        }
        viewModel.resetError()
    }

    Box(modifier = modifier.fillMaxSize().background(colors.backgroundNorm)) {
        // Animated background
        FluidBackground()

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "setup_wizard_steps"
        ) { step ->
            when (step) {
                SetupStep.WELCOME -> StepWelcome(
                    onGetStarted = { currentStep = SetupStep.LOGIN },
                    onGuest = { viewModel.loginAnonymous() },
                    isLoading = uiState is LoginUiState.Loading
                )
                SetupStep.LOGIN -> StepLogin(
                    viewModel = viewModel,
                    onBack = { currentStep = SetupStep.WELCOME },
                    onError = { currentStep = SetupStep.TROUBLESHOOT }
                )
                SetupStep.TROUBLESHOOT -> StepTroubleshoot(
                    onNext = { strategy ->
                        when (strategy) {
                            "byedpi" -> currentStep = SetupStep.BYEDPI_TESTER
                            "warp" -> {
                                viewModel.enableWarpBypass()
                                currentStep = SetupStep.LOGIN
                            }
                            else -> {
                                viewModel.disableBypass()
                                currentStep = SetupStep.LOGIN
                            }
                        }
                    },
                    onSkip = { 
                        viewModel.disableBypass()
                        currentStep = SetupStep.LOGIN 
                    }
                )
                SetupStep.BYEDPI_TESTER -> StepByeDpiTester(
                    tester = tester,
                    onFinished = { currentStep = SetupStep.LOGIN }
                )
            }
        }

        if (isWarpLoading) {
            WarpLoadingOverlay()
        }
    }
}

@Composable
private fun FluidBackground() {
    val colors = ProtonNextTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_bg")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize().alpha(0.3f)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.brandNorm, Color.Transparent),
                center = Offset(
                    centerX + (size.width * 0.3f * kotlin.math.sin(phase * 2 * Math.PI.toFloat())),
                    centerY + (size.height * 0.2f * kotlin.math.cos(phase * 2 * Math.PI.toFloat()))
                ),
                radius = size.minDimension * 0.8f
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.brandLighten20, Color.Transparent),
                center = Offset(
                    centerX - (size.width * 0.4f * kotlin.math.cos(phase * 2 * Math.PI.toFloat())),
                    centerY - (size.height * 0.3f * kotlin.math.sin(phase * 2 * Math.PI.toFloat()))
                ),
                radius = size.minDimension * 0.7f
            )
        )
    }
}

@Composable
private fun StepWelcome(
    onGetStarted: () -> Unit,
    onGuest: () -> Unit,
    isLoading: Boolean
) {
    val colors = ProtonNextTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.vpn_welcome_globe),
            contentDescription = null,
            modifier = Modifier.size(200.dp).graphicsLayer {
                rotationY = 15f
            }
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
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
        ) {
            Text(stringResource(R.string.welcome_get_started), style = MaterialTheme.typography.labelLarge)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onGuest, enabled = !isLoading) {
            if (isLoading) {
                ExpressiveCircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.brandNorm)
            } else {
                Text(stringResource(R.string.btn_continue_guest), color = colors.textNorm)
            }
        }
    }
}

@Composable
private fun StepLogin(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
    onError: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ProtonNextTheme.colors
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState, onError) {
        if (uiState is LoginUiState.Error) {
            val error = (uiState as LoginUiState.Error).message
            if (error.contains("timeout", ignoreCase = true) || error.contains("connect", ignoreCase = true)) {
                onError()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = colors.textNorm)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = colors.brandNorm
            )
            if (uiState is LoginUiState.Loading) {
                ExpressiveCircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.brandNorm
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )

        Spacer(modifier = Modifier.height(48.dp))

        SmoothOutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.hint_username)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Rounded.Mail, null) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SmoothOutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.hint_password)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Rounded.Lock, null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.login(username, password) })
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.login(username, password) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            enabled = uiState !is LoginUiState.Loading && username.isNotBlank() && password.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
        ) {
            Text(stringResource(R.string.btn_login), style = MaterialTheme.typography.labelLarge)
        }

        if (uiState is LoginUiState.Error) {
            Text(
                text = (uiState as LoginUiState.Error).message,
                color = colors.notificationError,
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StepTroubleshoot(
    onNext: (String) -> Unit,
    onSkip: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    var selectedStrategy by remember { mutableStateOf("warp") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = colors.notificationError
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.troubleshoot_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.troubleshoot_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = colors.textWeak
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.selectableGroup()) {
            TroubleshootOption(
                title = stringResource(R.string.troubleshoot_strategy_warp),
                selected = selectedStrategy == "warp",
                onClick = { selectedStrategy = "warp" }
            )
            TroubleshootOption(
                title = stringResource(R.string.troubleshoot_strategy_byedpi),
                selected = selectedStrategy == "byedpi",
                onClick = { selectedStrategy = "byedpi" }
            )
            TroubleshootOption(
                title = stringResource(R.string.troubleshoot_strategy_none),
                selected = selectedStrategy == "none",
                onClick = { selectedStrategy = "none" }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(stringResource(R.string.troubleshoot_btn_skip))
            }
            Button(
                onClick = { onNext(selectedStrategy) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
            ) {
                Text(stringResource(R.string.troubleshoot_btn_next))
            }
        }
    }
}

@Composable
private fun TroubleshootOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                alpha = if (selected) 0.15f else 0.05f,
                shadowElevation = 0.dp
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = colors.textNorm)
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun StepByeDpiTester(
    tester: ByeDpiStrategyTester,
    onFinished: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val isTesting by tester.isTesting.collectAsStateWithLifecycle()
    val progress by tester.progress.collectAsStateWithLifecycle()
    val currentStep by tester.currentStep.collectAsStateWithLifecycle()
    val totalSteps by tester.totalSteps.collectAsStateWithLifecycle()
    
    var selectedMode by remember { mutableStateOf("fast") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.byedpi_tester_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.byedpi_tester_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = colors.textWeak
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isTesting) {
            Column(modifier = Modifier.selectableGroup()) {
                TroubleshootOption(
                    title = stringResource(R.string.byedpi_mode_fast),
                    selected = selectedMode == "fast",
                    onClick = { selectedMode = "fast" }
                )
                TroubleshootOption(
                    title = stringResource(R.string.byedpi_mode_medium),
                    selected = selectedMode == "medium",
                    onClick = { selectedMode = "medium" }
                )
                TroubleshootOption(
                    title = stringResource(R.string.byedpi_mode_full),
                    selected = selectedMode == "full",
                    onClick = { selectedMode = "full" }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    tester.startTesting(selectedMode, listOf("google.com"))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
            ) {
                Text(stringResource(R.string.btn_start_test))
            }
        } else {
            Spacer(modifier = Modifier.height(64.dp))
            ExpressiveCircularProgressIndicator(modifier = Modifier.size(80.dp), color = colors.brandNorm)
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.byedpi_tester_running, currentStep, totalSteps),
                color = colors.textNorm
            )
            Spacer(modifier = Modifier.height(16.dp))
            ExpressiveLinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { tester.stopTesting() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(stringResource(R.string.btn_stop_test))
            }
        }
    }

    LaunchedEffect(isTesting, onFinished) {
        if (!isTesting && currentStep > 0) {
            delay(1000)
            onFinished()
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
