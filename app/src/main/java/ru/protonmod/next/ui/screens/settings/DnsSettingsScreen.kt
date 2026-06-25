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

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = isTablet()

    // Internal state for selection and input
    var useDefaultDns by remember(uiState.customDns) { mutableStateOf(uiState.customDns.isBlank()) }
    var inputText by remember(uiState.customDns) { mutableStateOf(uiState.customDns) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background gradient (Fullscreen)
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start,
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

                item(contentType = "Header") {
                    NavigationHeader(
                        title = stringResource(R.string.settings_custom_dns),
                        onBack = onBack
                    )
                }

                item(contentType = "Info") {
                    Box(modifier = contentModifier.padding(horizontal = 16.dp)) {
                        InfoCard(text = stringResource(R.string.settings_custom_dns_desc))
                    }
                }

                item(contentType = "ModeSelection") {
                    SettingsCategory(
                        modifier = contentModifier.padding(horizontal = 16.dp),
                        title = stringResource(R.string.settings_custom_dns_title)
                    ) {
                        // Default Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { useDefaultDns = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = useDefaultDns,
                                onClick = { useDefaultDns = true },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_custom_dns_default),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = colors.textNorm
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.shade20.copy(alpha = 0.5f)
                        )

                        // Custom Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { useDefaultDns = false }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !useDefaultDns,
                                onClick = { useDefaultDns = false },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brandNorm)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.settings_custom_dns),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = colors.textNorm
                            )
                        }

                        // Animated Custom Input
                        AnimatedVisibility(
                            visible = !useDefaultDns,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SmoothOutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.settings_custom_dns_placeholder),
                                            color = colors.textWeak.copy(alpha = 0.5f)
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.brandNorm,
                                        unfocusedBorderColor = colors.shade20,
                                        focusedTextColor = colors.textNorm,
                                        unfocusedTextColor = colors.textNorm,
                                        focusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.5f),
                                        unfocusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item(contentType = "Actions") {
                    val isChanged = remember(useDefaultDns, inputText, uiState.customDns) {
                        val currentEffectiveDns = if (useDefaultDns) "" else inputText.trim()
                        currentEffectiveDns != uiState.customDns
                    }

                    Column(
                        modifier = contentModifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val finalDns = if (useDefaultDns) "" else inputText.trim()
                                viewModel.setCustomDns(finalDns)
                                onBack()
                            },
                            enabled = isChanged || !useDefaultDns && inputText.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brandNorm,
                                disabledContainerColor = colors.brandNorm.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                stringResource(R.string.btn_save),
                                color = colors.textInverted,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (uiState.customDns.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    viewModel.setCustomDns("")
                                    onBack()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.settings_custom_dns_reset),
                                    color = colors.notificationError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
