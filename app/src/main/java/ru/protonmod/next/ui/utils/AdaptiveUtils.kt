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

package ru.protonmod.next.ui.utils

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

enum class DeviceType {
    Phone, Tablet
}

val LocalDeviceType = compositionLocalOf { DeviceType.Phone }

@Composable
fun ProvideDeviceType(
    windowWidthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val deviceType = when (windowWidthSizeClass) {
        WindowWidthSizeClass.Expanded -> DeviceType.Tablet
        else -> DeviceType.Phone
    }
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        CompositionLocalProvider(LocalDeviceType provides deviceType) {
            content()
        }
    }
}

@Composable
fun isTablet(): Boolean = LocalDeviceType.current == DeviceType.Tablet
