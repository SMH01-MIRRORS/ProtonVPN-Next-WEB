/*
 * Copyright (C) 2026 SMH01
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ru.protonmod.next.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.netshield.NetShieldStats

@Composable
fun NetShieldStatsCard(stats: NetShieldStats, compact: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .then(if (compact) Modifier.widthIn(max = 360.dp) else Modifier.fillMaxWidth())
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.88f else 1f), RoundedCornerShape(16.dp))
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(stats.adsBlocked.toString(), stringResource(R.string.netshield_ads_blocked), Modifier.weight(1f))
        Stat(stats.trackersBlocked.toString(), stringResource(R.string.netshield_trackers_blocked), Modifier.weight(1f))
        Stat(formatBytes(stats.savedBytes), stringResource(R.string.netshield_data_saved), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
