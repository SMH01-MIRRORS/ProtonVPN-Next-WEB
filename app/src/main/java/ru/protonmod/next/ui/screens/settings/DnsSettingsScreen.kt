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
 * along with this program.  See <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember(uiState.customDns) { mutableStateOf(uiState.customDns) }

    Box(modifier = Modifier.fillMaxSize().background(colors.backgroundNorm)) {
        // Background gradient (Fullscreen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(colors.brandNorm.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_custom_dns), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = colors.textNorm,
                        navigationIconContentColor = colors.textNorm
                    ),
                    windowInsets = WindowInsets.statusBars
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(shape = RoundedCornerShape(24.dp), alpha = 0.4f, shadowElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
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
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text(stringResource(R.string.settings_custom_dns_placeholder)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.brandNorm,
                                unfocusedBorderColor = colors.shade20,
                                focusedTextColor = colors.textNorm,
                                unfocusedTextColor = colors.textNorm
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setCustomDns("")
                                    onBack()
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, colors.shade20)
                            ) {
                                Text(stringResource(R.string.settings_custom_dns_reset), color = colors.textWeak)
                            }

                            Button(
                                onClick = {
                                    viewModel.setCustomDns(inputText.trim())
                                    onBack()
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
                            ) {
                                Text(stringResource(R.string.btn_save), color = colors.textInverted)
                            }
                        }
                    }
                }
            }
        }
    }
}
