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

package ru.protonmod.next.data.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.RecentConnectionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.model.BackupCategory
import ru.protonmod.next.data.model.BackupData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val settingsManager: SettingsManager,
    private val profileDao: ProfileDao,
    private val recentConnectionDao: RecentConnectionDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportData(categories: Set<BackupCategory>): String {
        val allSettings = settingsManager.getAllPreferences()
        
        val filteredSettings = mutableMapOf<String, String>()
        
        categories.forEach { category ->
            val keys = getKeysForCategory(category)
            keys.forEach { key ->
                allSettings[key]?.let { filteredSettings[key] = it }
            }
        }

        val profiles = if (categories.contains(BackupCategory.PROFILES)) {
            profileDao.getAllProfiles()
        } else null

        val recentConnections = if (categories.contains(BackupCategory.RECENT_CONNECTIONS)) {
            recentConnectionDao.getAllRecentConnections()
        } else null

        val backupData = BackupData(
            settings = if (filteredSettings.isNotEmpty()) filteredSettings else null,
            profiles = profiles,
            recentConnections = recentConnections
        )

        return json.encodeToString(backupData)
    }

    suspend fun importData(jsonContent: String, categories: Set<BackupCategory>) {
        val backupData = try {
            json.decodeFromString<BackupData>(jsonContent)
        } catch (e: Exception) {
            throw e
        }

        backupData.settings?.let { settings ->
            val filteredSettings = mutableMapOf<String, String>()
            categories.forEach { category ->
                val keys = getKeysForCategory(category)
                keys.forEach { key ->
                    settings[key]?.let { filteredSettings[key] = it }
                }
            }
            if (filteredSettings.isNotEmpty()) {
                settingsManager.importPreferences(filteredSettings)
            }
        }

        if (categories.contains(BackupCategory.PROFILES) && backupData.profiles != null) {
            profileDao.deleteAllProfiles()
            profileDao.insertProfiles(backupData.profiles)
        }

        if (categories.contains(BackupCategory.RECENT_CONNECTIONS) && backupData.recentConnections != null) {
            recentConnectionDao.clearHistory()
            recentConnectionDao.insertRecentConnections(backupData.recentConnections)
        }
    }

    private fun getKeysForCategory(category: BackupCategory): List<String> {
        return when (category) {
            BackupCategory.GENERAL_SETTINGS -> listOf(
                "kill_switch", "auto_connect", "notifications", "app_theme", "server_load_display_mode"
            )
            BackupCategory.OBFUSCATION -> listOf(
                "obfuscation_enabled", "obfuscation_advanced_mode", "custom_profiles",
                "awg_jc", "awg_jmin", "awg_jmax", "awg_s1", "awg_s2", "awg_s3", "awg_s4",
                "awg_h1", "awg_h2", "awg_h3", "awg_h4", "awg_i1", "awg_i2", "awg_i3", "awg_i4", "awg_i5",
                "awg_junk_level"
            )
            BackupCategory.API_BYPASS -> listOf(
                "api_bypass_enabled", "api_bypass_strategy", "byedpi_flags", "byedpi_sni",
                "api_proxy_host", "api_proxy_port", "api_proxy_type", "api_proxy_username", "api_proxy_password"
            )
            BackupCategory.PROFILES -> listOf("selected_profile_id")
            BackupCategory.RECENT_CONNECTIONS -> emptyList() // Handled via DB
            BackupCategory.QUICK_CONNECT -> listOf("quick_connect_strategy", "quick_connect_target_id")
            BackupCategory.SPLIT_TUNNELING -> listOf(
                "split_tunneling_enabled", "split_tunneling_mode", "excluded_apps", "excluded_ips", "excluded_domains",
                "st_show_system_apps"
            )
            BackupCategory.VPN_PORT -> listOf("vpn_port")
            BackupCategory.DNS -> listOf("custom_dns")
            BackupCategory.SPOOF_COUNTRY -> listOf("spoof_country_enabled", "spoof_country_null", "spoof_country_code")
            BackupCategory.OTA_UPDATES -> listOf("ota_update_frequency", "ota_update_channel")
            BackupCategory.TRUSTED_WIFI -> listOf("trusted_wifi_networks", "auto_connect_on_untrusted")
            BackupCategory.SENTRY_ANALYTICS -> listOf(
                "analytics_enabled", "crash_reports_enabled", "sentry_performance_enabled", "sentry_non_fatal_enabled",
                "sentry_session_replay_enabled", "sentry_anr_enabled", "sentry_metrics_enabled", "sentry_logs_enabled"
            )
        }
    }
}
