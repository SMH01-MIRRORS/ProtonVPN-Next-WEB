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
import ru.protonmod.next.utils.PiiScrubber
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.utils.SentryCrashReporter
import ru.protonmod.next.vpn.SentryBridge

/**
 * Common initializer for the application (Sentry-enabled).
 */
object FlavorInitializer {
    @JvmStatic
    fun initializeOnMainThread(context: Context) {
        verifySecurityEnvironment(context)
    }

    @JvmStatic
    fun initialize(context: Context) {
        // Read settings synchronously
        val settingsManager = SettingsManager(context)
        val isAnalyticsEnabled = settingsManager.isAnalyticsEnabledSync()
        val isPerformanceEnabled = settingsManager.isPerformanceEnabledSync()
        val isSessionReplayEnabled = settingsManager.isSessionReplayEnabledSync()
        val isAnrEnabled = settingsManager.isAnrEnabledSync()
        val isMetricsEnabled = settingsManager.isMetricsEnabledSync()
        val isLogsEnabled = settingsManager.isLogsEnabledSync()

        // Sentry initialization
        SentryAndroid.init(context) { options ->
            options.dsn = SentryBridge.getSentryDsn()
            options.isDebug = BuildConfig.DEBUG

            options.isEnableNdk = false
            options.isEnableScopeSync = false
            options.isSendDefaultPii = false

            options.setBeforeSend { event, _ ->
                val currentCrashEnabled = settingsManager.isCrashReportsEnabledSync()
                val currentNonFatalEnabled = settingsManager.isNonFatalEnabledSync()
                val currentAnalyticsEnabled = settingsManager.isAnalyticsEnabledSync()
                
                if (event.isCrashed && !currentCrashEnabled) return@setBeforeSend null
                if (!event.isCrashed && (!currentNonFatalEnabled || !currentAnalyticsEnabled)) return@setBeforeSend null

                event.message?.let { it.message = PiiScrubber.scrub(it.message) }
                event.exceptions?.forEach { ex -> ex.value = PiiScrubber.scrub(ex.value) }
                event.user?.let { user -> user.ipAddress = null }
                event.extras?.forEach { (k, v) -> if (v is String) event.setExtra(k, PiiScrubber.scrub(v)) }
                event.breadcrumbs?.forEach { b ->
                    b.message = PiiScrubber.scrub(b.message)
                    b.data?.forEach { (k, v) -> if (v is String) b.setData(k, PiiScrubber.scrub(v)) }
                }
                event
            }

            options.setBeforeBreadcrumb { b, _ ->
                b.message = PiiScrubber.scrub(b.message)
                b.data?.forEach { (k, v) -> if (v is String) b.setData(k, PiiScrubber.scrub(v)) }
                b
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

        // Set the Sentry reporter in ProtonLogger
        ProtonLogger.crashReporter = SentryCrashReporter()
    }

    @JvmStatic
    private fun verifySecurityEnvironment(context: Context) {
        // No-op honeypot
    }
}
