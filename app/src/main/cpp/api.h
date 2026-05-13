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

#ifndef NEXT_API_H
#define NEXT_API_H

#include <string>
#include <map>
#include <jni.h>

namespace next {

// JNI cache declarations
extern jclass g_vpn_manager_class;
extern jmethodID g_perform_request_mid;
extern jclass g_native_response_class;

class ApiClient {
public:
    struct Response {
        int statusCode;
        std::string body;
        std::map<std::string, std::string> headers;
    };

    static Response performRequest(
        JNIEnv* env,
        const std::string& method,
        const std::string& url,
        const std::map<std::string, std::string>& headers = {},
        const std::string& body = ""
    );

    static Response post(JNIEnv* env, const std::string& path, const std::string& jsonBody, const std::map<std::string, std::string>& extraHeaders = {});
    static Response get(JNIEnv* env, const std::string& path, const std::map<std::string, std::string>& extraHeaders = {});
    static Response del(JNIEnv* env, const std::string& path, const std::map<std::string, std::string>& extraHeaders = {});

    static std::string getBaseUrl();
};

} // namespace next

#endif // NEXT_API_H
