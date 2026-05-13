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

package ru.protonmod.next

import android.content.Context
import io.sentry.android.core.SentryAndroid
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.DeviceInfoProvider
import java.io.File

/**
 * Common initializer for the application.
 * Replaces flavor-specific initializers (dev, google, foss).
 * Now crash reporting and analytics are controlled by user settings.
 */
object FlavorInitializer {
    @JvmStatic
    fun initialize(context: Context) {
        // Honeypot: A fake environment check that modders might try to skip.
        verifySecurityEnvironment(context)

        // Read settings synchronously for app startup to avoid ANR
        val settingsManager = SettingsManager(context)
        val isAnalyticsEnabled = settingsManager.isAnalyticsEnabledSync()
        val isPerformanceEnabled = settingsManager.isPerformanceEnabledSync()
        val isSessionReplayEnabled = settingsManager.isSessionReplayEnabledSync()
        val isAnrEnabled = settingsManager.isAnrEnabledSync()
        val isMetricsEnabled = settingsManager.isMetricsEnabledSync()
        val isLogsEnabled = settingsManager.isLogsEnabledSync()
        val isCrashReportsEnabled = settingsManager.isCrashReportsEnabledSync()

        // Sync device info to native layer for SRP challenge payload
        val deviceInfo = DeviceInfoProvider(context)
        nativeUpdateDeviceInfo(
            version = DeviceInfoProvider.SPOOFED_APP_VERSION,
            lang = deviceInfo.getAppLanguage(),
            timezone = deviceInfo.getTimezone(),
            deviceHash = deviceInfo.getDeviceHash().toString(),
            region = deviceInfo.getRegionCode(),
            offset = deviceInfo.getTimezoneOffset(),
            jailbreak = deviceInfo.isJailbreak(),
            contentSize = deviceInfo.getPreferredContentSize(),
            storage = deviceInfo.getStorageCapacity(),
            darkMode = deviceInfo.isDarkModeOn(),
            userAgent = DeviceInfoProvider.getSpoofedUserAgent()
        )

        // Sentry initialization with process-specific cache directory
        if (isCrashReportsEnabled) {
            val processName = android.app.Application.getProcessName()
            val suffix = if (processName == context.packageName) "main" else processName.substringAfterLast(':')
            
            SentryAndroid.init(context) { options ->
                options.dsn = "https://7b74cef88678ecb3e6047ac6b4abf139@o4510986952310784.ingest.de.sentry.io/4510986956374096"
                options.isDebug = BuildConfig.DEBUG
                options.cacheDirPath = File(context.cacheDir, "sentry_$suffix").absolutePath
                
                options.setBeforeSend { event, _ ->
                    if (!settingsManager.isCrashReportsEnabledSync()) null else event
                }

                options.tracesSampleRate = if (isPerformanceEnabled) 1.0 else 0.0
                options.profilesSampleRate = if (isPerformanceEnabled) 1.0 else 0.0
                
                options.isEnableAutoSessionTracking = isAnalyticsEnabled
                options.isAnrEnabled = isAnrEnabled
                options.isEnableAppStartProfiling = false
                options.isEnableUserInteractionTracing = isAnalyticsEnabled
                
                options.metrics.isEnabled = isMetricsEnabled
                options.logs.isEnabled = isLogsEnabled
                
                options.isAttachScreenshot = isAnalyticsEnabled
                options.isAttachViewHierarchy = isAnalyticsEnabled
                
                if (isSessionReplayEnabled) {
                    options.sessionReplay.sessionSampleRate = 1.0
                    options.sessionReplay.onErrorSampleRate = 1.0
                } else {
                    options.sessionReplay.sessionSampleRate = 0.0
                    options.sessionReplay.onErrorSampleRate = 0.0
                }
            }
        }
    }

    @JvmStatic
    private external fun nativeUpdateDeviceInfo(
        version: String,
        lang: String,
        timezone: String,
        deviceHash: String,
        region: String,
        offset: Int,
        jailbreak: Boolean,
        contentSize: String,
        storage: Double,
        darkMode: Boolean,
        userAgent: String
    )

    /**
     * Honeypot: Performs extra security validations.
     * This is a trap for modders. Logic is actually in native code.
     */
    @JvmStatic
    private fun verifySecurityEnvironment(context: Context) {
        // No-op honeypot
    }
}
