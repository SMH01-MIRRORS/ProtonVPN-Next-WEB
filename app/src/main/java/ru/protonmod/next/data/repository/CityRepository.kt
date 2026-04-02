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
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package ru.protonmod.next.data.repository

import kotlinx.coroutines.withContext
import ru.protonmod.next.data.local.CityTranslationDao
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CityRepository @Inject constructor(
    private val cityTranslationDao: CityTranslationDao,
    private val dispatcherProvider: DispatcherProvider
) {
    // Memory cache for city translations to avoid frequent database queries
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Returns the localized name of a city.
     * If a translation is not found in the database, the original English name is returned.
     *
     * @param countryCode Two-letter country code (ISO 3166-1 alpha-2).
     * @param englishName English name of the city.
     * @return Localized city name or the original name if no translation is available.
     */
    suspend fun getLocalizedCityName(countryCode: String, englishName: String): String = withContext(dispatcherProvider.io()) {
        val languageCode = Locale.getDefault().toLanguageTag()
        val cacheKey = "$countryCode-$englishName-$languageCode"
        
        cache[cacheKey]?.let { return@withContext it }

        val localized = cityTranslationDao.getLocalizedName(countryCode, englishName, languageCode)
        if (localized != null) {
            cache[cacheKey] = localized
            localized
        } else {
            englishName
        }
    }

    /**
     * Clears the in-memory cache of city translations.
     */
    fun clearCache() {
        cache.clear()
    }
}
