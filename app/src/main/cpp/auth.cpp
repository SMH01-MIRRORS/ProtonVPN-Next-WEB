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
    LOGD("Native login started for user: %s", username.c_str());

    std::string challenge = buildChallengePayload();
    std::map<std::string, std::string> headers;
    if (!captchaToken.empty()) {
        headers["x-pm-human-verification-token"] = captchaToken;
        headers["x-PM-Human-Verification-Token-Type"] = "captcha"; // Proton often uses mixed case for some reason
    }

    // Phase 0: Anonymous Session
    auto anonResp = ApiClient::post(env, "auth/v4/sessions", challenge, headers);
    if (anonResp.statusCode != 200) {
        LOGE("Failed to create anonymous session: %d. Body: %s", anonResp.statusCode, anonResp.body.c_str());
        return {false, anonResp.statusCode, "", "", "", "", {}, "Anon session failed", false, "", ""};
    }

    auto anonJson = json::parse(anonResp.body);
    std::string anonToken = anonJson.value("AccessToken", "");
    std::string anonUid = anonJson.value("UID", "");

    std::map<std::string, std::string> authHeaders;
    authHeaders["Authorization"] = "Bearer " + anonToken;
    authHeaders["x-pm-uid"] = anonUid;

    // Phase 1: Auth Info
    json infoReq = {{"Username", username}, {"Intent", "Auto"}};
    auto infoResp = ApiClient::post(env, "auth/v4/info", infoReq.dump(), authHeaders);

    if (infoResp.statusCode == 422) {
        // Handle Captcha
        auto errJson = json::parse(infoResp.body);
        if (errJson.value("Code", 0) == 9001) {
             auto details = errJson["Details"];
             return {false, 422, "", "", anonUid, "", {}, "Captcha required", true, details.value("WebUrl", ""), details.value("HumanVerificationToken", "")};
        }
    }

    if (infoResp.statusCode != 200) {
        LOGE("Auth info failed: %d. Body: %s", infoResp.statusCode, infoResp.body.c_str());
        return {false, infoResp.statusCode, "", "", anonUid, "", {}, "Auth info failed", false, "", ""};
    }

    auto infoJson = json::parse(infoResp.body);
    std::string salt = infoJson.value("Salt", "");
    std::string modulus = infoJson.value("Modulus", "");
    std::string serverEphemeral = infoJson.value("ServerEphemeral", "");
    std::string srpSession = infoJson.value("SRPSession", "");

    // Phase 2: SRP Proofs
    jclass cryptoClass = env->FindClass(XOR_STR("ru/protonmod/next/utils/crypto/CryptoWrapper").c_str());
    if (!cryptoClass) return {false, 500, "", "", anonUid, "", {}, "CryptoWrapper class not found", false, "", ""};

    jmethodID cryptoInit = env->GetMethodID(cryptoClass, "<init>", "()V");
    jobject cryptoObj = env->NewObject(cryptoClass, cryptoInit);

    jmethodID proofsMethod = env->GetMethodID(cryptoClass, XOR_STR("generateSrpProofs").c_str(),
        XOR_STR("(Ljava/lang/String;[BLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lru/protonmod/next/utils/crypto/SrpProofs;").c_str());

    jstring jUser = env->NewStringUTF(username.c_str());
    jbyteArray jPass = env->NewByteArray(password.length());
    env->SetByteArrayRegion(jPass, 0, password.length(), (const jbyte*)password.c_str());
    jstring jSalt = env->NewStringUTF(salt.c_str());
    jstring jMod = env->NewStringUTF(modulus.c_str());
    jstring jEph = env->NewStringUTF(serverEphemeral.c_str());

    jobject jProofs = env->CallObjectMethod(cryptoObj, proofsMethod, jUser, jPass, jSalt, jMod, jEph);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        return {false, 500, "", "", anonUid, "", {}, "SRP proof generation threw exception", false, "", ""};
    }

    if (!jProofs) {
        return {false, 500, "", "", anonUid, "", {}, "SRP proof generation returned null", false, "", ""};
    }

    jclass proofsClass = env->GetObjectClass(jProofs);
    jstring jClientEph = (jstring)env->GetObjectField(jProofs, env->GetFieldID(proofsClass, "clientEphemeral", "Ljava/lang/String;"));
    jstring jClientProof = (jstring)env->GetObjectField(jProofs, env->GetFieldID(proofsClass, "clientProof", "Ljava/lang/String;"));

    const char* clientEphChars = env->GetStringUTFChars(jClientEph, nullptr);
    const char* clientProofChars = env->GetStringUTFChars(jClientProof, nullptr);

    json loginReq = {
        {"Username", username},
        {"ClientEphemeral", clientEphChars},
        {"ClientProof", clientProofChars},
        {"SRPSession", srpSession},
        {"Payload", json::parse(challenge)["Payload"]}
    };

    env->ReleaseStringUTFChars(jClientEph, clientEphChars);
    env->ReleaseStringUTFChars(jClientProof, clientProofChars);

    // Final Login Request
    auto loginResp = ApiClient::post(env, "auth/v4", loginReq.dump(), authHeaders);

    if (loginResp.statusCode == 200) {
        auto loginJson = json::parse(loginResp.body);
        LoginResult res;
        res.success = true;
        res.code = 1000;
        res.accessToken = loginJson.value("AccessToken", anonToken);
        res.refreshToken = loginJson.value("RefreshToken", "");
        res.sessionId = loginJson.value("UID", anonUid);
        res.userId = loginJson.value("UserID", "");
        if (loginJson.contains("Scopes")) {
            for (auto& scope : loginJson["Scopes"]) res.scopes.push_back(scope);
        }
        res.error = "";
        res.captchaRequired = false;
        res.captchaUrl = "";
        res.captchaToken = "";
        return res;
    }

    LOGE("Final login failed: %d. Body: %s", loginResp.statusCode, loginResp.body.c_str());
    return {false, loginResp.statusCode, "", "", anonUid, "", {}, "Login failed", false, "", ""};
}

LoginResult AuthManager::loginAnonymous(JNIEnv* env, const std::string& captchaToken) {
    (void)env; (void)captchaToken;
    return {false, 500, "", "", "", "", {}, "Not implemented yet", false, "", ""};
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
