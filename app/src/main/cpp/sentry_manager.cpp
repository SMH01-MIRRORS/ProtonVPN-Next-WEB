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

#include "sentry_manager.h"
#include "obfuscation.h"
#include <android/log.h>
#include <cstdio>

#define TAG "SentryManager"

namespace next {

bool SentryManager::g_initialized = false;

std::string SentryManager::getSentryDsn() {
    return XOR_STR("https://7b74cef88678ecb3e6047ac6b4abf139@o4510986952310784.ingest.de.sentry.io/4510986956374096");
}

void SentryManager::init(const char* cache_dir, bool debug, const char* version_name, int version_code, const SentrySettings& settings) {
    if (g_initialized) return;

    sentry_options_t *options = sentry_options_new();

    // Use the same DSN for now, but in a separate native instance
    sentry_options_set_dsn(options, getSentryDsn().c_str());

    // Set a process-specific cache directory for native to avoid conflicts with Kotlin Sentry
    char native_cache[512];
    snprintf(native_cache, sizeof(native_cache), "%s/sentry_native", cache_dir);
    sentry_options_set_database_path(options, native_cache);

    sentry_options_set_release(options, (std::string(XOR_STR("ru.protonmod.next@")) + version_name + "+" + std::to_string(version_code)).c_str());
    sentry_options_set_debug(options, debug ? 1 : 0);

    // Filter events based on settings
    if (!settings.crashReportsEnabled) {
        sentry_options_set_before_send(options, [](sentry_value_t event, void *hint, void *closure) {
            (void)hint; (void)closure;
            return sentry_value_new_null();
        }, nullptr);
    }

    if (sentry_init(options) == 0) {
        g_initialized = true;
        __android_log_print(ANDROID_LOG_INFO, TAG, "Sentry Native initialized independently in %s", native_cache);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Sentry Native initialization failed!");
    }
}

void SentryManager::shutdown() {
    if (g_initialized) {
        sentry_close();
        g_initialized = false;
    }
}

void SentryManager::reportSecurityEvent(const std::string& event) {
    sentry_value_t sentry_event = sentry_value_new_event();

    sentry_value_t s_msg = sentry_value_new_object();
    sentry_value_set_by_key(s_msg, "formatted", sentry_value_new_string(event.c_str()));
    sentry_value_set_by_key(sentry_event, "message", s_msg);

    sentry_value_set_by_key(sentry_event, "level", sentry_value_new_string("warning"));

    sentry_value_t tags = sentry_value_new_object();
    sentry_value_set_by_key(tags, "category", sentry_value_new_string("security"));
    sentry_value_set_by_key(sentry_event, "tags", tags);

    sentry_capture_event(sentry_event);
    sentry_flush(5000);

    __android_log_print(ANDROID_LOG_WARN, TAG, "Security event reported: %s", event.c_str());
}

} // namespace next
