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
import ru.protonmod.next.BuildConfig

/**
 * A professional logging wrapper for Proton VPN-Next.
 * 
 * Automatically handles debug/release logic, tag generation from stack trace,
 * and provides a cleaner API. All logging is stripped from release builds
 * by checking [BuildConfig.DEBUG].
 */
object ProtonLogger {

    private const val DEFAULT_TAG = "ProtonVPN"
    private const val CALL_STACK_INDEX = 4

    /** Log at VERBOSE level */
    fun v(tag: String? = null, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.v(tag ?: getAutoTag(), message, throwable)
        }
    }

    /** Log at VERBOSE level with a lazy message lambda for better performance */
    inline fun v(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        if (BuildConfig.DEBUG) {
            v(tag, message(), throwable)
        }
    }

    /** Log at DEBUG level */
    fun d(tag: String? = null, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.d(tag ?: getAutoTag(), message, throwable)
        }
    }

    /** Log at DEBUG level with a lazy message lambda for better performance */
    inline fun d(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        if (BuildConfig.DEBUG) {
            d(tag, message(), throwable)
        }
    }

    /** Log at INFO level */
    fun i(tag: String? = null, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.i(tag ?: getAutoTag(), message, throwable)
        }
    }

    /** Log at INFO level with a lazy message lambda for better performance */
    inline fun i(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        if (BuildConfig.DEBUG) {
            i(tag, message(), throwable)
        }
    }

    /** Log at WARN level */
    fun w(tag: String? = null, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.w(tag ?: getAutoTag(), message, throwable)
        }
    }

    /** Log at WARN level with a lazy message lambda for better performance */
    inline fun w(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        if (BuildConfig.DEBUG) {
            w(tag, message(), throwable)
        }
    }

    /** Log at ERROR level */
    fun e(tag: String? = null, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(tag ?: getAutoTag(), message, throwable)
        }
    }

    /** Log at ERROR level with a lazy message lambda for better performance */
    inline fun e(tag: String? = null, throwable: Throwable? = null, crossinline message: () -> String) {
        if (BuildConfig.DEBUG) {
            e(tag, message(), throwable)
        }
    }

    /**
     * Professional error logging that accepts a message and an optional throwable.
     */
    fun error(tag: String? = null, message: String, throwable: Throwable? = null) {
        e(tag, message, throwable)
    }

    /**
     * Automatically extracts the class name from the stack trace to use as a tag.
     */
    @PublishedApi
    internal fun getAutoTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return if (stackTrace.size > CALL_STACK_INDEX) {
            val element = stackTrace[CALL_STACK_INDEX]
            val className = element.className.substringAfterLast('.')
            // If the caller is an anonymous class or lambda, cleanup the name
            className.substringBefore('$')
        } else {
            DEFAULT_TAG
        }
    }
}
