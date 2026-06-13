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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.protonmod.next.data.local.SetupStep
import ru.protonmod.next.ui.theme.ProtonNextTheme

/**
 * A full-screen loading overlay used during setup/login transitions.
 */
@Composable
fun SetupLoadingScreen(
    message: String,
    modifier: Modifier = Modifier,
    step: SetupStep = SetupStep.LOADING,
    progress: Float? = null,
    currentStrategy: String? = null,
    onSkip: (() -> Unit)? = null
) {
    val colors = ProtonNextTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "loading_pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Optimization: Removed redundant ExpressiveBackground call here because 
        // SetupLoadingScreen is used inside WelcomeScreen which already provides the background.
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
            ) {
                // Background glow
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    colors.brandNorm.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                MorphedGears(
                    modifier = Modifier.size(200.dp),
                    color = colors.brandNorm
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textNorm,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (progress != null) stringResource(ru.protonmod.next.R.string.byedpi_auto_test_desc)
                       else stringResource(ru.protonmod.next.R.string.setup_please_wait_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textWeak,
                textAlign = TextAlign.Center
            )

            if (progress != null) {
                Spacer(modifier = Modifier.height(32.dp))
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = colors.brandNorm,
                    trackColor = colors.separatorNorm
                )
                
                if (currentStrategy != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentStrategy,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
                
                if (onSkip != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(ru.protonmod.next.R.string.btn_skip), color = colors.brandNorm)
                    }
                }
            }
        }
    }
}

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
