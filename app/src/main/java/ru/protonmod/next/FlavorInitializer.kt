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
        val isCrashReportsEnabled = runBlocking { settingsManager.crashReportsEnabled.first() }
        val isAnalyticsEnabled = runBlocking { settingsManager.analyticsEnabled.first() }

        // Sentry initialization
        SentryAndroid.init(context) { options ->
            options.dsn = "https://7b74cef88678ecb3e6047ac6b4abf139@o4510986952310784.ingest.de.sentry.io/4510986956374096"
            
            // Initial state based on settings
            options.isEnabled = isCrashReportsEnabled

            // Filter out system spam from breadcrumbs to keep logs clean
            options.setBeforeBreadcrumb { breadcrumb, _ ->
                val category = breadcrumb.category ?: ""
                val message = breadcrumb.message ?: ""
                
                // List of noisy tags/categories that usually don't help with app-level debugging
                val spammyTags = listOf(
                    "chromium", 
                    "mesa", 
                    "GoLog", 
                    "EGL_emulation", 
                    "Vulkan", 
                    "skia", 
                    "libEGL", 
                    "OpenGLRenderer",
                    "SceneHelper",
                    "SurfaceFilters",
                    "InputMethodManager",
                    "ViewRootImpl",
                    "Choreographer"
                )

                val isSpam = spammyTags.any { tag -> 
                    category.contains(tag.trim(), ignoreCase = true) || 
                    message.contains(tag.trim(), ignoreCase = true)
                }

                if (isSpam) null else breadcrumb
            }

            // Only send if user enabled crash reporting
            options.setBeforeSend { event, _ ->
                val currentCrashEnabled = runBlocking { settingsManager.crashReportsEnabled.first() }
                if (!currentCrashEnabled) return@setBeforeSend null
                
                // On Business plan during alpha, we send all events (FATAL, ERROR, WARNING, INFO, DEBUG)
                // to catch as many issues as possible.
                event
            }

            // Performance monitoring (Analytics)
            options.tracesSampleRate = if (isAnalyticsEnabled) 1.0 else 0.0 // 100% for alpha/business plan
            
            // Collect more context for errors
            options.isEnableAutoSessionTracking = isAnalyticsEnabled
            options.isAnrEnabled = true
            
            // Collect breadcrumbs for user interactions to help debugging
            options.isEnableUserInteractionBreadcrumbs = true
            options.isEnableAppLifecycleBreadcrumbs = true
        }
    }
}
