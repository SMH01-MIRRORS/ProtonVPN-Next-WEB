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
 * along with this program.  See the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "api.h"
#include "obfuscation.h"
#include "auth.h"
#include <android/log.h>

#define LOG_TAG "NextApi"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace next {

std::string ApiClient::getBaseUrl() {
    return XOR_STR("https://vpn-api.proton.me/");
}

ApiClient::Response ApiClient::performRequest(
    JNIEnv* env,
    const std::string& method,
    const std::string& url,
    const std::map<std::string, std::string>& headers,
    const std::string& body
) {
    if (!g_vpn_manager_class || !g_perform_request_mid) {
        LOGE("JNI cache not initialized for NextVpnManager");
        return {500, "", {}};
    }

    const auto& deviceInfo = AuthManager::getDeviceInfo();

    // Merge provided headers with mandatory dynamic Proton headers
    std::map<std::string, std::string> finalHeaders = headers;
    if (finalHeaders.find("x-pm-appversion") == finalHeaders.end()) {
        std::string version = deviceInfo.version.empty() ? XOR_STR("5.18.1.0") : deviceInfo.version;
        finalHeaders["x-pm-appversion"] = XOR_STR("android-vpn@") + version + XOR_STR("-dev+play");
    }
    if (finalHeaders.find("x-pm-apiversion") == finalHeaders.end()) {
        finalHeaders["x-pm-apiversion"] = XOR_STR("3");
    }
    if (finalHeaders.find("User-Agent") == finalHeaders.end()) {
        finalHeaders["User-Agent"] = deviceInfo.userAgent.empty() ?
            XOR_STR("ProtonVPN/5.18.1.0 (Android 14; EN)") : deviceInfo.userAgent;
    }

    // Convert headers map to Java Map
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jobject hashMap = env->NewObject(hashMapClass, hashMapInit);
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    for (const auto& [key, value] : finalHeaders) {
        jstring jKey = env->NewStringUTF(key.c_str());
        jstring jValue = env->NewStringUTF(value.c_str());
        env->CallObjectMethod(hashMap, putMethod, jKey, jValue);
        env->DeleteLocalRef(jKey);
        env->DeleteLocalRef(jValue);
    }

    jstring jMethod = env->NewStringUTF(method.c_str());
    jstring jUrl = env->NewStringUTF(url.c_str());
    jstring jBody = body.empty() ? nullptr : env->NewStringUTF(body.c_str());

    jobject nativeResponse = env->CallStaticObjectMethod(g_vpn_manager_class, g_perform_request_mid, jMethod, jUrl, hashMap, jBody);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        LOGE("Exception occurred during performNativeRequest");
    }

    env->DeleteLocalRef(jMethod);
    env->DeleteLocalRef(jUrl);
    if (jBody) env->DeleteLocalRef(jBody);
    env->DeleteLocalRef(hashMap);

    if (!nativeResponse) {
        LOGE("Native request returned null");
        return {500, "", {}};
    }

    jfieldID codeField = env->GetFieldID(g_native_response_class, "code", "I");
    jfieldID bodyField = env->GetFieldID(g_native_response_class, "body", "Ljava/lang/String;");

    int code = env->GetIntField(nativeResponse, codeField);
    jstring jResponseBody = (jstring)env->GetObjectField(nativeResponse, bodyField);

    std::string responseBody;
    if (jResponseBody) {
        const char* bodyChars = env->GetStringUTFChars(jResponseBody, nullptr);
        responseBody = bodyChars;
        env->ReleaseStringUTFChars(jResponseBody, bodyChars);
    }

    env->DeleteLocalRef(nativeResponse);

    return {code, responseBody, {}};
}

ApiClient::Response ApiClient::post(JNIEnv* env, const std::string& path, const std::string& jsonBody, const std::map<std::string, std::string>& extraHeaders) {
    std::map<std::string, std::string> headers = extraHeaders;
    headers["Content-Type"] = "application/json";
    return performRequest(env, "POST", getBaseUrl() + path, headers, jsonBody);
}

ApiClient::Response ApiClient::get(JNIEnv* env, const std::string& path, const std::map<std::string, std::string>& extraHeaders) {
    std::map<std::string, std::string> headers = extraHeaders;
    return performRequest(env, "GET", getBaseUrl() + path, headers);
}

ApiClient::Response ApiClient::del(JNIEnv* env, const std::string& path, const std::map<std::string, std::string>& extraHeaders) {
    std::map<std::string, std::string> headers = extraHeaders;
    return performRequest(env, "DELETE", getBaseUrl() + path, headers);
}

} // namespace next
