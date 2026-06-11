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
 * A no-op implementation of CrashReporter for privacy-first flavors.
 * Ensures zero Sentry dependency and no data leakage.
 */
class NoOpCrashReporter : CrashReporter {
    override fun addBreadcrumb(tag: String, message: String, level: String, category: String) {}
    override fun addLog(tag: String, message: String, level: String, throwable: Throwable?) {}
    override fun captureException(throwable: Throwable) {}
    override fun captureMessage(message: String, level: String) {}
    override fun flush(timeout: Long) {}
}
