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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

/**
 * Screen for configuring API Block Bypass strategies.
 * Features smart detection to disable bypass if a VPN is already active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiBypassScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    val isTablet = isTablet()

    // Assuming the ViewModel exposes whether ANY VPN (ours or third-party) is active
    // via ConnectivityManager NetworkCapabilities.TRANSPORT_VPN
    val isAnyVpnActive = uiState.isAnyVpnActive

    // Force disable the feature if VPN is active
    val isEffectivelyEnabled = uiState.apiBypassEnabled && !isAnyVpnActive

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background gradient matching the unified design language
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start
            ) {
                NavigationHeader(
                    title = stringResource(R.string.settings_api_bypass),
                    onBack = onBack
                )

                val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                // Header Image/Icon
                Box(
                    modifier = contentModifier
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(colors.brandNorm.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.brandNorm
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.settings_api_bypass),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = stringResource(R.string.settings_api_bypass_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    modifier = contentModifier
                        .padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Smart Warning for active VPN
                AnimatedVisibility(
                    visible = isAnyVpnActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = contentModifier.padding(horizontal = 16.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.notificationWarning.copy(alpha = 0.15f),
                        contentColor = colors.notificationWarning
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = colors.notificationWarning,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.api_bypass_vpn_detected),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.notificationWarning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Main Settings Card
                Box(
                    modifier = contentModifier
                        .padding(horizontal = 16.dp)
                        .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.4f, shadowElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {

                        // Master Toggle for API Bypass
                        SettingToggleRow(
                            title = stringResource(R.string.settings_api_bypass),
                            subtitle = when {
                                isAnyVpnActive -> stringResource(R.string.api_bypass_disabled_by_vpn)
                                isEffectivelyEnabled -> stringResource(R.string.st_enabled_subtitle)
                                else -> stringResource(R.string.st_disabled_subtitle)
                            },
                            icon = Icons.Rounded.Security,
                            checked = isEffectivelyEnabled,
                            enabled = !isAnyVpnActive,
                            onCheckedChange = { viewModel.setApiBypassEnabled(it) }
                        )

                        // Expanded Strategy Options
                        AnimatedVisibility(
                            visible = isEffectivelyEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = colors.separatorNorm.copy(alpha = 0.5f)
                                )

                                // Section Title
                                Text(
                                    text = stringResource(R.string.api_bypass_strategy_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textWeak,
                                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                                )

                                // Strategy 1: Netlify (Currently the only one, but built to scale)
                                StrategySelectionRow(
                                    title = stringResource(R.string.api_bypass_strategy_netlify),
                                    description = stringResource(R.string.api_bypass_strategy_netlify_desc),
                                    icon = Icons.Rounded.Public,
                                    isSelected = uiState.apiBypassStrategy == "netlify",
                                    onClick = { viewModel.setApiBypassStrategy("netlify") }
                                )

                                // Future strategies can be added here easily
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Reusable component for selecting a bypass strategy (works like a rich RadioButton row).
 */
@Composable
private fun StrategySelectionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = ProtonNextTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) colors.brandNorm.copy(alpha = 0.15f) else colors.backgroundNorm),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) colors.brandNorm else colors.iconNorm,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Texts
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textNorm
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textWeak
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Radio indicator
        RadioButton(
            selected = isSelected,
            onClick = null, // Handled by the Row's clickable
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.brandNorm,
                unselectedColor = colors.iconWeak
            )
        )
    }
}