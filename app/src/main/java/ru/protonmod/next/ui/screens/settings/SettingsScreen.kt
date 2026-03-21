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

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.LiquidGlassBottomBar
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToHome: (() -> Unit)? = null,
    onNavigateToCountries: (() -> Unit)? = null,
    onNavigateToProfiles: (() -> Unit)? = null,
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null,
    onNavigateToProtocol: (() -> Unit)? = null,
    onNavigateToKillSwitch: (() -> Unit)? = null,
    onNavigateToApiBypass: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToErrorReporting: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val currentTarget = MainTarget.Settings
    val isTablet = isTablet()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient decoration (immersive)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.2f),
                                colors.backgroundNorm
                            )
                        )
                    )
            )

            SettingsContent(
                state = uiState,
                isTablet = isTablet,
                onAutoConnectChange = viewModel::setAutoConnect,
                onNotificationsChange = viewModel::setNotifications,
                onPortChange = viewModel::setVpnPort,
                onCustomDnsChange = viewModel::setCustomDns,
                onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                onNavigateToProtocol = onNavigateToProtocol,
                onNavigateToKillSwitch = onNavigateToKillSwitch,
                onNavigateToApiBypass = onNavigateToApiBypass,
                onNavigateToAbout = onNavigateToAbout,
                onNavigateToErrorReporting = onNavigateToErrorReporting,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            )

            LiquidGlassBottomBar(
                selectedTarget = currentTarget,
                showCountries = true,
                showGateways = false,
                navigateTo = { target ->
                    when (target) {
                        MainTarget.Home -> onNavigateToHome?.invoke()
                        MainTarget.Countries -> onNavigateToCountries?.invoke()
                        MainTarget.Profiles -> onNavigateToProfiles?.invoke()
                        MainTarget.Settings -> { /* Already here */ }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    isTablet: Boolean = false,
    onAutoConnectChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onPortChange: (Int) -> Unit,
    onCustomDnsChange: (String) -> Unit,
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null,
    onNavigateToProtocol: (() -> Unit)? = null,
    onNavigateToKillSwitch: (() -> Unit)? = null,
    onNavigateToApiBypass: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToErrorReporting: (() -> Unit)? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors

    LazyColumn(
        modifier = modifier,
        horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = if (isTablet) 140.dp else 120.dp
        )
    ) {
        if (isTablet) {
            item {
                Row(
                    modifier = Modifier
                        .widthIn(max = 1000.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Left Column: Main Settings & Connection
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textNorm,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp)
                        )

                        FeatureCategory(
                            isTablet = true,
                            state = state,
                            onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                            onNavigateToProtocol = onNavigateToProtocol
                        )

                        ConnectionSettingsSection(
                            state = state,
                            onAutoConnectChange = onAutoConnectChange,
                            onNavigateToApiBypass = onNavigateToApiBypass,
                            onPortChange = onPortChange
                        )
                    }

                    // Right Column: Privacy, Notifications & About
                    Column(modifier = Modifier.weight(1f)) {
                        Spacer(modifier = Modifier.height(80.dp)) // Alignment offset

                        PrivacySettingsSection(
                            state = state,
                            onCustomDnsChange = onCustomDnsChange,
                            onNavigateToKillSwitch = onNavigateToKillSwitch,
                            onNavigateToErrorReporting = onNavigateToErrorReporting,
                            onNotificationsChange = onNotificationsChange
                        )

                        AboutSettingsSection(
                            onNavigateToAbout = onNavigateToAbout
                        )
                    }
                }
            }
        } else {
            // Phone Layout
            val contentModifier = Modifier.fillMaxWidth()

            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    modifier = contentModifier.padding(horizontal = 12.dp, vertical = 24.dp)
                )
            }

            item {
                FeatureCategory(
                    modifier = contentModifier,
                    isTablet = false,
                    state = state,
                    onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                    onNavigateToProtocol = onNavigateToProtocol
                )
            }

            item {
                ConnectionSettingsSection(
                    modifier = contentModifier,
                    state = state,
                    onAutoConnectChange = onAutoConnectChange,
                    onNavigateToApiBypass = onNavigateToApiBypass,
                    onPortChange = onPortChange
                )
            }

            item {
                PrivacySettingsSection(
                    modifier = contentModifier,
                    state = state,
                    onCustomDnsChange = onCustomDnsChange,
                    onNavigateToKillSwitch = onNavigateToKillSwitch,
                    onNavigateToErrorReporting = onNavigateToErrorReporting,
                    onNotificationsChange = onNotificationsChange
                )
            }

            item {
                AboutSettingsSection(
                    modifier = contentModifier,
                    onNavigateToAbout = onNavigateToAbout
                )
            }
        }
    }
}

