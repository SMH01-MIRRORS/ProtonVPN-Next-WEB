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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.data.model.BackupCategory
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.components.NavigationHeader

@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val colors = ProtonNextTheme.colors

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        viewModel.exportToUri(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    LaunchedEffect(uiState.showSuccessExport) {
        if (uiState.showSuccessExport) {
            snackbarHostState.showSnackbar(context.getString(R.string.backup_export_success))
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.showSuccessImport) {
        if (uiState.showSuccessImport) {
            snackbarHostState.showSnackbar(context.getString(R.string.backup_import_success))
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.lastError) {
        uiState.lastError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearMessages()
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.backup_import), color = colors.textNorm) },
            text = { Text(stringResource(R.string.backup_import_confirm), color = colors.textWeak) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    viewModel.importFromUri(pendingImportUri)
                }) {
                    Text(stringResource(android.R.string.ok), color = colors.brandNorm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel), color = colors.textWeak)
                }
            },
            containerColor = colors.backgroundSecondary
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Background gradient decoration
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
                    .statusBarsPadding()
            ) {
                NavigationHeader(
                    title = stringResource(R.string.backup_title),
                    onBack = onNavigateBack
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SettingsCategory(title = stringResource(R.string.backup_categories)) {
                            BackupCategory.entries.forEach { category ->
                                SettingToggleRow(
                                    title = getCategoryName(category),
                                    checked = uiState.selectedCategories.contains(category),
                                    onCheckedChange = { viewModel.toggleCategory(category) }
                                )
                            }
                        }
                    }
                }

                // Action Buttons at the bottom
                Surface(
                    color = colors.backgroundSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding()
                    ) {
                        Button(
                            onClick = { 
                                if (uiState.selectedCategories.isNotEmpty()) {
                                    exportLauncher.launch("proton_vpn_backup_${System.currentTimeMillis()}.json")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.selectedCategories.isNotEmpty() && !uiState.isExporting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brandNorm,
                                contentColor = colors.textInverted
                            )
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_export))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { 
                                if (uiState.selectedCategories.isNotEmpty()) {
                                    importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.selectedCategories.isNotEmpty() && !uiState.isImporting,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.brandNorm),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.brandNorm
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_import))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getCategoryName(category: BackupCategory): String {
    return when (category) {
        BackupCategory.GENERAL_SETTINGS -> stringResource(R.string.backup_cat_general)
        BackupCategory.OBFUSCATION -> stringResource(R.string.backup_cat_obfuscation)
        BackupCategory.API_BYPASS -> stringResource(R.string.backup_cat_api_bypass)
        BackupCategory.PROFILES -> stringResource(R.string.backup_cat_profiles)
        BackupCategory.RECENT_CONNECTIONS -> stringResource(R.string.backup_cat_recent)
        BackupCategory.QUICK_CONNECT -> stringResource(R.string.backup_cat_quick_connect)
        BackupCategory.SPLIT_TUNNELING -> stringResource(R.string.backup_cat_split_tunneling)
        BackupCategory.VPN_PORT -> stringResource(R.string.backup_cat_vpn_port)
        BackupCategory.DNS -> stringResource(R.string.backup_cat_dns)
        BackupCategory.SPOOF_COUNTRY -> stringResource(R.string.backup_cat_spoof_country)
        BackupCategory.OTA_UPDATES -> stringResource(R.string.backup_cat_ota)
        BackupCategory.TRUSTED_WIFI -> stringResource(R.string.backup_cat_trusted_wifi)
        BackupCategory.SENTRY_ANALYTICS -> stringResource(R.string.backup_cat_sentry)
    }
}
