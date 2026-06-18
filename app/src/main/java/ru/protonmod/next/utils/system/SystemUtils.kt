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

package ru.protonmod.next.utils.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object SystemUtils {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val standardCheck = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        if (isXiaomi()) {
            val miuiLevel = getMiuiBatterySaverLevel(context)
            // Level 0 means "No restrictions" in MIUI
            if (miuiLevel == 0) return true
        }

        return standardCheck
    }

    private fun isXiaomi(): Boolean {
        return Build.MANUFACTURER.contains("Xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.contains("Redmi", ignoreCase = true) ||
                Build.MANUFACTURER.contains("POCO", ignoreCase = true)
    }

    fun isNothingDevice(): Boolean {
        return Build.MANUFACTURER.contains("Nothing", ignoreCase = true)
    }

    private fun getMiuiBatterySaverLevel(context: Context): Int {
        return try {
            val processManager = Class.forName("miui.process.ProcessManager")
            val getPolicyMethod = processManager.getMethod("getAppBatterySaverPolicy", String::class.java)
            getPolicyMethod.invoke(null, context.packageName) as Int
        } catch (e: Exception) {
            -1
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (isXiaomi()) {
            try {
                val intent = Intent()
                intent.component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                intent.putExtra("package_name", context.packageName)
                intent.putExtra("package_label", context.getString(context.applicationInfo.labelRes))
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to standard settings if MIUI activity is not found
            }
        }

        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }
}