@Composable
private fun ConnectionSettingsSection(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onAutoConnectChange: (Boolean) -> Unit,
    onNavigateToApiBypass: (() -> Unit)?,
    onPortChange: (Int) -> Unit
) {
    Category(modifier = modifier, title = stringResource(R.string.settings_connection)) {
        SettingToggleRow(
            icon = Icons.Rounded.Autorenew,
            title = stringResource(R.string.settings_auto_connect),
            subtitle = stringResource(R.string.settings_auto_connect_desc),
            checked = state.autoConnectEnabled,
            onCheckedChange = onAutoConnectChange
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.CloudSync,
            title = stringResource(R.string.settings_api_bypass),
            subtitle = if (state.apiBypassEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
            onClick = { onNavigateToApiBypass?.invoke() }
        )

        var showPortDialog by remember { mutableStateOf(false) }
        SettingRowWithIcon(
            icon = Icons.Rounded.Numbers,
            title = stringResource(R.string.settings_port),
            subtitle = if (state.vpnPort == 0) stringResource(R.string.settings_port_auto) else state.vpnPort.toString(),
            onClick = { showPortDialog = true }
        )
        if (showPortDialog) {
            PortSelectionDialog(
                currentPort = state.vpnPort,
                onDismiss = { showPortDialog = false },
                onPortSelected = {
                    onPortChange(it)
                    showPortDialog = false
                }
            )
        }
    }
}

@Composable
private fun PrivacySettingsSection(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onCustomDnsChange: (String) -> Unit,
    onNavigateToKillSwitch: (() -> Unit)?,
    onNavigateToErrorReporting: (() -> Unit)?,
    onNotificationsChange: (Boolean) -> Unit
) {
    Category(modifier = modifier, title = stringResource(R.string.settings_privacy)) {
        var showCustomDnsDialog by remember { mutableStateOf(false) }
        val currentDnsSubtitle = state.customDns.ifBlank {
            stringResource(R.string.settings_custom_dns_default)
        }

        SettingRowWithIcon(
            icon = Icons.Rounded.Dns,
            title = stringResource(R.string.settings_custom_dns),
            subtitle = currentDnsSubtitle,
            onClick = { showCustomDnsDialog = true }
        )

        if (showCustomDnsDialog) {
            CustomDnsDialog(
                currentDns = state.customDns,
                onDismiss = { showCustomDnsDialog = false },
                onDnsSaved = {
                    onCustomDnsChange(it)
                    showCustomDnsDialog = false
                }
            )
        }

        SettingRowWithIcon(
            icon = Icons.Rounded.GppMaybe,
            title = stringResource(R.string.settings_kill_switch),
            subtitle = stringResource(R.string.settings_kill_switch_desc),
            onClick = onNavigateToKillSwitch
        )

        SettingRowWithIcon(
            icon = Icons.Rounded.BugReport,
            title = stringResource(R.string.settings_error_reporting),
            subtitle = stringResource(R.string.settings_error_reporting_desc),
            onClick = onNavigateToErrorReporting
        )

        SettingToggleRow(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.settings_notifications),
            subtitle = stringResource(R.string.settings_notifications_desc),
            checked = state.notificationsEnabled,
            onCheckedChange = onNotificationsChange
        )
    }
}

@Composable
private fun AboutSettingsSection(
    modifier: Modifier = Modifier,
    onNavigateToAbout: (() -> Unit)?
) {
    Category(modifier = modifier, title = stringResource(R.string.settings_about)) {
        SettingRowWithIcon(
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            onClick = onNavigateToAbout
        )
    }
}

@Composable
fun CustomDnsDialog(
    currentDns: String,
    onDismiss: () -> Unit,
    onDnsSaved: (String) -> Unit
) {
    val colors = ProtonNextTheme.colors
    var inputText by remember { mutableStateOf(currentDns) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 24.dp, horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.settings_custom_dns_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textNorm,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.settings_custom_dns_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("e.g. 1.1.1.1 or 94.140.14.14") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.brandNorm,
                        unfocusedBorderColor = colors.shade60,
                        focusedTextColor = colors.textNorm,
                        unfocusedTextColor = colors.textNorm
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onDnsSaved("") }) {
                        Text(stringResource(R.string.settings_custom_dns_reset), color = colors.textWeak)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onDnsSaved(inputText.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
                    ) {
                        Text(stringResource(R.string.btn_save), color = colors.textInverted)
                    }
                }
            }
        }
    }
}

@Composable
fun PortSelectionDialog(
    currentPort: Int,
    onDismiss: () -> Unit,
    onPortSelected: (Int) -> Unit
) {
    val colors = ProtonNextTheme.colors
    val portOptions = listOf(0, 443, 123, 1194, 51820)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundSecondary)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.settings_port),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textNorm,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(portOptions) { port ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPortSelected(port) }
                                .padding(vertical = 12.dp, horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (port == 0) stringResource(R.string.settings_port_auto) else port.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textNorm,
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(
                                selected = (port == currentPort),
                                onClick = { onPortSelected(port) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colors.brandNorm,
                                    unselectedColor = colors.shade60
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp)
                ) {
                    Text(stringResource(id = android.R.string.cancel), color = colors.brandNorm)
                }
            }
        }
    }
}

@Composable
private fun FeatureCategory(
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
    state: SettingsUiState,
    onNavigateToSplitTunnelingMain: (() -> Unit)?,
    onNavigateToProtocol: (() -> Unit)?
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = if (isTablet) Arrangement.Start else Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tileModifier = if (isTablet) Modifier.size(160.dp) else Modifier.weight(1f)

        // Split Tunneling Tile
        FeatureTile(
            modifier = tileModifier,
            title = stringResource(id = R.string.settings_split_tunneling),
            subtitle = if (state.splitTunnelingEnabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
            icon = Icons.AutoMirrored.Rounded.AltRoute,
            isActive = state.splitTunnelingEnabled,
            onClick = { onNavigateToSplitTunnelingMain?.invoke() }
        )

        if (isTablet) Spacer(modifier = Modifier.width(16.dp))

        // Protocol Tile
        FeatureTile(
            modifier = tileModifier,
            title = stringResource(id = R.string.settings_protocol),
            subtitle = "AmneziaWG",
            icon = Icons.Rounded.Security,
            isActive = true,
            onClick = { onNavigateToProtocol?.invoke() }
        )
    }
}

@Composable
fun FeatureTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundSecondary.copy(alpha = 0.8f)
        ),
        modifier = modifier.aspectRatio(1f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) colors.brandNorm.copy(alpha = 0.15f)
                            else colors.backgroundSecondary.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) colors.brandNorm else colors.iconWeak,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textNorm
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
