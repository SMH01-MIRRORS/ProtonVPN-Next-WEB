/*
 * Copyright (c) 2026 SMH01
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

package ru.protonmod.next.utils

import android.content.res.Resources
import kotlin.math.roundToInt

object ViewUtils {
    fun convertDpToPixel(dp: Int): Int =
        (dp * Resources.getSystem().displayMetrics.density).roundToInt()

    fun Int.toPx(): Int = convertDpToPixel(this)
}

fun Int.toPx(): Int = ViewUtils.convertDpToPixel(this)
