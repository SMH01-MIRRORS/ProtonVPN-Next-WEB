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

struct SentrySettings {
    bool analyticsEnabled;
    bool performanceEnabled;
    bool sessionReplayEnabled;
    bool anrEnabled;
    bool metricsEnabled;
    bool logsEnabled;
    bool crashReportsEnabled;
};

class SentryManager {
public:
    /**
     * Initializes Sentry Native SDK.
     * @param cache_dir Process-specific cache directory for Sentry outbox.
     * @param debug Enable Sentry debug logs.
     * @param version_name Application version name.
     * @param version_code Application version code.
     * @param settings Granular settings for Sentry features.
     */
    static void init(const char* cache_dir, bool debug, const char* version_name, int version_code, const SentrySettings& settings);

    /**
     * Shuts down the Sentry Native SDK.
     */
    static void shutdown();

    /**
     * Returns the XOR-protected Sentry DSN.
     */
    static std::string getSentryDsn();

    /**
     * Reports a security-related event to Sentry.
     * @param event Description of the security event.
     */
    static void reportSecurityEvent(const std::string& event);

private:
    static bool g_initialized;
};

} // namespace next

#endif // NEXT_SENTRY_MANAGER_H
