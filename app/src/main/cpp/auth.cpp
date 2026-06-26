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

#include "auth.h"
#include "json.hpp"
#include "obfuscation.h"
#include <android/log.h>

#define LOG_TAG "NextAuth"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using json = nlohmann::json;

namespace next {

static DeviceInfo g_device_info;

void AuthManager::updateDeviceInfo(const DeviceInfo& info) {
    g_device_info = info;
}

const DeviceInfo& AuthManager::getDeviceInfo() {
    return g_device_info;
}

std::string AuthManager::buildChallengePayload() {
    json payload = {
        {"Payload", {
            {"vpn-android-v4-challenge-0", {
                {"type", "me.proton.core.challenge.data.frame.ChallengeFrame.Device"},
                {"v", g_device_info.version.empty() ? XOR_STR("5.18.1.0") : g_device_info.version},
                {"appLang", g_device_info.lang.empty() ? XOR_STR("en") : g_device_info.lang},
                {"timezone", g_device_info.timezone.empty() ? XOR_STR("UTC") : g_device_info.timezone},
                {"deviceName", g_device_info.deviceHash.empty() ? XOR_STR("0") : g_device_info.deviceHash},
                {"regionCode", g_device_info.region.empty() ? XOR_STR("US") : g_device_info.region},
                {"timezoneOffset", g_device_info.offset},
                {"isJailbreak", g_device_info.jailbreak},
                {"preferredContentSize", g_device_info.contentSize.empty() ? XOR_STR("1.0") : g_device_info.contentSize},
                {"storageCapacity", g_device_info.storage},
                {"isDarkmodeOn", g_device_info.darkMode},
                {"keyboards", json::array()}
            }}
        }}
    };
    return payload.dump();
}

LoginResult AuthManager::login(JNIEnv* env, const std::string& username, const std::string& password, const std::string& captchaToken) {
    (void)env; (void)username; (void)password; (void)captchaToken;
    return {false, 500, "", "", "", "", {}, "Not implemented in native code (moved to Kotlin)", false, "", ""};
}

LoginResult AuthManager::loginAnonymous(JNIEnv* env, const std::string& captchaToken) {
    (void)env; (void)captchaToken;
    return {false, 500, "", "", "", "", {}, "Not implemented in native code (moved to Kotlin)", false, "", ""};
}

LoginResult AuthManager::refreshSession(JNIEnv* env, const std::string& sessionId, const std::string& refreshToken) {
    json req = {
        {"UID", sessionId},
        {"RefreshToken", refreshToken},
        {"ResponseType", "token"},
        {"GrantType", "refresh_token"},
        {"RedirectURI", "https://vpn-api.proton.me/"}
    };

    auto resp = ApiClient::post(env, "auth/v4/refresh", req.dump());
    if (resp.statusCode == 200) {
        auto j = json::parse(resp.body);
        LoginResult res;
        res.success = true;
        res.code = 1000;
        res.accessToken = j.value("AccessToken", "");
        res.refreshToken = j.value("RefreshToken", refreshToken);
        res.sessionId = sessionId;
        res.userId = "";
        res.scopes = {};
        res.error = "";
        res.captchaRequired = false;
        res.captchaUrl = "";
        res.captchaToken = "";
        return res;
    }
    return {false, resp.statusCode, "", "", "", "", {}, "Refresh failed", false, "", ""};
}

} // namespace next
