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

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import ru.protonmod.next.ui.theme.ProtonNextTheme

/**
 * A "Google Pixel Style" shape-shifting circular loading indicator.
 * In Android 16, this is known as Material 3 Expressive LoadingIndicator.
 *
 * @param modifier Modifier for this indicator.
 * @param color Color of the indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    LoadingIndicator(
        modifier = modifier,
        color = color
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewExpressiveCircularProgressIndicator() {
    ProtonNextTheme {
        ExpressiveCircularProgressIndicator(modifier = Modifier.size(48.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewExpressiveLinearProgressIndicator() {
    ProtonNextTheme {
        ExpressiveLinearProgressIndicator(
            progress = { 0.5f },
            modifier = Modifier.height(16.dp)
        )
    }
}

/**
 * A "worm/fuse" style linear progress indicator with a wavy animation.
 *
 * @param progress Current progress value from 0.0 to 1.0.
 * @param modifier Modifier for this indicator.
 * @param color Color of the active part of the indicator.
 * @param trackColor Color of the track background.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val strokeWidth = with(LocalDensity.current) { 4.dp.toPx() }
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        trackStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

/**
 * An indeterminate "worm/fuse" style linear progress indicator.
 *
 * @param modifier Modifier for this indicator.
 * @param color Color of the indicator.
 * @param trackColor Color of the track background.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val strokeWidth = with(LocalDensity.current) { 4.dp.toPx() }
    LinearWavyProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        trackStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}
