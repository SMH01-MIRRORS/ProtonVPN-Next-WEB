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

package ru.protonmod.next.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    alpha: Float = 0.4f,
    borderAlpha: Float = 0.1f,
    shadowElevation: Dp = 0.dp
): Modifier {
    val colors = ProtonNextTheme.colors
    val isDark = colors.isDark
    
    val highlightColor = if (isDark) Color.White else Color.Black
    
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            highlightColor.copy(alpha = borderAlpha),
            Color.Transparent
        )
    )

    // Glass background: use backgroundSecondary with the provided alpha to ensure translucency
    val glassBackgroundColor = colors.backgroundSecondary.copy(alpha = alpha)

    return this
        .then(
            if (shadowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
            } else Modifier
        )
        .clip(shape)
        .background(glassBackgroundColor)
        .border(
            width = 0.8.dp,
            brush = borderBrush,
            shape = shape
        )
}
