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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortSelectionScreen(
    currentPort: Int,
    onBack: () -> Unit,
    onPortSelected: (Int) -> Unit
) {
    val colors = ProtonNextTheme.colors
    val portOptions = listOf(0, 443, 123, 1194, 51820)

    Box(modifier = Modifier.fillMaxSize().background(colors.backgroundNorm)) {
        // Background gradient
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
                    title = { Text(stringResource(R.string.settings_port), fontWeight = FontWeight.Bold) },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(portOptions) { port ->
                    val isSelected = port == currentPort
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(
                                shape = RoundedCornerShape(20.dp),
                                alpha = if (isSelected) 0.6f else 0.4f,
                                shadowElevation = 0.dp
                            )
                            .clickable { onPortSelected(port) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (port == 0) stringResource(R.string.settings_port_auto) else port.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) colors.brandNorm else colors.textNorm
                                )
                                if (port == 0) {
                                    Text(
                                        text = stringResource(R.string.settings_port_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textWeak
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.brandNorm,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                RadioButton(
                                    selected = false,
                                    onClick = { onPortSelected(port) },
                                    colors = RadioButtonDefaults.colors(unselectedColor = colors.shade60)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
