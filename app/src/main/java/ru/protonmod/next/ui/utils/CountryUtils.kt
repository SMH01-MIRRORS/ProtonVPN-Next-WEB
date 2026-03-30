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

import android.content.Context
import androidx.compose.ui.graphics.Color
import ru.protonmod.next.ui.theme.ProtonPalette
import java.util.Locale

object CountryUtils {

    /**
     * Returns a drawable resource ID for a country flag, or 0 if not found.
     */
    fun getFlagResource(context: Context, countryCode: String?): Int {
        if (countryCode == null || countryCode.isBlank()) return 0
        
        // Handle codes with suffixes like "US-FREE", "NL-BASIC", etc.
        val baseCode = countryCode.split('-')[0].lowercase().trim()
        
        // Standard mapping: UK -> GB for resource matching if necessary.
        val normalizedCode = when (baseCode) {
            "uk" -> "gb"
            else -> baseCode
        }
        
        val resName = "flag_$normalizedCode"
        return context.resources.getIdentifier(resName, "drawable", context.packageName)
    }

    /**
     * Generates an Emoji flag from an ISO country code (e.g., "US" -> 🇺🇸)
     */
    fun getFlagForCountry(countryCode: String?): String {
        if (countryCode == null || countryCode.isBlank()) return "🌍"
        
        val baseCode = countryCode.split('-')[0].uppercase().trim()
        if (baseCode.length != 2) return "🌍"
        
        val firstChar = Character.codePointAt(baseCode, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(baseCode, 1) - 0x41 + 0x1F1E6
        
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    /**
     * Returns the localized country name.
     */
    fun getCountryName(context: Context, countryCode: String?): String {
        if (countryCode == null || countryCode.equals("null", ignoreCase = true) || countryCode.isBlank()) return ""

        val baseCode = countryCode.split('-')[0].uppercase().trim()
        val locale = Locale.Builder().setRegion(baseCode).build()
        val displayName = locale.getDisplayCountry(Locale.getDefault())
        
        return if (displayName.isNotEmpty() && !displayName.equals(baseCode, ignoreCase = true)) {
            displayName
        } else {
            val resourceName = "country_${baseCode.lowercase()}"
            val resourceId = context.resources.getIdentifier(resourceName, "string", context.packageName)
            if (resourceId != 0) {
                context.getString(resourceId).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: baseCode
            } else baseCode
        }
    }

    /**
     * Returns a color based on the load:
     * 0-60% -> Green (Low)
     * 60-85% -> Yellow (Medium)
     * 85-100% -> Red (High)
     */
    fun getColorForLoad(load: Int): Color {
        return when {
            load < 60 -> ProtonPalette.Apple
            load < 85 -> ProtonPalette.Sunglow
            else -> ProtonPalette.Pomegranate
        }
    }
}
