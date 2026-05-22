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

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import ru.protonmod.next.ui.theme.ProtonNextTheme

/**
 * A fluid, animated background inspired by Material 3 Expressive and Google Pixel Setup Wizard.
 * It features transparent morphing shapes with white outlines.
 */
@Composable
fun ExpressiveBackground(
    modifier: Modifier = Modifier,
    alpha: Float = 0.8f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_bg")
    val colors = ProtonNextTheme.colors

    // Morph Progress Animation
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morph"
    )

    // Slow rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)),
        label = "global_rot"
    )

    // Define Shapes for Morphing (More organic/bloby)
    val blob1 = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.8f,
            rounding = CornerRounding(0.5f)
        )
    }
    val blob2 = remember {
        RoundedPolygon.circle(numVertices = 12)
    }
    val morph = remember { Morph(blob1, blob2) }

    Box(modifier = modifier.fillMaxSize()) {
        // Deep Background Glows (Purple/Brand)
        Canvas(modifier = Modifier.fillMaxSize().blur(120.dp)) {
            val baseRadius = size.minDimension * 0.7f
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.brandNorm.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = baseRadius
                )
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.brandNorm.copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.8f),
                    radius = baseRadius * 1.2f
                )
            )
        }

        // Outlined Morphing Shapes (Moving slowly)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .blur(4.dp)
        ) {
            val width = size.width
            val height = size.height
            val baseSize = size.minDimension * 0.9f

            withTransform({
                translate(width * 0.5f, height * 0.5f)
                rotate(rotation)
                scale(baseSize, baseSize)
            }) {
                val path = morph.toPath(morphProgress).asComposePath()
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.15f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            
            withTransform({
                translate(width * 0.4f, height * 0.6f)
                rotate(-rotation * 1.2f)
                scale(baseSize * 1.1f, baseSize * 1.1f)
            }) {
                val path = morph.toPath(1f - morphProgress).asComposePath()
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.1f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
