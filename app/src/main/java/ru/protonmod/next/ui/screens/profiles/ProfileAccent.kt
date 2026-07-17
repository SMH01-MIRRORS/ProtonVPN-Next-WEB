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

package ru.protonmod.next.ui.screens.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.protonmod.next.ui.theme.ProtonNextTheme

/**
 * Accent gradient pair for a profile card, derived from the connection target:
 * fastest -> green, city -> red, specific server -> metallic gray,
 * country-only -> brand color.
 */
data class ProfileAccent(
    val start: Color,
    val end: Color,
)

@Composable
fun profileAccent(
    targetServerId: String?,
    targetCity: String?,
    targetCountry: String?,
): ProfileAccent = when {
    // Specific server: metallic gray (silver -> steel).
    targetServerId != null -> ProfileAccent(Color(0xFFB0BEC5), Color(0xFF607D8B))
    // City: red.
    targetCity != null -> ProfileAccent(Color(0xFFEF5350), Color(0xFFC62828))
    // Country only: brand color.
    targetCountry != null -> ProfileAccent(
        ProtonNextTheme.colors.brandNorm,
        ProtonNextTheme.colors.brandNorm,
    )
    // Fastest connection: green.
    else -> ProfileAccent(Color(0xFF66BB6A), Color(0xFF2E7D32))
}
