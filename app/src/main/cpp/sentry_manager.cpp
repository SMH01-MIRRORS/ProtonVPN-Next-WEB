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

#include <sys/stat.h>

#define TAG "SentryManager"

namespace next {

bool SentryManager::g_initialized = false;

std::string SentryManager::getSentryDsn() {
    return XOR_STR("https://7b74cef88678ecb3e6047ac6b4abf139@o4510986952310784.ingest.de.sentry.io/4510986956374096");
}

void SentryManager::init(const char* cache_dir, bool debug, const char* version_name, int version_code, const char* package_name) {
    if (g_initialized) return;

    sentry_options_t *options = sentry_options_new();

    // Use the same DSN for now, but in a separate native instance
    sentry_options_set_dsn(options, getSentryDsn().c_str());

    // Set a process-specific cache directory for native to avoid conflicts with Kotlin Sentry
    char native_cache[512];
    snprintf(native_cache, sizeof(native_cache), "%s/sentry_native", cache_dir);

    // Create the directory if it doesn't exist
    mkdir(native_cache, 0777);

    sentry_options_set_database_path(options, native_cache);

    sentry_options_set_release(options, (std::string(package_name) + "@" + version_name + "+" + std::to_string(version_code)).c_str());

    // Always enable debug for now to see what Sentry is doing internally
    sentry_options_set_debug(options, 1);

    sentry_options_set_environment(options, XOR_STR("production").c_str());

    if (sentry_init(options) == 0) {
        g_initialized = true;
        __android_log_print(ANDROID_LOG_INFO, TAG, "Sentry Native initialized for security events in %s", native_cache);
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
    if (!g_initialized) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot report security event: Sentry not initialized. Event: %s", event.c_str());
        return;
    }

    sentry_uuid_t event_id;
    sentry_value_t sentry_event = sentry_value_new_event();

    sentry_value_set_by_key(sentry_event, "message", sentry_value_new_string(event.c_str()));
    sentry_value_set_by_key(sentry_event, "level", sentry_value_new_string("fatal"));
    sentry_value_set_by_key(sentry_event, "logger", sentry_value_new_string("security"));

    sentry_value_t tags = sentry_value_new_object();
    sentry_value_set_by_key(tags, "category", sentry_value_new_string("security"));
    sentry_value_set_by_key(tags, "tamper_detected", sentry_value_new_string("true"));
    sentry_value_set_by_key(sentry_event, "tags", tags);

    event_id = sentry_capture_event(sentry_event);
    bool success = !sentry_uuid_is_nil(&event_id);

    // Attempt to flush immediately.
    // In native, this should trigger the transport to send events from the outbox.
    sentry_flush(10000);

    if (success) {
        char uuid_str[37];
        sentry_uuid_as_string(&event_id, uuid_str);
        __android_log_print(ANDROID_LOG_WARN, TAG, "Security event captured (ID: %s). Check 'sentry' tag for transport logs.", uuid_str);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to capture security event: %s", event.c_str());
    }
}

} // namespace next
