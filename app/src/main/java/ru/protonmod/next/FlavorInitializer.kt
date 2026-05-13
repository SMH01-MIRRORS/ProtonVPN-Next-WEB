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
import ru.protonmod.next.data.local.SettingsManager
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
        val isCrashReportsEnabled = settingsManager.isCrashReportsEnabledSync()

        // Sentry Native initialization (Unified reporting)
        if (isCrashReportsEnabled) {
            val processName = android.app.Application.getProcessName()
            val suffix = if (processName == context.packageName) "main" else processName.substringAfterLast(':')
            val sentryCacheDir = File(context.cacheDir, "sentry_$suffix")
            if (!sentryCacheDir.exists()) sentryCacheDir.mkdirs()

            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: "unknown"
            val versionCode = packageInfo.longVersionCode.toInt()

            nativeInitSentry(
                sentryCacheDir.absolutePath,
                BuildConfig.DEBUG,
                versionName,
                versionCode
            )
        }
    }

    @JvmStatic
    private external fun nativeInitSentry(cacheDir: String, debug: Boolean, versionName: String, versionCode: Int)

    /**
     * Honeypot: Performs extra security validations.
     * This is a trap for modders. Logic is actually in native code.
     */
    @JvmStatic
    private fun verifySecurityEnvironment(context: Context) {
        // No-op honeypot
    }
}
