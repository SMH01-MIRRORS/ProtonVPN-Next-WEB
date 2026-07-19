/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.ui.screens.netshield

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.netshield.NetShieldLevel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetShieldSettingsScreen(
    onBack: () -> Unit,
    viewModel: NetShieldSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.netshield_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.netshield_description), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            NetShieldLevel.entries.forEach { level ->
                LevelRow(
                    level = level,
                    selected = state.level == level,
                    onClick = { viewModel.setLevel(level) },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.netshield_lists_title), fontWeight = FontWeight.Bold)
            val status = when {
                state.lists.isUpdating -> stringResource(R.string.netshield_lists_updating)
                state.lists.lastUpdatedAt > 0 -> stringResource(
                    R.string.netshield_lists_updated,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.lists.lastUpdatedAt)),
                    state.lists.domainCount,
                )
                else -> stringResource(R.string.netshield_lists_never_updated)
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            state.lists.error?.let { Text(stringResource(R.string.netshield_lists_error, it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::updateLists, enabled = !state.lists.isUpdating) {
                if (state.lists.isUpdating) {
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.netshield_update_lists))
                }
            }
            Text(stringResource(R.string.netshield_saved_estimate_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LevelRow(level: NetShieldLevel, selected: Boolean, onClick: () -> Unit) {
    val title = when (level) {
        NetShieldLevel.DISABLED -> R.string.netshield_level_off
        NetShieldLevel.MALWARE -> R.string.netshield_level_malware
        NetShieldLevel.ADS_TRACKERS -> R.string.netshield_level_extended
        NetShieldLevel.ADS_TRACKERS_ADULT -> R.string.netshield_level_adult
    }
    val description = when (level) {
        NetShieldLevel.DISABLED -> R.string.netshield_level_off_desc
        NetShieldLevel.MALWARE -> R.string.netshield_level_malware_desc
        NetShieldLevel.ADS_TRACKERS -> R.string.netshield_level_extended_desc
        NetShieldLevel.ADS_TRACKERS_ADULT -> R.string.netshield_level_adult_desc
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 8.dp)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
        }
    }
}
