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

package ru.protonmod.next.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.protonmod.next.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * A professional logging wrapper for Proton VPN-Next.
 *
 * Automatically handles debug/release logic, tag generation from stack trace,
 * and integrates with Sentry (if available) for remote diagnostics.
 */
object ProtonLogger {

    private const val DEFAULT_TAG = "ProtonVPN"
    private const val CALL_STACK_INDEX = 4

    /**
     * Minimum interval (ms) between breadcrumbs with the same category+message prefix.
     */
    private const val BREADCRUMB_RATE_LIMIT_MS = 1_000L

    private val breadcrumbLastEmitted = ConcurrentHashMap<String, Long>()
    private val sentryLogLastEmitted = ConcurrentHashMap<String, Long>()

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Controlled by SettingsManager at runtime/startup */
    var isNonFatalEnabled: Boolean = true
    /** Controlled by SettingsManager at runtime/startup */
    var isAnalyticsEnabled: Boolean = true
    /** Controlled by SettingsManager at runtime/startup */
    var isSentryLogsEnabled: Boolean = true
    /** Global toggle for logcat output. Can be overridden in release builds. */
    var isLogcatEnabled: Boolean = BuildConfig.DEBUG

    /** Abstracted reporter for crashlytics/analytics */
    var crashReporter: CrashReporter? = null

    /** Log at VERBOSE level */
    fun v(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.v(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, "DEBUG")
        addSentryLog(finalTag, decoratedMsg, "DEBUG", throwable)
    }

    /** Log at VERBOSE level with a lazy message lambda */
    inline fun v(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.v(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, "DEBUG")
        addSentryLog(finalTag, decoratedMsg, "DEBUG", throwable)
    }

    /** Log at DEBUG level */
    fun d(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.d(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, "DEBUG")
        addSentryLog(finalTag, decoratedMsg, "DEBUG", throwable)
    }

    /** Log at DEBUG level with a lazy message lambda */
    inline fun d(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.d(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, "DEBUG")
        addSentryLog(finalTag, decoratedMsg, "DEBUG", throwable)
    }

    /** Log at INFO level */
    fun i(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.i(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, "INFO")
        addSentryLog(finalTag, decoratedMsg, "INFO", throwable)
    }

    /** Log at INFO level with a lazy message lambda */
    inline fun i(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.i(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, "INFO")
        addSentryLog(finalTag, decoratedMsg, "INFO", throwable)
    }

    /** Log at WARN level */
    fun w(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.w(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, "WARNING")
        addSentryLog(finalTag, decoratedMsg, "WARNING", throwable)
        if (throwable != null && isNonFatalEnabled) {
            crashReporter?.captureException(throwable)
        }
    }

    /** Log at WARN level with a lazy message lambda */
    inline fun w(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.w(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, "WARNING")
        addSentryLog(finalTag, decoratedMsg, "WARNING", throwable)
        if (throwable != null && isNonFatalEnabled) {
            crashReporter?.captureException(throwable)
        }
    }

    /** Log at ERROR level */
    fun e(tag: String? = null, message: String, throwable: Throwable? = null) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val decoratedMsg = "[$threadName] $message"
        if (isLogcatEnabled) {
            Log.e(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, message, "ERROR")
        addSentryLog(finalTag, decoratedMsg, "ERROR", throwable)
        if (isNonFatalEnabled) {
            if (throwable != null) {
                crashReporter?.captureException(throwable)
            } else {
                crashReporter?.captureMessage(PiiScrubber.scrub(message), "ERROR")
            }
        }
    }

    /** Log at ERROR level with a lazy message lambda */
    inline fun e(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        val finalTag = tag ?: getAutoTag()
        val threadName = Thread.currentThread().name
        val msg = message()
        val decoratedMsg = "[$threadName] $msg"
        if (isLogcatEnabled) {
            Log.e(finalTag, decoratedMsg, throwable)
        }
        addSentryBreadcrumb(finalTag, msg, "ERROR")
        addSentryLog(finalTag, decoratedMsg, "ERROR", throwable)
        if (isNonFatalEnabled) {
            if (throwable != null) {
                crashReporter?.captureException(throwable)
            } else {
                crashReporter?.captureMessage(PiiScrubber.scrub(msg), "ERROR")
            }
        }
    }

    /** Records a user action as a breadcrumb. */
    fun action(tag: String, message: String) {
        if (isLogcatEnabled) {
            Log.d(tag, "[ACTION] $message")
        }
        addSentryBreadcrumb(tag, message, "INFO", category = "ui.action")
    }

    /** Professional error logging that accepts a message and an optional throwable. */
    fun error(tag: String? = null, message: String, throwable: Throwable? = null) {
        e(tag, message, throwable)
    }

    @PublishedApi
    internal fun addSentryBreadcrumb(
        tag: String,
        message: String,
        level: String,
        category: String = "log.message"
    ) {
        if (!isAnalyticsEnabled) return

        val scrubbedMessage = PiiScrubber.scrub(message)
        val dedupKey = "$category:${scrubbedMessage.take(60)}"
        val now = System.currentTimeMillis()
        val last = breadcrumbLastEmitted[dedupKey]
        if (last != null && now - last < BREADCRUMB_RATE_LIMIT_MS) {
            return
        }
        breadcrumbLastEmitted[dedupKey] = now

        crashReporter?.addBreadcrumb(tag, scrubbedMessage, level, category)
    }

    @PublishedApi
    internal fun addSentryLog(tag: String, message: String, level: String, throwable: Throwable? = null) {
        if (!isAnalyticsEnabled || !isSentryLogsEnabled) return

        val scrubbedMessage = PiiScrubber.scrub(message)
        val dedupKey = "$tag:${scrubbedMessage.take(60)}"
        val now = System.currentTimeMillis()
        val last = sentryLogLastEmitted[dedupKey]
        if (last != null && now - last < BREADCRUMB_RATE_LIMIT_MS) {
            return
        }
        sentryLogLastEmitted[dedupKey] = now

        logScope.launch {
            crashReporter?.addLog(tag, scrubbedMessage, level, throwable)
        }
    }

    @PublishedApi
    internal fun getAutoTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return if (stackTrace.size > CALL_STACK_INDEX) {
            val element = stackTrace[CALL_STACK_INDEX]
            val className = element.className.substringAfterLast('.')
            className.substringBefore('$')
        } else {
            DEFAULT_TAG
        }
    }
}
