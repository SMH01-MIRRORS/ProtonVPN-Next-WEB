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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.protonmod.next.data.local.SettingsManager

/**
 * Common initializer for the application.
 * Replaces flavor-specific initializers (dev, google, foss).
 * Now crash reporting and analytics are controlled by user settings.
 */
object FlavorInitializer {
    fun initialize(context: Context) {
        // Read settings synchronously for app startup
        val settingsManager = SettingsManager(context)
        val isAnalyticsEnabled = runBlocking { settingsManager.analyticsEnabled.first() }

        // Sentry initialization
        SentryAndroid.init(context) { options ->
            options.dsn = "https://7b74cef88678ecb3e6047ac6b4abf139@o4510986952310784.ingest.de.sentry.io/4510986956374096"
            
            // Allow all errors if crash reporting is enabled
            options.setBeforeSend { event, _ ->
                val currentCrashEnabled = runBlocking { settingsManager.crashReportsEnabled.first() }
                if (!currentCrashEnabled) null else event
            }

            // Utilize 100M Spans and 6K Profile Hours quota when analytics is on
            options.tracesSampleRate = if (isAnalyticsEnabled) 1.0 else 0.0
            options.profilesSampleRate = if (isAnalyticsEnabled) 1.0 else 0.0
            
            options.isEnableAutoSessionTracking = isAnalyticsEnabled
            options.isAnrEnabled = true
            options.isEnableAppStartProfiling = true
            options.isEnableUserInteractionTracing = true
            
            // Advanced Debugging (Attachments & Screenshots, 10 GB quota)
            options.isAttachScreenshot = true
            options.isAttachViewHierarchy = true
            
            // Session Replay (100K replays quota)
            if (isAnalyticsEnabled) {
                options.sessionReplay.sessionSampleRate = 1.0
                options.sessionReplay.onErrorSampleRate = 1.0
            }
        }
    }
}
