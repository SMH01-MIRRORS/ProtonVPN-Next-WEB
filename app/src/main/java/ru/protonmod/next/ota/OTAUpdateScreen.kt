package ru.protonmod.next.ota

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

@Composable
fun OTAUpdateScreen(
    viewModel: OTAUpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = ProtonNextTheme.colors
    val context = LocalContext.current

    val updateInfo = uiState.updateInfo ?: return

    if (updateInfo.force) {
        // Fullscreen forced update
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.brandNorm.copy(alpha = 0.2f),
                            colors.backgroundNorm
                        )
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = colors.brandNorm,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.ota_force_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textNorm,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.ota_force_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textWeak,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                if (uiState.isDownloading) {
                    Spacer(Modifier.height(32.dp))
                    LinearProgressIndicator(
                        progress = { uiState.downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.brandNorm,
                        trackColor = colors.backgroundSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ota_downloading, (uiState.downloadProgress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak
                    )
                } else {
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.startDownload(context, updateInfo) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.ota_btn_update))
                    }
                }
            }
        }
    } else {
        // Optional update dialog
        AlertDialog(
            onDismissRequest = { /* Handle dismiss */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = colors.brandNorm)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.ota_title))
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.ota_new_version, updateInfo.versionName),
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.ota_changelog), style = MaterialTheme.typography.labelMedium, color = colors.textWeak)
                    Text(updateInfo.changelog, color = colors.textWeak)
                    
                    if (uiState.isDownloading) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (!uiState.isDownloading) {
                    Button(onClick = { viewModel.startDownload(context, updateInfo) }) {
                        Text(stringResource(R.string.ota_btn_update))
                    }
                }
            },
            dismissButton = {
                if (!uiState.isDownloading) {
                    TextButton(onClick = { /* Dismiss */ }) {
                        Text(stringResource(R.string.ota_btn_later))
                    }
                }
            },
            containerColor = colors.backgroundSecondary,
            titleContentColor = colors.textNorm,
            textContentColor = colors.textWeak
        )
    }
}
