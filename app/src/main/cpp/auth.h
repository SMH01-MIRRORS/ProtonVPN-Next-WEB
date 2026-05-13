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

#ifndef NEXT_AUTH_H
#define NEXT_AUTH_H

#include <string>
#include <vector>
#include <jni.h>
#include "api.h"

namespace next {

struct DeviceInfo {
    std::string version;
    std::string lang;
    std::string timezone;
    std::string deviceHash;
    std::string region;
    int offset;
    bool jailbreak;
    std::string contentSize;
    double storage;
    bool darkMode;
    std::string userAgent;
};

struct LoginResult {
    bool success;
    int code;
    std::string accessToken;
    std::string refreshToken;
    std::string sessionId;
    std::string userId;
    std::vector<std::string> scopes;
    std::string error;

    bool captchaRequired = false;
    std::string captchaUrl;
    std::string captchaToken;
};

class AuthManager {
public:
    static void updateDeviceInfo(const DeviceInfo& info);
    static const DeviceInfo& getDeviceInfo();

    static LoginResult login(JNIEnv* env, const std::string& username, const std::string& password, const std::string& captchaToken = "");

    static LoginResult loginAnonymous(JNIEnv* env, const std::string& captchaToken = "");

    static LoginResult refreshSession(JNIEnv* env, const std::string& sessionId, const std::string& refreshToken);

    static std::string buildChallengePayload();
};

} // namespace next

#endif // NEXT_AUTH_H
