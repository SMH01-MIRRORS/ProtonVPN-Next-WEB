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

package ru.protonmod.next.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.ui.theme.ProtonNextTheme

@Composable
fun LoadIndicator(
    load: Int,
    modifier: Modifier = Modifier,
    displayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL
) {
    if (displayMode == ServerLoadDisplayMode.HIDDEN || displayMode == ServerLoadDisplayMode.LINE) return

    val colors = ProtonNextTheme.colors
    val color = when {
        load < 40 -> colors.notificationSuccess
        load < 70 -> colors.notificationWarning
        else -> colors.notificationError
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.location_load, load),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LoadProgressBar(
    load: Int,
    modifier: Modifier = Modifier,
    displayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL
) {
    if (displayMode == ServerLoadDisplayMode.HIDDEN || displayMode == ServerLoadDisplayMode.PERCENT) return

    val colors = ProtonNextTheme.colors
    val color = when {
        load < 40 -> colors.notificationSuccess
        load < 70 -> colors.notificationWarning
        else -> colors.notificationError
    }

    ExpressiveLinearProgressIndicator(
        progress = { load / 100f },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        color = color,
        trackColor = color.copy(alpha = 0.1f)
    )
}
