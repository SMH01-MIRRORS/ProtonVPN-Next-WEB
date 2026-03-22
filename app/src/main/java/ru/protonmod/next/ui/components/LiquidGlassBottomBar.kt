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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.protonmod.next.ui.nav.MainTarget
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.ui.utils.isTablet

@Composable
fun LiquidGlassBottomBar(
    selectedTarget: MainTarget?,
    showCountries: Boolean = true,
    showGateways: Boolean = true,
    notificationDots: Set<MainTarget> = emptySet(),
    navigateTo: (MainTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTablet = isTablet()

    val glassShape = RoundedCornerShape(32.dp)

    val targets = mutableListOf(MainTarget.Home)
    if (showCountries) targets.add(MainTarget.Countries)
    targets.add(MainTarget.Profiles)
    targets.add(MainTarget.Settings)

    // Center the bar on tablets and limit its width
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = if (isTablet) 400.dp else 600.dp) // Limit width on tablets
                .liquidGlass(
                    shape = glassShape,
                    alpha = 0.85f,
                    shadowElevation = 15.dp
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                targets.forEach { target ->
                    NavigationItem(
                        target = target,
                        isSelected = target == selectedTarget,
                        hasNotification = notificationDots.contains(target),
                        onNavigate = { navigateTo(target) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationItem(
    target: MainTarget,
    isSelected: Boolean,
    hasNotification: Boolean,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val activeColor = colors.brandNorm
    val inactiveColor = colors.iconWeak

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "iconColor"
    )

    val iconVector = getMaterialIconForTarget(target)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onNavigate() }
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            )
        }

        Box {
            Icon(
                imageVector = iconVector,
                contentDescription = target.name,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            if (hasNotification) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(colors.notificationError, CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}

private fun getMaterialIconForTarget(target: MainTarget): ImageVector {
    return when (target) {
        MainTarget.Home -> Icons.Rounded.Home
        MainTarget.Profiles -> Icons.Rounded.Terminal
        MainTarget.Countries -> Icons.Rounded.Public
        MainTarget.Settings -> Icons.Rounded.Settings
    }
}
