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

package ru.protonmod.next.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ru.protonmod.next.utils.ProtonLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import ru.protonmod.next.data.model.ObfuscationProfile
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("boot_settings", Context.MODE_PRIVATE)

    companion object {
        private val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications")

        private val APP_THEME = stringPreferencesKey("app_theme")
        private val SERVER_LOAD_DISPLAY_MODE = stringPreferencesKey("server_load_display_mode")

        private val SPLIT_TUNNELING_ENABLED = booleanPreferencesKey("split_tunneling_enabled")
        private val SPLIT_TUNNELING_MODE = stringPreferencesKey("split_tunneling_mode") // "exclude" or "include"
        private val EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")
        private val EXCLUDED_IPS = stringSetPreferencesKey("excluded_ips")
        private val EXCLUDED_DOMAINS = stringSetPreferencesKey("excluded_domains")

        private val VPN_PORT = intPreferencesKey("vpn_port")

        // Custom DNS IP setting (IPv4 or IPv6)
        private val CUSTOM_DNS = stringPreferencesKey("custom_dns")

        // API Bypass Settings
        private val API_BYPASS_ENABLED = booleanPreferencesKey("api_bypass_enabled")
        private val API_BYPASS_STRATEGY = stringPreferencesKey("api_bypass_strategy")

        private val OBFUSCATION_ENABLED = booleanPreferencesKey("obfuscation_enabled")
        private val OBFUSCATION_ADVANCED_MODE = booleanPreferencesKey("obfuscation_advanced_mode")
        private val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        private val CUSTOM_PROFILES = stringPreferencesKey("custom_profiles")

        private val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        private val CRASH_REPORTS_ENABLED = booleanPreferencesKey("crash_reports_enabled")
        private val SENTRY_PERFORMANCE_ENABLED = booleanPreferencesKey("sentry_performance_enabled")
        private val SENTRY_NON_FATAL_ENABLED = booleanPreferencesKey("sentry_non_fatal_enabled")
        private val SENTRY_SESSION_REPLAY_ENABLED = booleanPreferencesKey("sentry_session_replay_enabled")
        private val SENTRY_ANR_ENABLED = booleanPreferencesKey("sentry_anr_enabled")

        private val QUICK_CONNECT_STRATEGY = stringPreferencesKey("quick_connect_strategy") // "fastest", "recent", "profile"
        private val QUICK_CONNECT_TARGET_ID = stringPreferencesKey("quick_connect_target_id")

        private val AWG_JC = intPreferencesKey("awg_jc")
        private val AWG_JMIN = intPreferencesKey("awg_jmin")
        private val AWG_JMAX = intPreferencesKey("awg_jmax")
        private val AWG_S1 = intPreferencesKey("awg_s1")
        private val AWG_S2 = intPreferencesKey("awg_s2")
        private val AWG_S3 = intPreferencesKey("awg_s3") // cookieReplyPacketJunkSize
        private val AWG_S4 = intPreferencesKey("awg_s4") // transportPacketJunkSize
        private val AWG_H1 = stringPreferencesKey("awg_h1")
        private val AWG_H2 = stringPreferencesKey("awg_h2")
        private val AWG_H3 = stringPreferencesKey("awg_h3")
        private val AWG_H4 = stringPreferencesKey("awg_h4")
        private val AWG_I1 = stringPreferencesKey("awg_i1")
        private val AWG_I2 = stringPreferencesKey("awg_i2")
        private val AWG_I3 = stringPreferencesKey("awg_i3")
        private val AWG_I4 = stringPreferencesKey("awg_i4")
        private val AWG_I5 = stringPreferencesKey("awg_i5")
        private val AWG_JUNK_LEVEL = intPreferencesKey("awg_junk_level")

        const val DEFAULT_I1 = "<b 0xce000000010897a297ecc34cd6dd000044d0ec2e2e1ea2991f467ace4222129b5a098823784694b4897b9986ae0b7280135fa85e196d9ad980b150122129ce2a9379531b0fd3e871ca5fdb883c369832f730e272d7b8b74f393f9f0fa43f11e510ecb2219a52984410c204cf875585340c62238e14ad04dff382f2c200e0ee22fe743b9c6b8b043121c5710ec289f471c91ee414fca8b8be8419ae8ce7ffc53837f6ade262891895f3f4cecd31bc93ac5599e18e4f01b472362b8056c3172b513051f8322d1062997ef4a383b01706598d08d48c221d30e74c7ce000cdad36b706b1bf9b0607c32ec4b3203a4ee21ab64df336212b9758280803fcab14933b0e7ee1e04a7becce3e2633f4852585c567894a5f9efe9706a151b615856647e8b7dba69ab357b3982f554549bef9256111b2d67afde0b496f16962d4957ff654232aa9e845b61463908309cfd9de0a6abf5f425f577d7e5f6440652aa8da5f73588e82e9470f3b21b27b28c649506ae1a7f5f15b876f56abc4615f49911549b9bb39dd804fde182bd2dcec0c33bad9b138ca07d4a4a1650a2c2686acea05727e2a78962a840ae428f55627516e73c83dd8893b02358e81b524b4d99fda6df52b3a8d7a5291326e7ac9d773c5b43b8444554ef5aea104a738ed650aa979674bbed38da58ac29d87c29d387d80b526065baeb073ce65f075ccb56e47533aef357dceaa8293a523c5f6f790be90e4731123d3c6152a70576e90b4ab5bc5ead01576c68ab633ff7d36dcde2a0b2c68897e1acfc4d6483aaaeb635dd63c96b2b6a7a2bfe042f6aed82e5363aa850aace12ee3b1a93f30d8ab9537df483152a5527faca21efc9981b304f11fc95336f5b9637b174c5a0659e2b22e159a9fed4b8e93047371175b1d6d9cc8ab745f3b2281537d1c75fb9451871864efa5d184c38c185fd203de206751b92620f7c369e031d2041e152040920ac2c5ab5340bfc9d0561176abf10a147287ea90758575ac6a9f5ac9f390d0d5b23ee12af583383d994e22c0cf42383834bcd3ada1b3825a0664d8f3fb678261d57601ddf94a8a68a7c273a18c08aa99c7ad8c6c42eab67718843597ec9930457359dfdfbce024afc2dcf9348579a57d8d3490b2fa99f278f1c37d87dad9b221acd575192ffae1784f8e60ec7cee4068b6b988f0433d96d6a1b1865f4e155e9fe020279f434f3bf1bd117b717b92f6cd1cc9bea7d45978bcc3f24bda631a36910110a6ec06da35f8966c9279d130347594f13e9e07514fa370754d1424c0a1545c5070ef9fb2acd14233e8a50bfc5978b5bdf8bc1714731f798d21e2004117c61f2989dd44f0cf027b27d4019e81ed4b5c31db347c4a3a4d85048d7093cf16753d7b0d15e078f5c7a5205dc2f87e330a1f716738dce1c6180e9d02869b5546f1c4d2748f8c90d9693cba4e0079297d22fd61402dea32ff0eb69ebd65a5d0b687d87e3a8b2c42b648aa723c7c7daf37abcc4bb85caea2ee8f55bec20e913b3324ab8f5c3304f820d42ad1b9f2ffc1a3af9927136b4419e1e579ab4c2ae3c776d293d397d575df181e6cae0a4ada5d67ecea171cca3288d57c7bbdaee3befe745fb7d634f70386d873b90c4d6c6596bb65af68f9e5121e67ebf0d89d3c909ceedfb32ce9575a7758ff080724e1ab5d5f43074ecb53a479af21ed03d7b6899c36631c0166f9d47e5e1d4528a5d3d3f744029c4b1c190cbfbad06f5f83f7ad0429fa9a2719c56ffe3783460e166de2d8>"
    }

    val killSwitchEnabled: Flow<Boolean> = context.dataStore.data.map { it[KILL_SWITCH] ?: false }
    val autoConnectEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CONNECT] ?: true }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS] ?: true }

    val appTheme: Flow<ru.protonmod.next.ui.theme.AppTheme> = context.dataStore.data.map { preferences ->
        val themeString = preferences[APP_THEME] ?: ru.protonmod.next.ui.theme.AppTheme.DARK.name
        try {
            ru.protonmod.next.ui.theme.AppTheme.valueOf(themeString)
        } catch (e: Exception) {
            ru.protonmod.next.ui.theme.AppTheme.DARK
        }
    }

    val serverLoadDisplayMode: Flow<ServerLoadDisplayMode> = context.dataStore.data.map { preferences ->
        val modeString = preferences[SERVER_LOAD_DISPLAY_MODE] ?: ServerLoadDisplayMode.ALL.name
        try {
            ServerLoadDisplayMode.valueOf(modeString)
        } catch (e: Exception) {
            ServerLoadDisplayMode.ALL
        }
    }

    val splitTunnelingEnabled: Flow<Boolean> = context.dataStore.data.map { it[SPLIT_TUNNELING_ENABLED] ?: false }
    val splitTunnelingMode: Flow<String> = context.dataStore.data.map { it[SPLIT_TUNNELING_MODE] ?: "exclude" }
    val excludedApps: Flow<Set<String>> = context.dataStore.data.map { it[EXCLUDED_APPS] ?: emptySet() }
    val excludedIps: Flow<Set<String>> = context.dataStore.data.map { it[EXCLUDED_IPS] ?: emptySet() }
    val excludedDomains: Flow<Set<String>> = context.dataStore.data.map { it[EXCLUDED_DOMAINS] ?: emptySet() }

    val vpnPort: Flow<Int> = context.dataStore.data.map { it[VPN_PORT] ?: 1194 }
    val customDns: Flow<String> = context.dataStore.data.map { it[CUSTOM_DNS] ?: "" }

    val apiBypassEnabled: Flow<Boolean> = context.dataStore.data.map { it[API_BYPASS_ENABLED] ?: false }
    val apiBypassStrategy: Flow<String> = context.dataStore.data.map { it[API_BYPASS_STRATEGY] ?: "netlify" }

    val obfuscationEnabled: Flow<Boolean> = context.dataStore.data.map { it[OBFUSCATION_ENABLED] ?: false }
    val obfuscationAdvancedMode: Flow<Boolean> = context.dataStore.data.map { it[OBFUSCATION_ADVANCED_MODE] ?: false }
    val selectedProfileId: Flow<String> = context.dataStore.data.map { it[SELECTED_PROFILE_ID] ?: "standard_1" }

    val analyticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[ANALYTICS_ENABLED] ?: true }
    val crashReportsEnabled: Flow<Boolean> = context.dataStore.data.map { it[CRASH_REPORTS_ENABLED] ?: true }
    val sentryPerformanceEnabled: Flow<Boolean> = context.dataStore.data.map { it[SENTRY_PERFORMANCE_ENABLED] ?: true }
    val sentryNonFatalEnabled: Flow<Boolean> = context.dataStore.data.map { it[SENTRY_NON_FATAL_ENABLED] ?: true }
    val sentrySessionReplayEnabled: Flow<Boolean> = context.dataStore.data.map { it[SENTRY_SESSION_REPLAY_ENABLED] ?: true }
    val sentryAnrEnabled: Flow<Boolean> = context.dataStore.data.map { it[SENTRY_ANR_ENABLED] ?: true }

    /** Synchronous check for app startup initializers to avoid ANR from runBlocking */
    fun isAnalyticsEnabledSync(): Boolean = prefs.getBoolean("analytics_enabled", true)
    
    /** Synchronous check for app startup initializers to avoid ANR from runBlocking */
    fun isCrashReportsEnabledSync(): Boolean = prefs.getBoolean("crash_reports_enabled", true)

    fun isPerformanceEnabledSync(): Boolean = prefs.getBoolean("sentry_performance_enabled", true)
    fun isNonFatalEnabledSync(): Boolean = prefs.getBoolean("sentry_non_fatal_enabled", true)
    fun isSessionReplayEnabledSync(): Boolean = prefs.getBoolean("sentry_session_replay_enabled", true)
    fun isAnrEnabledSync(): Boolean = prefs.getBoolean("sentry_anr_enabled", true)

    val quickConnectStrategy: Flow<String> = context.dataStore.data.map { it[QUICK_CONNECT_STRATEGY] ?: "fastest" }
    val quickConnectTargetId: Flow<String?> = context.dataStore.data.map { it[QUICK_CONNECT_TARGET_ID] }

    val customProfiles: Flow<List<ObfuscationProfile>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[CUSTOM_PROFILES] ?: "[]"
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<ObfuscationProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ObfuscationProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        isReadOnly = obj.optBoolean("isReadOnly", false),
                        jc = obj.optInt("jc", 3),
                        jmin = obj.optInt("jmin", 1),
                        jmax = obj.optInt("jmax", 3),
                        s1 = obj.optInt("s1", 0),
                        s2 = obj.optInt("s2", 0),
                        s3 = obj.optInt("s3", 0),
                        s4 = obj.optInt("s4", 0),
                        h1 = obj.optString("h1", "1"),
                        h2 = obj.optString("h2", "2"),
                        h3 = obj.optString("h3", "3"),
                        h4 = obj.optString("h4", "4"),
                        i1 = obj.optString("i1", DEFAULT_I1),
                        i2 = obj.optString("i2", ""),
                        i3 = obj.optString("i3", ""),
                        i4 = obj.optString("i4", ""),
                        i5 = obj.optString("i5", ""),
                        junkLevel = obj.optInt("junkLevel", 3)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    val awgJc: Flow<Int> = context.dataStore.data.map { it[AWG_JC] ?: 3 }
    val awgJmin: Flow<Int> = context.dataStore.data.map { it[AWG_JMIN] ?: 1 }
    val awgJmax: Flow<Int> = context.dataStore.data.map { it[AWG_JMAX] ?: 3 }
    val awgS1: Flow<Int> = context.dataStore.data.map { it[AWG_S1] ?: 0 }
    val awgS2: Flow<Int> = context.dataStore.data.map { it[AWG_S2] ?: 0 }
    val awgS3: Flow<Int> = context.dataStore.data.map { it[AWG_S3] ?: 0 }
    val awgS4: Flow<Int> = context.dataStore.data.map { it[AWG_S4] ?: 0 }
    val awgH1: Flow<String> = context.dataStore.data.map { it[AWG_H1] ?: "1" }
    val awgH2: Flow<String> = context.dataStore.data.map { it[AWG_H2] ?: "2" }
    val awgH3: Flow<String> = context.dataStore.data.map { it[AWG_H3] ?: "3" }
    val awgH4: Flow<String> = context.dataStore.data.map { it[AWG_H4] ?: "4" }
    val awgI1: Flow<String> = context.dataStore.data.map { it[AWG_I1] ?: DEFAULT_I1 }
    val awgI2: Flow<String> = context.dataStore.data.map { it[AWG_I2] ?: "" }
    val awgI3: Flow<String> = context.dataStore.data.map { it[AWG_I3] ?: "" }
    val awgI4: Flow<String> = context.dataStore.data.map { it[AWG_I4] ?: "" }
    val awgI5: Flow<String> = context.dataStore.data.map { it[AWG_I5] ?: "" }
    val awgJunkLevel: Flow<Int> = context.dataStore.data.map { it[AWG_JUNK_LEVEL] ?: 0 }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.dataStore.edit { it[KILL_SWITCH] = enabled }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_CONNECT] = enabled }
    }

    suspend fun setNotifications(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    suspend fun setAppTheme(theme: ru.protonmod.next.ui.theme.AppTheme) {
        context.dataStore.edit { it[APP_THEME] = theme.name }
    }

    suspend fun setServerLoadDisplayMode(mode: ServerLoadDisplayMode) {
        context.dataStore.edit { it[SERVER_LOAD_DISPLAY_MODE] = mode.name }
    }

    suspend fun setSplitTunnelingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SPLIT_TUNNELING_ENABLED] = enabled }
    }

    suspend fun setSplitTunnelingMode(mode: String) {
        context.dataStore.edit { it[SPLIT_TUNNELING_MODE] = mode }
    }

    suspend fun setExcludedApps(apps: Set<String>) {
        context.dataStore.edit { it[EXCLUDED_APPS] = apps }
    }

    suspend fun setExcludedIps(ips: Set<String>) {
        context.dataStore.edit { it[EXCLUDED_IPS] = ips }
    }

    suspend fun setExcludedDomains(domains: Set<String>) {
        context.dataStore.edit { it[EXCLUDED_DOMAINS] = domains }
    }

    suspend fun setVpnPort(port: Int) {
        context.dataStore.edit { it[VPN_PORT] = port }
    }

    suspend fun setCustomDns(dnsIp: String) {
        context.dataStore.edit { it[CUSTOM_DNS] = dnsIp }
    }

    suspend fun setApiBypassEnabled(enabled: Boolean) {
        context.dataStore.edit { it[API_BYPASS_ENABLED] = enabled }
    }

    suspend fun setApiBypassStrategy(strategy: String) {
        context.dataStore.edit { it[API_BYPASS_STRATEGY] = strategy }
    }

    suspend fun setObfuscationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[OBFUSCATION_ENABLED] = enabled }
    }

    suspend fun setObfuscationAdvancedMode(enabled: Boolean) {
        context.dataStore.edit { it[OBFUSCATION_ADVANCED_MODE] = enabled }
    }

    suspend fun setSelectedProfileId(id: String) {
        context.dataStore.edit { it[SELECTED_PROFILE_ID] = id }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("analytics_enabled", enabled) }
        context.dataStore.edit { it[ANALYTICS_ENABLED] = enabled }
        ProtonLogger.isAnalyticsEnabled = enabled
    }

    suspend fun setCrashReportsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("crash_reports_enabled", enabled) }
        context.dataStore.edit { it[CRASH_REPORTS_ENABLED] = enabled }
    }

    suspend fun setSentryPerformanceEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_performance_enabled", enabled) }
        context.dataStore.edit { it[SENTRY_PERFORMANCE_ENABLED] = enabled }
    }

    suspend fun setSentryNonFatalEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_non_fatal_enabled", enabled) }
        context.dataStore.edit { it[SENTRY_NON_FATAL_ENABLED] = enabled }
        ProtonLogger.isNonFatalEnabled = enabled
    }

    suspend fun setSentrySessionReplayEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_session_replay_enabled", enabled) }
        context.dataStore.edit { it[SENTRY_SESSION_REPLAY_ENABLED] = enabled }
    }

    suspend fun setSentryAnrEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("sentry_anr_enabled", enabled) }
        context.dataStore.edit { it[SENTRY_ANR_ENABLED] = enabled }
    }

    suspend fun setQuickConnectStrategy(strategy: String, targetId: String? = null) {
        context.dataStore.edit { 
            it[QUICK_CONNECT_STRATEGY] = strategy
            if (targetId != null) {
                it[QUICK_CONNECT_TARGET_ID] = targetId
            } else {
                it.remove(QUICK_CONNECT_TARGET_ID)
            }
        }
    }

    suspend fun saveCustomProfiles(profiles: List<ObfuscationProfile>) {
        val array = JSONArray()
        profiles.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("isReadOnly", p.isReadOnly)
                put("jc", p.jc)
                put("jmin", p.jmin)
                put("jmax", p.jmax)
                put("s1", p.s1)
                put("s2", p.s2)
                put("s3", p.s3)
                put("s4", p.s4)
                put("h1", p.h1)
                put("h2", p.h2)
                put("h3", p.h3)
                put("h4", p.h4)
                put("i1", p.i1)
                put("i2", p.i2)
                put("i3", p.i3)
                put("i4", p.i4)
                put("i5", p.i5)
                put("junkLevel", p.junkLevel)
            }
            array.put(obj)
        }
        context.dataStore.edit { it[CUSTOM_PROFILES] = array.toString() }
    }

    suspend fun setAwgParams(
        jc: Int, jmin: Int, jmax: Int, s1: Int, s2: Int, s3: Int = 0, s4: Int = 0,
        h1: String, h2: String, h3: String, h4: String,
        i1: String, i2: String = "", i3: String = "", i4: String = "", i5: String = "",
        junkLevel: Int = 3
    ) {
        context.dataStore.edit {
            it[AWG_JC] = jc
            it[AWG_JMIN] = jmin
            it[AWG_JMAX] = jmax
            it[AWG_S1] = s1
            it[AWG_S2] = s2
            it[AWG_S3] = s3
            it[AWG_S4] = s4
            it[AWG_H1] = h1
            it[AWG_H2] = h2
            it[AWG_H3] = h3
            it[AWG_H4] = h4
            it[AWG_I1] = i1
            it[AWG_I2] = i2
            it[AWG_I3] = i3
            it[AWG_I4] = i4
            it[AWG_I5] = i5
            it[AWG_JUNK_LEVEL] = junkLevel
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
