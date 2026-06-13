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

/**
 * Interface for crash reporting and analytics.
 * Abstracted to allow completely removing Sentry in privacy-first flavors.
 */
interface CrashReporter {
    fun addBreadcrumb(tag: String, message: String, level: String, category: String)
    fun addLog(tag: String, message: String, level: String, throwable: Throwable?)
    fun captureException(throwable: Throwable, extras: Map<String, String>? = null)
    fun captureMessage(message: String, level: String, extras: Map<String, String>? = null)
    fun flush(timeout: Long)
    
    // Metrics
    fun recordDistribution(name: String, value: Double)
    fun recordCount(name: String, value: Double)
}
