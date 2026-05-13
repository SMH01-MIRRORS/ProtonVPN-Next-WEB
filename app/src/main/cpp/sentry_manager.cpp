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

#define TAG "SentryManager"

namespace next {

bool SentryManager::g_initialized = true; // Assume initialized by Java SDK via NDK integration

void SentryManager::init(const char* cache_dir, bool debug, const char* version_name, int version_code, const SentrySettings& settings) {
    // Manual init removed. The Java SDK handles this.
    (void)cache_dir; (void)debug; (void)version_name; (void)version_code; (void)settings;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Sentry Native scope linked to Java SDK");
}

void SentryManager::shutdown() {
    // Shutdown handled by Java SDK
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

    __android_log_print(ANDROID_LOG_WARN, TAG, "Security event reported: %s", event.c_str());
}

void SentryManager::addBreadcrumb(const std::string& category, const std::string& message, sentry_level_t level) {
    sentry_value_t breadcrumb = sentry_value_new_breadcrumb(category.c_str(), message.c_str());
    sentry_value_set_by_key(breadcrumb, "level", sentry_value_new_string(
        level == SENTRY_LEVEL_DEBUG ? "debug" :
        level == SENTRY_LEVEL_INFO ? "info" :
        level == SENTRY_LEVEL_WARNING ? "warning" :
        level == SENTRY_LEVEL_ERROR ? "error" : "fatal"
    ));
    sentry_add_breadcrumb(breadcrumb);
}

void SentryManager::captureMessage(const std::string& message, sentry_level_t level) {
    sentry_value_t event = sentry_value_new_message_event(level, nullptr, message.c_str());
    sentry_capture_event(event);
}

} // namespace next
