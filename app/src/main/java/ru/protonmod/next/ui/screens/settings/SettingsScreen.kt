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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.MainHeader
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null,
    onNavigateToProtocol: (() -> Unit)? = null,
    onNavigateToKillSwitch: (() -> Unit)? = null,
    onNavigateToApiBypass: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToErrorReporting: (() -> Unit)? = null,
    onNavigateToThemeSelection: (() -> Unit)? = null,
    onNavigateToLoadDisplayMode: (() -> Unit)? = null,
    onNavigateToDebug: (() -> Unit)? = null,
    onNavigateToCustomDns: (() -> Unit)? = null,
    onNavigateToPortSelection: ((Int) -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
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

            SettingsContent(
                state = uiState,
                isTablet = isTablet,
                onAutoConnectChange = viewModel::setAutoConnect,
                onNotificationsChange = viewModel::setNotifications,
                onLogout = viewModel::logout,
                onNavigateToSplitTunnelingMain = onNavigateToSplitTunnelingMain,
                onNavigateToProtocol = onNavigateToProtocol,
                onNavigateToKillSwitch = onNavigateToKillSwitch,
                onNavigateToApiBypass = onNavigateToApiBypass,
                onNavigateToAbout = onNavigateToAbout,
                onNavigateToErrorReporting = onNavigateToErrorReporting,
                onNavigateToThemeSelection = onNavigateToThemeSelection,
                onNavigateToLoadDisplayMode = onNavigateToLoadDisplayMode,
                onNavigateToDebug = onNavigateToDebug,
                onNavigateToCustomDns = onNavigateToCustomDns,
                onNavigateToPortSelection = onNavigateToPortSelection,
                modifier = Modifier.fillMaxSize()
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
    onLogout: () -> Unit,
    onNavigateToSplitTunnelingMain: (() -> Unit)? = null,
    onNavigateToProtocol: (() -> Unit)? = null,
    onNavigateToKillSwitch: (() -> Unit)? = null,
    onNavigateToApiBypass: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToErrorReporting: (() -> Unit)? = null,
    onNavigateToThemeSelection: (() -> Unit)? = null,
    onNavigateToLoadDisplayMode: (() -> Unit)? = null,
    onNavigateToDebug: (() -> Unit)? = null,
    onNavigateToCustomDns: (() -> Unit)? = null,
    onNavigateToPortSelection: ((Int) -> Unit)? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 0.dp,
            bottom = if (isTablet) 140.dp else 120.dp
        )
    ) {
        item {
            MainHeader(title = stringResource(R.string.settings_title))
        }

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
                            onNavigateToPortSelection = onNavigateToPortSelection
                        )

                        CustomizationSettingsSection(
                            state = state,
                            onNavigateToThemeSelection = onNavigateToThemeSelection,
                            onNavigateToLoadDisplayMode = onNavigateToLoadDisplayMode
                        )
                    }

                    // Right Column: Privacy, Notifications & About
                    Column(modifier = Modifier.weight(1f)) {
                        PrivacySettingsSection(
                            state = state,
                            onNavigateToCustomDns = onNavigateToCustomDns,
                            onNavigateToKillSwitch = onNavigateToKillSwitch,
                            onNavigateToErrorReporting = onNavigateToErrorReporting,
                            onNotificationsChange = onNotificationsChange
                        )

                        WidgetSettingsSection()

                        AboutSettingsSection(
                            onNavigateToAbout = onNavigateToAbout,
                            onNavigateToDebug = onNavigateToDebug,
                            onLogout = onLogout
                        )
                    }
                }
            }
        } else {
            // Phone Layout
            val contentModifier = Modifier.fillMaxWidth()

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
                    onNavigateToPortSelection = onNavigateToPortSelection
                )
            }

            item {
                CustomizationSettingsSection(
                    modifier = contentModifier,
                    state = state,
                    onNavigateToThemeSelection = onNavigateToThemeSelection,
                    onNavigateToLoadDisplayMode = onNavigateToLoadDisplayMode
                )
            }

            item {
                PrivacySettingsSection(
                    modifier = contentModifier,
                    state = state,
                    onNavigateToCustomDns = onNavigateToCustomDns,
                    onNavigateToKillSwitch = onNavigateToKillSwitch,
                    onNavigateToErrorReporting = onNavigateToErrorReporting,
                    onNotificationsChange = onNotificationsChange
                )
            }

            item {
                WidgetSettingsSection(modifier = contentModifier)
            }

            item {
                AboutSettingsSection(
                    modifier = contentModifier,
                    onNavigateToAbout = onNavigateToAbout,
                    onNavigateToDebug = onNavigateToDebug,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun WidgetSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appWidgetManager = remember { android.appwidget.AppWidgetManager.getInstance(context) }
    val isSupported = remember { appWidgetManager.isRequestPinAppWidgetSupported }

    if (isSupported) {
        Category(modifier = modifier, title = stringResource(R.string.settings_widget)) {
            SettingRowWithIcon(
                icon = Icons.Rounded.Widgets,
                title = stringResource(R.string.settings_widget_add_to_home),
                subtitle = stringResource(R.string.settings_widget_add_to_home_desc),
                onClick = {
                    val myProvider = android.content.ComponentName(context, ru.protonmod.next.ui.widget.VpnWidgetProvider::class.java)
                    appWidgetManager.requestPinAppWidget(myProvider, null, null)
                }
            )
        }
    }
}

@Composable
private fun ConnectionSettingsSection(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onAutoConnectChange: (Boolean) -> Unit,
    onNavigateToApiBypass: (() -> Unit)?,
    onNavigateToPortSelection: ((Int) -> Unit)?
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

        SettingRowWithIcon(
            icon = Icons.Rounded.Numbers,
            title = stringResource(R.string.settings_port),
            subtitle = if (state.vpnPort == 0) stringResource(R.string.settings_port_auto) else state.vpnPort.toString(),
            onClick = { onNavigateToPortSelection?.invoke(state.vpnPort) }
        )
    }
}

