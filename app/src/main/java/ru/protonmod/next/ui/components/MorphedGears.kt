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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.utils.ProtonLogger

/**
 * A loading animation featuring two morphed shapes rotating like gears.
 * Used in the "Please Wait" screen of the setup wizard.
 */
@Composable
fun MorphedGears(
    modifier: Modifier = Modifier,
    color: Color = ProtonNextTheme.colors.brandNorm,
) {
    // Gear 1 rotation state
    val gear1Rotation = remember { Animatable(0f) }
    // Gear 2 rotation state
    val gear2Rotation = remember { Animatable(0f) }
    // Morph progress
    val morphProgress = remember { Animatable(0f) }

    LaunchedEffect(color) {
        ProtonLogger.v("MorphedGears", "Initializing Animation")

        // Gear 1 "ticking" rotation: 45 degrees every 0.5s
        launch {
            while(true) {
                gear1Rotation.animateTo(
                    targetValue = gear1Rotation.value + 45f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                )
                delay(200) // Wait to total 0.5s per tick
            }
        }
        // Gear 2 (opposite)
        launch {
            while(true) {
                gear2Rotation.animateTo(
                    targetValue = gear2Rotation.value - 45f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                )
                delay(200)
            }
        }
        // Continuous Morphing
        launch {
            while(true) {
                morphProgress.animateTo(1f, tween(2000, easing = LinearEasing))
                morphProgress.animateTo(0f, tween(2000, easing = LinearEasing))
            }
        }
    }

    val gear1 = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.6f,
            rounding = CornerRounding(0.2f)
        )
    }
    val gear2 = remember {
        RoundedPolygon.circle(numVertices = 8)
    }
    val morph = remember { Morph(gear1, gear2) }

    Canvas(modifier = modifier.size(120.dp)) {
        val baseSize = size.minDimension * 0.4f
        
        val currentMorph = morph.toPath(morphProgress.value).asComposePath()

        // Upper Gear
        withTransform(
            {
                translate(size.width / 2, size.height * 0.35f)
                rotate(gear1Rotation.value)
                scale(baseSize, baseSize)
            }
        ) {
            // Draw both stroke and a very faint fill to ensure visibility
            drawPath(
                path = currentMorph,
                color = color.copy(alpha = 0.1f)
            )
            drawPath(
                path = currentMorph,
                color = color.copy(alpha = 0.8f),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Lower Gear
        withTransform(
            {
                translate(size.width / 2, size.height * 0.65f)
                rotate(gear2Rotation.value + 22.5f)
                scale(baseSize, baseSize)
            }
        ) {
            drawPath(
                path = currentMorph,
                color = color.copy(alpha = 0.1f)
            )
            drawPath(
                path = currentMorph,
                color = color.copy(alpha = 0.5f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
