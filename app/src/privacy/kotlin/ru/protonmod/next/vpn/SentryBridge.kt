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

import ru.protonmod.next.utils.ProtonLogger

/**
 * A no-op bridge for privacy-first flavors.
 * Ensures native security events are still handled (logged) but not sent to Sentry.
 */
object SentryBridge {
    init {
        System.loadLibrary("next")
    }

    /**
     * No-op in privacy flavor.
     */
    fun getSentryDsn(): String = ""

    /**
     * Only logs the security event to local logcat in privacy flavor.
     */
    @JvmStatic
    fun reportSecurityEvent(event: String) {
        ProtonLogger.e("Security", "[PRIVACY] Security event: $event")
    }

    /**
     * Terminates the process without flushing Sentry.
     */
    @JvmStatic
    fun flushAndTerminate() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * Only logs the message to local logcat in privacy flavor.
     */
    @JvmStatic
    fun reportLog(level: Int, tag: String, message: String) {
        when (level) {
            2 -> ProtonLogger.d(tag, message)
            3 -> ProtonLogger.i(tag, message)
            4 -> ProtonLogger.w(tag, message)
            5 -> ProtonLogger.e(tag, message)
            6 -> ProtonLogger.e(tag, "[FATAL] $message")
            else -> ProtonLogger.i(tag, message)
        }
    }

    private external fun getSentryDsnNative(): String
}
