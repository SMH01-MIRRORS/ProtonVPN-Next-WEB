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

package ru.protonmod.next.ui.screens.dashboard

import android.app.Activity
import android.net.VpnService
import android.text.BidiFormatter
import ru.protonmod.next.utils.ProtonLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.components.FlagIcon
import ru.protonmod.next.ui.theme.ProtonColors
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.CountryUtils
import ru.protonmod.next.ui.utils.isTablet
import ru.protonmod.next.vpn.AmneziaVpnManager

// --- Extensions for UI Effects matching Original Proton ---

fun Modifier.vpnStatusOverlayBackground(
    isConnected: Boolean,
    isConnecting: Boolean,
    colors: ProtonColors
): Modifier = composed {
    val targetColor = when {
        isConnected -> colors.notificationSuccess.copy(alpha = 0.4f)
        isConnecting -> Color.White.copy(alpha = 0.4f)
        else -> colors.notificationError.copy(alpha = 0.4f)
    }

    val gradientColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500),
        label = "Gradient Animation"
    )

    background(
        Brush.verticalGradient(
            colors = listOf(gradientColor, gradientColor.copy(alpha = 0.0F))
        )
    )
}

@Composable
fun VpnStatusTop(
    isConnected: Boolean,
    isConnecting: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = Pair(isConnected, isConnecting),
            label = "VpnStatusTopTransition"
        ) { (connected, connecting) ->
            when {
                connected -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_proton_lock_filled),
                            tint = colors.notificationSuccess,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.status_connected),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.notificationSuccess,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                connecting -> {
                    CircularProgressIndicator(
                        color = colors.iconNorm,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
                else -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_proton_lock_open_filled_2),
                        contentDescription = null,
                        tint = colors.notificationError,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

// --- Masked Location Text Components ---

@Composable
private fun annotatedCountryHighlight(
    text: String,
    highlight: String,
    displayText: String = text,
) = buildAnnotatedString {
    append(displayText)
    val startIndex = text.indexOf(highlight)
    if (startIndex >= 0) {
        val styleStart = startIndex.coerceAtMost(displayText.length)
        val styleEnd = (startIndex + highlight.length).coerceAtMost(displayText.length)
        if (styleStart < styleEnd) {
            addStyle(
                style = SpanStyle(color = ProtonNextTheme.colors.textNorm, fontWeight = FontWeight.SemiBold),
                start = styleStart,
                end = styleEnd
            )
        }
    }
}

/**
 * Text that can beautifully obscure its contents with a character-by-character animation.
 * Replaces chars with '*' while keeping spaces and dots intact.
 */
@Composable
private fun ObscurableText(
    modifier: Modifier = Modifier,
    targetText: String,
    highlightText: String,
    isObscured: Boolean,
    duration: Int = 30, // animation speed per character
    targetCharacter: Char = '*',
    preserveCharacters: CharArray = charArrayOf('.', ' ', '-', ':')
) {
    val obscureStartIndex = 0

    // Remove targetText from remember keys so we don't instantly throw away the animation state
    var displayText by remember {
        mutableStateOf(
            if (isObscured) {
                val chars = targetText.toCharArray()
                for (i in obscureStartIndex until chars.size) {
                    if (!preserveCharacters.contains(chars[i])) chars[i] = targetCharacter
                }
                String(chars)
            } else {
                targetText
            }
        )
    }
    var fixedWidth by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(isObscured, targetText) {
        val targetChars = targetText.toCharArray()
        var currentChars = displayText.toCharArray()

        val currentObscureStart = 0

        // Check if the base string structure changed (length difference or country name change)
        var baseChanged = currentChars.size != targetChars.size
        if (!baseChanged) {
            for (i in 0 until currentObscureStart) {
                if (currentChars[i] != targetChars[i]) {
                    baseChanged = true
                    break
                }
            }
        }

        if (baseChanged) {
            // Reset fixed width to allow the layout to remeasure for the new string
            fixedWidth = null

            val baseChars = targetChars.clone()
            if (isObscured) {
                // If the new string is obscured, jump to its obscured form immediately
                for (i in currentObscureStart until baseChars.size) {
                    if (!preserveCharacters.contains(baseChars[i])) baseChars[i] = targetCharacter
                }
                displayText = String(baseChars)
                return@LaunchedEffect
            } else {
                // If we are unobscuring to a NEW target, start from its obscured version
                // and let the animation below reveal the new characters.
                for (i in currentObscureStart until baseChars.size) {
                    if (!preserveCharacters.contains(baseChars[i])) baseChars[i] = targetCharacter
                }
                currentChars = baseChars
                displayText = String(currentChars)
            }
        }

        // Animate the differences character by character
        val indicesToAnimate = (currentObscureStart until targetText.length)
            .filter { !preserveCharacters.contains(targetText[it]) }
            .filter {
                if (isObscured) currentChars[it] != targetCharacter
                else currentChars[it] != targetChars[it]
            }
            .shuffled()

        for (i in indicesToAnimate) {
            delay(duration.toLong())
            val newChar = if (isObscured) targetCharacter else targetChars[i]
            currentChars[i] = newChar
            displayText = String(currentChars)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Layout(
            content = {
                Text(
                    text = annotatedCountryHighlight(
                        text = targetText,
                        highlight = highlightText,
                        displayText = displayText
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProtonNextTheme.colors.textWeak,
                    modifier = Modifier.onGloballyPositioned {
                        // Prevent layout jumping while animating asterisks
                        if (fixedWidth == null || fixedWidth!! < it.size.width) {
                            fixedWidth = it.size.width
                        }
                    },
                )
            },
            modifier = modifier,
            measurePolicy = { measurables, constraints ->
                val placeable = measurables.first().measure(constraints)
                val width = fixedWidth ?: placeable.width
                val offsetX = (width - placeable.width) / 2
                layout(width, placeable.height) {
                    placeable.placeRelative(offsetX, 0)
                }
            }
        )
    }
}

@Composable
private fun LocationTextElement(
    locationText: LocationText,
    isObscured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    Surface(
        color = colors.backgroundSecondary.copy(alpha = 0.86F),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(listOf(colors.shade100.copy(alpha = 0.08f), colors.shade100.copy(alpha = 0.02f)))
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick) // Makes the entire IP block clickable to toggle privacy mode
    ) {
        // Safe fallbacks to absolutely guarantee no null strings
        val safeCountry = if (locationText.country.isBlank() || locationText.country.equals("null", ignoreCase = true)) {
            stringResource(R.string.status_not_connected)
        } else locationText.country
        
        val safeIp = if (locationText.ip.isBlank() || locationText.ip.equals("null", ignoreCase = true)) {
            "0.0.0.0"
        } else locationText.ip

        val country = BidiFormatter.getInstance().unicodeWrap(safeCountry)
        val fullText = "$country • $safeIp"

        ObscurableText(
            targetText = fullText,
            highlightText = country,
            isObscured = isObscured,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// --- Main Screen ---

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingServer by remember { mutableStateOf<LogicalServer?>(null) }
    var isQuickConnectPending by remember { mutableStateOf(false) }
    val isTablet = isTablet()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ProtonLogger.d("DashboardScreen", "VPN permission granted")
            if (isQuickConnectPending) {
                viewModel.quickConnect()
                isQuickConnectPending = false
            } else {
                pendingServer?.let {
                    viewModel.toggleConnection(it)
                    pendingServer = null
                }
            }
        } else {
            pendingServer = null
            isQuickConnectPending = false
        }
    }

    val errorAppOpsMsg = stringResource(R.string.error_system_appops)

    val checkVpnAndConnect: (LogicalServer) -> Unit = { server ->
        try {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                pendingServer = server
                vpnPermissionLauncher.launch(intent)
            } else {
                viewModel.toggleConnection(server)
            }
        } catch (_: SecurityException) {
            android.widget.Toast.makeText(context, errorAppOpsMsg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.toggleConnection(server)
        }
    }

    val checkVpnAndQuickConnect: () -> Unit = {
        try {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                isQuickConnectPending = true
                vpnPermissionLauncher.launch(intent)
            } else {
                viewModel.quickConnect()
            }
        } catch (_: SecurityException) {
            android.widget.Toast.makeText(context, errorAppOpsMsg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.quickConnect()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colors.backgroundNorm,
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val successState = uiState as? DashboardUiState.Success
            val isConnected = successState?.isConnected == true
            val isConnecting = successState?.isConnecting == true

            // Background gradient decoration (immersive)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.25f),
                                colors.backgroundNorm.copy(alpha = 0.1f),
                                colors.backgroundNorm
                            )
                        )
                    )
            )

            HomeMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isTablet) 1f else 0.6f),
                allServers = successState?.servers ?: emptyList(),
                connectedServer = successState?.connectedServer,
                userCountryCode = successState?.originalLocationText?.countryCode,
                isConnecting = isConnecting,
                isInteractive = isTablet
            )

            if (!isTablet) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .align(Alignment.TopCenter)
                        .vpnStatusOverlayBackground(isConnected, isConnecting, colors)
                )
            }

            VpnStatusTop(
                isConnected = isConnected,
                isConnecting = isConnecting,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 16.dp)
            )

            val baseState = when (uiState) {
                is DashboardUiState.Loading -> 0
                is DashboardUiState.Error -> 1
                is DashboardUiState.Success -> 2
            }

            AnimatedContent(
                targetState = baseState,
                label = "dashboard_state",
                modifier = Modifier.fillMaxSize()
            ) { target ->
                when (target) {
                    0 -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.brandNorm)
                        }
                    }
                    1 -> {
                        val errorState = uiState as? DashboardUiState.Error
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(errorState?.message.orEmpty(), color = colors.notificationError)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.loadServers() },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.interactionNorm)
                                ) {
                                    Text(stringResource(R.string.btn_retry), color = colors.textInverted)
                                }
                            }
                        }
                    }
                    2 -> {
                        val state = uiState as? DashboardUiState.Success
                        if (state != null) {
                            DashboardContent(
                                state = state,
                                isTablet = isTablet,
                                onServerClick = { server -> checkVpnAndConnect(server) },
                                onQuickConnect = { checkVpnAndQuickConnect() },
                                onDisconnect = { viewModel.disconnect() },
                                onRefreshCert = { viewModel.refreshCertificate() },
                                onToggleIpVisibility = { viewModel.toggleIpVisibility() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    state: DashboardUiState.Success,
    isTablet: Boolean = false,
    onServerClick: (LogicalServer) -> Unit,
    onQuickConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshCert: () -> Unit,
    onToggleIpVisibility: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    if (isTablet) {
        // Tablet Layout: Split connection (Left) and recent connections (Right)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(bottom = 140.dp), // Increased for the centered bottom bar
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Left Side: Connection Status
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                CertificateBanner(
                    state = state.certificateState,
                    onRefresh = onRefreshCert,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ConnectionStatusCard(
                    isConnected = state.isConnected,
                    isConnecting = state.isConnecting,
                    connectedServer = state.connectedServer,
                    originalLocationText = state.originalLocationText,
                    vpnLocationText = state.vpnLocationText,
                    isIpHidden = state.isIpHidden,
                    onToggleIpVisibility = onToggleIpVisibility,
                    onToggleConnection = {
                        if (state.isConnected) onDisconnect() else onQuickConnect()
                    }
                )
            }

            // Right Side: Recent Connections
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                if (state.recentConnections.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.title_recent_connections),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.backgroundNorm.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.recentConnections) { server ->
                            ServerCard(
                                server = server,
                                isConnected = state.connectedServer?.id == server.id,
                                isConnecting = state.isConnecting && state.connectedServer?.id == server.id,
                                onClick = { onServerClick(server) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Phone Layout (original LazyColumn)
        val topSpacerHeight = (screenHeight * 0.55f).coerceAtLeast(400.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(topSpacerHeight))
            }

            item {
                CertificateBanner(
                    state = state.certificateState,
                    onRefresh = onRefreshCert,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                ConnectionStatusCard(
                    isConnected = state.isConnected,
                    isConnecting = state.isConnecting,
                    connectedServer = state.connectedServer,
                    originalLocationText = state.originalLocationText,
                    vpnLocationText = state.vpnLocationText,
                    isIpHidden = state.isIpHidden,
                    onToggleIpVisibility = onToggleIpVisibility,
                    onToggleConnection = {
                        if (state.isConnected) onDisconnect() else onQuickConnect()
                    }
                )
            }

            if (state.recentConnections.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, colors.backgroundNorm)
                                )
                            )
                            .padding(top = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.title_recent_connections),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.textNorm,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }

                items(state.recentConnections) { server ->
                    Box(modifier = Modifier.background(colors.backgroundNorm)) {
                        ServerCard(
                            server = server,
                            isConnected = state.connectedServer?.id == server.id,
                            isConnecting = state.isConnecting && state.connectedServer?.id == server.id,
                            onClick = { onServerClick(server) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CertificateBanner(
    state: AmneziaVpnManager.CertificateState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state == AmneziaVpnManager.CertificateState.Valid) return

    val colors = ProtonNextTheme.colors
    val (backgroundColor, contentColor, icon, message) = when (state) {
        is AmneziaVpnManager.CertificateState.ExpiringSoon -> Quadruple(
            colors.notificationWarning.copy(alpha = 0.1f),
            colors.notificationWarning,
            Icons.Rounded.Warning,
            stringResource(R.string.cert_msg_expiring_soon, state.hoursRemaining)
        )
        is AmneziaVpnManager.CertificateState.Expired -> Quadruple(
            colors.notificationError.copy(alpha = 0.1f),
            colors.notificationError,
            Icons.Default.ErrorOutline,
            stringResource(R.string.cert_msg_expired)
        )
        is AmneziaVpnManager.CertificateState.Refreshing -> Quadruple(
            colors.backgroundSecondary,
            colors.textNorm,
            Icons.Default.Refresh,
            stringResource(R.string.cert_msg_refreshing)
        )
        is AmneziaVpnManager.CertificateState.RefreshFailed -> {
            val msg = if (state.isFullyExpired) {
                stringResource(R.string.cert_msg_refresh_failed, state.error)
            } else {
                stringResource(R.string.cert_msg_auto_refresh_failed)
            }
            Quadruple(
                colors.notificationError.copy(alpha = 0.1f),
                colors.notificationError,
                Icons.Default.ErrorOutline,
                msg
            )
        }
    }

    val isRefreshing = state is AmneziaVpnManager.CertificateState.Refreshing
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_anim")
    val rotation by if (isRefreshing) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
            }
            if (state is AmneziaVpnManager.CertificateState.Expired || state is AmneziaVpnManager.CertificateState.RefreshFailed) {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.cert_btn_refresh_now), color = contentColor)
                }
            } else if (isRefreshing) {
                Text(
                    text = stringResource(R.string.cert_msg_refreshing),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    connectedServer: LogicalServer?,
    originalLocationText: LocationText?,
    vpnLocationText: LocationText?,
    isIpHidden: Boolean,
    onToggleIpVisibility: () -> Unit,
    onToggleConnection: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current

    val contentColor = colors.textNorm

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .liquidGlass(
                shape = RoundedCornerShape(32.dp),
                alpha = if (isConnected) 0.2f else 0.4f,
                shadowElevation = 0.dp
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        isConnected -> stringResource(R.string.status_connected)
                        isConnecting -> stringResource(R.string.status_connecting)
                        else -> stringResource(R.string.status_not_connected)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isConnected) colors.notificationSuccess else contentColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )

                // Manage the intermediate state when the VPN is UP, but the real IP hasn't been fetched yet
                val isFetchingVpnIp = isConnected && vpnLocationText == null

                val currentLocation = when {
                    isConnected && vpnLocationText != null -> vpnLocationText
                    isConnected && vpnLocationText == null -> {
                        // Provide a dummy IP string while waiting for the real one.
                        val rawCountry = connectedServer?.exitCountry?.let { CountryUtils.getCountryName(context, it) }
                        val safeCountry = rawCountry?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: "VPN"
                        LocationText(country = safeCountry, countryCode = connectedServer?.exitCountry, ip = "0.0.0.0")
                    }
                    else -> originalLocationText ?: LocationText(country = stringResource(R.string.status_connecting), ip = "0.0.0.0")
                }

                Spacer(modifier = Modifier.width(12.dp))
                LocationTextElement(
                    locationText = currentLocation,
                    // Force obscuring when connecting, hiding IP manually, waiting for VPN IP, or waiting for Original IP
                    isObscured = isIpHidden || isConnecting || isFetchingVpnIp || originalLocationText == null,
                    onClick = onToggleIpVisibility
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !isConnecting) { /* TODO: Open Change Server Bottom Sheet */ }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConnected || isConnecting) {
                    val countryCode = connectedServer?.exitCountry
                    val flagResId = CountryUtils.getFlagResource(context, countryCode)
                    if (flagResId != 0) {
                        FlagIcon(
                            countryFlag = flagResId,
                            size = DpSize(48.dp, 32.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp, 32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.backgroundNorm),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = stringResource(R.string.desc_country),
                                tint = colors.iconNorm,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    FlagIcon(
                        countryFlag = R.drawable.flag_fastest,
                        size = DpSize(48.dp, 32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val rawCountry = connectedServer?.let { CountryUtils.getCountryName(context, it.exitCountry) }
                    val safeCountryName = rawCountry?.takeIf { it.isNotBlank() && it != "null" } ?: "VPN"
                    val safeCityName = connectedServer?.city?.takeIf { it.isNotBlank() && it != "null" } ?: ""

                    val locationTitleText = if (isConnected || isConnecting) {
                        if (safeCityName.isNotEmpty()) "$safeCountryName, $safeCityName" else safeCountryName
                    } else {
                        stringResource(R.string.label_fastest_server)
                    }

                    Text(
                        text = locationTitleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isConnected || isConnecting) {
                            connectedServer?.name ?: ""
                        } else stringResource(R.string.label_select_location),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.desc_change_server),
                    tint = colors.iconWeak.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onToggleConnection,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) colors.shade20 else colors.brandNorm,
                    contentColor = if (isConnected) colors.textNorm else colors.textInverted
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (isConnected) 0.dp else 4.dp,
                    pressedElevation = 2.dp
                ),
                enabled = !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colors.textInverted,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isConnected) stringResource(R.string.btn_disconnect) else stringResource(R.string.btn_quick_connect),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ServerCard(
    server: LogicalServer,
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                alpha = if (isConnected) 0.3f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(enabled = !isConnecting) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp, 24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.brandNorm
                    )
                } else {
                    val flagResId = CountryUtils.getFlagResource(context, server.exitCountry)
                    if (flagResId != 0) {
                        FlagIcon(
                            countryFlag = flagResId,
                            size = DpSize(36.dp, 24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.backgroundNorm),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = stringResource(R.string.desc_country),
                                tint = colors.iconNorm,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                val rawCountry = CountryUtils.getCountryName(context, server.exitCountry)
                val safeCountry = rawCountry.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: "VPN"
                val safeCity = server.city.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: ""
                val locationTitle = if (safeCity.isNotEmpty()) "$safeCountry, $safeCity" else safeCountry

                Text(
                    text = locationTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textNorm
                )
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak
                )
            }
        }
    }
}
