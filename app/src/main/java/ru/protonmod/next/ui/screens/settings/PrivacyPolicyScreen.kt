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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.theme.ProtonNextTheme

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val resources = LocalResources.current
    val policyText = remember {
        try {
            resources.openRawResource(R.raw.privacy_policy)
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Error loading privacy policy"
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background gradient
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

            Column(modifier = Modifier.fillMaxSize()) {
                NavigationHeader(
                    title = stringResource(R.string.settings_privacy_policy),
                    onBack = onBack
                )

                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val lines = policyText.split("\n")
                        items(lines) { line ->
                            MarkdownLine(line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownLine(line: String) {
    val colors = ProtonNextTheme.colors
    when {
        line.startsWith("# ") -> {
            Text(
                text = line.substring(2),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textNorm,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        line.startsWith("## ") -> {
            Text(
                text = line.substring(3),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textNorm,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        line.startsWith("### ") -> {
            Text(
                text = line.substring(4),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textNorm,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        line.startsWith("#### ") -> {
            Text(
                text = line.substring(5),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textNorm,
                fontWeight = FontWeight.Bold
            )
        }
        line.trim().isEmpty() -> {
            Spacer(modifier = Modifier.height(4.dp))
        }
        else -> {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textWeak
            )
        }
    }
}
