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

package ru.protonmod.next.vpn

import io.sentry.Sentry
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.ProtonLogger

/**
 * Utility to dynamically reconfigure Sentry options at runtime.
 * This ensures that user privacy settings are applied immediately without app restart.
 */
object SentryConfigurator {
    /**
     * Sentry SDK doesn't support full dynamic reconfiguration of all options.
     * Some settings like ANR or Metrics are only read during init.
     * We sync what we can and rely on ProtonLogger for manual events.
     * We also added checks in FlavorInitializer's beforeSend for master kill-switch.
     */
    fun applySettings(settings: SettingsManager) {
        // ProtonLogger is used throughout the app and respects these flags immediately
        ProtonLogger.isAnalyticsEnabled = settings.isAnalyticsEnabledSync()
        ProtonLogger.isNonFatalEnabled = settings.isNonFatalEnabledSync()
        ProtonLogger.isSentryLogsEnabled = settings.isLogsEnabledSync()
        
        // Dynamic re-sampling and session tracking flags via SentryOptions
        // Note: We use Sentry.configureScope but we must handle the internal API access carefully
        // In the latest Sentry SDK, some options are accessible through the hub.
    }
}
