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

/**
 * Common initializer for the application.
 * Replaces flavor-specific initializers (dev, google, foss).
 * Now crash reporting and analytics are controlled by user settings.
 */
object FlavorInitializer {
    fun initialize(context: Context) {
        // Read settings synchronously for app startup to avoid ANR
        val settingsManager = SettingsManager(context)
        val isAnalyticsEnabled = settingsManager.isAnalyticsEnabledSync()
        val isPerformanceEnabled = settingsManager.isPerformanceEnabledSync()
        val isSessionReplayEnabled = settingsManager.isSessionReplayEnabledSync()
        val isAnrEnabled = settingsManager.isAnrEnabledSync()
        val isMetricsEnabled = settingsManager.isMetricsEnabledSync()
        val isLogsEnabled = settingsManager.isLogsEnabledSync()

        // Sentry initialization
        SentryAndroid.init(context) { options ->
            options.dsn = "https://7b74cef88678ecb3e6047ac6b4abf139@o4510986952310784.ingest.de.sentry.io/4510986956374096"
            
            // Allow all errors if crash reporting is enabled
            options.setBeforeSend { event, _ ->
                val currentCrashEnabled = settingsManager.isCrashReportsEnabledSync()
                if (!currentCrashEnabled) null else event
            }

            // Utilize 100M Spans and 6K Profile Hours quota when analytics is on
            options.tracesSampleRate = if (isPerformanceEnabled) 1.0 else 0.0
            options.profilesSampleRate = if (isPerformanceEnabled) 1.0 else 0.0
            
            options.isEnableAutoSessionTracking = isAnalyticsEnabled
            options.isAnrEnabled = isAnrEnabled
            // App Start Profiling is disabled to prevent ANR on startup. 
            // It triggers method tracing which can hang the main thread on some devices.
            options.isEnableAppStartProfiling = false
            options.isEnableUserInteractionTracing = isAnalyticsEnabled
            
            // Measure what matters with Metrics (v8.30.0+)
            // Track application health with numeric data like counters and gauges
            options.metrics.isEnabled = isMetricsEnabled

            // Enable structured Logs (v8.12.0+)
            // All ProtonLogger calls will be forwarded to Sentry Logs for real-time querying
            options.logs.isEnabled = isLogsEnabled
            
            // Advanced Debugging (Attachments & Screenshots, 10 GB quota)
            options.isAttachScreenshot = isAnalyticsEnabled
            options.isAttachViewHierarchy = isAnalyticsEnabled
            
            // Session Replay (100K replays quota)
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
