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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.theme.ProtonNextTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelingIpsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SplitTunnelingIpsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var inputValue by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            // Background gradient
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                item(contentType = "Header") {
                    NavigationHeader(
                        title = stringResource(
                            if (uiState.splitTunnelingMode == "exclude") R.string.settings_excluded_ips
                            else R.string.settings_included_ips
                        ),
                        onBack = onBack
                    )
                }

                item(contentType = "InputRow") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // IP Input Row matching Proton Design
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = inputValue,
                                onValueChange = {
                                    inputValue = it
                                    inputError = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.st_ip_hint),
                                        color = colors.textWeak
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                    keyboardType = KeyboardType.Number
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (inputValue.isNotBlank()) {
                                            viewModel.addIp(inputValue)
                                            inputValue = ""
                                            focusManager.clearFocus()
                                        } else {
                                            inputError = true
                                        }
                                    }
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.7f),
                                    unfocusedContainerColor = colors.backgroundSecondary.copy(alpha = 0.7f),
                                    errorContainerColor = colors.backgroundSecondary.copy(alpha = 0.7f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    focusedTextColor = colors.textNorm,
                                    unfocusedTextColor = colors.textNorm
                                ),
                                singleLine = true,
                                isError = inputError
                            )

                            Spacer(Modifier.width(12.dp))

                            // Add Button
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (inputValue.isNotBlank()) colors.brandNorm
                                        else colors.backgroundSecondary.copy(alpha = 0.3f)
                                    )
                                    .clickable(enabled = inputValue.isNotBlank()) {
                                        viewModel.addIp(inputValue)
                                        inputValue = ""
                                        focusManager.clearFocus()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.st_add_ip_desc),
                                    tint = if (inputValue.isNotBlank()) colors.textInverted
                                    else colors.iconWeak
                                )
                            }
                        }

                        if (inputError) {
                            Text(
                                text = stringResource(R.string.st_invalid_ip_error),
                                color = colors.notificationError,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                if (uiState.ips.isNotEmpty()) {
                    item(contentType = "SectionHeader") {
                        Text(
                            text = stringResource(
                                if (uiState.splitTunnelingMode == "exclude") R.string.st_excluded_ips_header
                                else R.string.st_included_ips_header,
                                uiState.ips.size
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.brandNorm,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                        )
                    }

                    items(uiState.ips, key = { it.ip }, contentType = { "Ip" }) { ipEntry ->
                        IpListItem(
                            ip = ipEntry.ip,
                            onRemove = { viewModel.removeIp(ipEntry.ip) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    item(contentType = "EmptyState") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.st_no_ips_added),
                                color = colors.textWeak,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IpListItem(
    ip: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onRemove)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Globe Icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.backgroundNorm.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = colors.iconWeak,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = ip,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textNorm,
            modifier = Modifier.weight(1f)
        )

        // Remove Icon
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.st_remove_ip_desc),
            tint = colors.iconWeak,
            modifier = Modifier.size(24.dp)
        )
    }
}