@Composable
private fun CustomizationSettingsSection(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onNavigateToThemeSelection: (() -> Unit)?,
    onNavigateToLoadDisplayMode: (() -> Unit)?
) {
    Category(modifier = modifier, title = stringResource(R.string.settings_customization)) {
        val currentThemeName = when (state.appTheme) {
            AppTheme.LIGHT -> stringResource(R.string.theme_light)
            AppTheme.DARK -> stringResource(R.string.theme_dark)
            AppTheme.AMOLED -> stringResource(R.string.theme_amoled)
            AppTheme.GOLD_LIGHT -> stringResource(R.string.theme_gold_light)
            AppTheme.GOLD_DARK -> stringResource(R.string.theme_gold_dark)
            AppTheme.GOLD_AMOLED -> stringResource(R.string.theme_gold_amoled)
            AppTheme.SURFSHARK -> stringResource(R.string.theme_surfshark)
            AppTheme.NORD -> stringResource(R.string.theme_nord)
            AppTheme.IPVANISH -> stringResource(R.string.theme_ipvanish)
            AppTheme.PUREVPN -> stringResource(R.string.theme_purevpn)
            AppTheme.MULLVAD -> stringResource(R.string.theme_mullvad)
            AppTheme.WINDSCRIBE -> stringResource(R.string.theme_windscribe)
        }

        SettingRowWithIcon(
            title = stringResource(R.string.settings_app_theme),
            subtitle = currentThemeName,
            icon = Icons.Rounded.Palette,
            onClick = { onNavigateToThemeSelection?.invoke() }
        )

        val currentLoadModeName = when (state.serverLoadDisplayMode) {
            ru.protonmod.next.data.local.ServerLoadDisplayMode.ALL -> stringResource(R.string.load_mode_all)
            ru.protonmod.next.data.local.ServerLoadDisplayMode.LINE -> stringResource(R.string.load_mode_line)
            ru.protonmod.next.data.local.ServerLoadDisplayMode.PERCENT -> stringResource(R.string.load_mode_percent)
            ru.protonmod.next.data.local.ServerLoadDisplayMode.HIDDEN -> stringResource(R.string.load_mode_hidden)
        }

        SettingRowWithIcon(
            title = stringResource(R.string.settings_load_display_mode),
            subtitle = currentLoadModeName,
            icon = Icons.Rounded.BarChart,
            onClick = { onNavigateToLoadDisplayMode?.invoke() }
        )
    }
}

@Composable
private fun PrivacySettingsSection(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onNavigateToCustomDns: (() -> Unit)?,
    onNavigateToKillSwitch: (() -> Unit)?,
    onNavigateToErrorReporting: (() -> Unit)?,
    onNotificationsChange: (Boolean) -> Unit
) {
    Category(modifier = modifier, title = stringResource(R.string.settings_privacy)) {
        val currentDnsSubtitle = state.customDns.ifBlank {
            stringResource(R.string.settings_custom_dns_default)
        }

        SettingRowWithIcon(
            icon = Icons.Rounded.Dns,
            title = stringResource(R.string.settings_custom_dns),
            subtitle = currentDnsSubtitle,
            onClick = onNavigateToCustomDns
        )

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
    onNavigateToAbout: (() -> Unit)?,
    onNavigateToDebug: (() -> Unit)? = null,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    Category(modifier = modifier, title = stringResource(R.string.settings_about)) {
        SettingRowWithIcon(
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            onClick = onNavigateToAbout
        )

        if (BuildConfig.DEBUG) {
            SettingRowWithIcon(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.settings_debug),
                subtitle = stringResource(R.string.debug_title),
                onClick = onNavigateToDebug
            )
        }

        SettingRowWithIcon(
            icon = Icons.AutoMirrored.Rounded.Logout,
            title = stringResource(R.string.btn_logout),
            subtitle = stringResource(R.string.desc_toggle_connection),
            onClick = { showLogoutDialog = true },
            titleColor = ProtonNextTheme.colors.notificationError
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.btn_logout)) },
            text = { Text(stringResource(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ProtonNextTheme.colors.notificationError)
                ) {
                    Text(stringResource(R.string.btn_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            containerColor = ProtonNextTheme.colors.backgroundSecondary,
            titleContentColor = ProtonNextTheme.colors.textNorm,
            textContentColor = ProtonNextTheme.colors.textWeak
        )
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
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                alpha = if (isActive) 0.3f else 0.4f,
                shadowElevation = 0.dp
            )
            .clickable(onClick = onClick)
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
