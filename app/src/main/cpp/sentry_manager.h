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

#ifndef NEXT_SENTRY_MANAGER_H
#define NEXT_SENTRY_MANAGER_H

#include <string>
#include <sentry.h>

namespace next {

class SentryManager {
public:
    /**
     * Initializes Sentry Native SDK.
     * @param cache_dir Process-specific cache directory for Sentry outbox.
     * @param debug Enable Sentry debug logs.
     * @param version_name Application version name.
     * @param version_code Application version code.
     */
    static void init(const char* cache_dir, bool debug, const char* version_name, int version_code);

    /**
     * Shuts down the Sentry Native SDK.
     */
    static void shutdown();

    /**
     * Reports a security-related event to Sentry.
     * @param event Description of the security event.
     */
    static void reportSecurityEvent(const std::string& event);

    /**
     * Adds a breadcrumb to Sentry.
     * @param category Category of the breadcrumb.
     * @param message Breadcrumb message.
     * @param level Sentry level (default: INFO).
     */
    static void addBreadcrumb(const std::string& category, const std::string& message, sentry_level_t level = SENTRY_LEVEL_INFO);

    /**
     * Captures an exception/error message.
     * @param message The error message.
     * @param level Severity level.
     */
    static void captureMessage(const std::string& message, sentry_level_t level = SENTRY_LEVEL_ERROR);

private:
    static bool g_initialized;
};

} // namespace next

#endif // NEXT_SENTRY_MANAGER_H
