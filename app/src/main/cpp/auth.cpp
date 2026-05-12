#include "auth.h"
#include "json.hpp"
#include "obfuscation.h"
#include <android/log.h>

#define LOG_TAG "NextAuth"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using json = nlohmann::json;

namespace next {

std::string AuthManager::buildChallengePayload() {
    // Simplified version of the Kotlin buildChallengePayload
    json payload = {
        {"Payload", {
            {"vpn-android-v4-challenge-0", {
                {"type", "me.proton.core.challenge.data.frame.ChallengeFrame.Device"},
                {"v", "12.0.0"}, // Should be dynamic
                {"appLang", "en"},
                {"timezone", "UTC"},
                {"deviceName", "Android Device"},
                {"isJailbreak", false}
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
        headers["x-pm-human-verification-token-type"] = "captcha";
    }

    // Phase 0: Anonymous Session
    auto anonResp = ApiClient::post(env, "auth/v4/sessions", challenge, headers);
    if (anonResp.statusCode != 200) {
        LOGE("Failed to create anonymous session: %d", anonResp.statusCode);
        return {false, anonResp.statusCode, "", "", "", "", {}, "Anon session failed"};
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
        return {false, infoResp.statusCode, "", "", anonUid, "", {}, "Auth info failed"};
    }

    auto infoJson = json::parse(infoResp.body);
    std::string salt = infoJson.value("Salt", "");
    std::string modulus = infoJson.value("Modulus", "");
    std::string serverEphemeral = infoJson.value("ServerEphemeral", "");
    std::string srpSession = infoJson.value("SRPSession", "");

    // Phase 2: SRP Proofs (Calling back to Kotlin for now to use existing Srp implementation)
    jclass cryptoClass = env->FindClass(XOR_STR("ru/protonmod/next/utils/crypto/CryptoWrapper").c_str());
    jmethodID proofsMethod = env->GetMethodID(cryptoClass, XOR_STR("generateSrpProofs").c_str(),
        XOR_STR("(Ljava/lang/String;[BLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lru/protonmod/next/utils/crypto/SrpProofs;").c_str());

    // Need an instance of CryptoWrapper. In a real app we'd inject it or have a static method.
    // For this migration, we'll assume we can find/create one.
    jmethodID cryptoInit = env->GetMethodID(cryptoClass, "<init>", "()V");
    jobject cryptoObj = env->NewObject(cryptoClass, cryptoInit);

    jstring jUser = env->NewStringUTF(username.c_str());
    jbyteArray jPass = env->NewByteArray(password.length());
    env->SetByteArrayRegion(jPass, 0, password.length(), (const jbyte*)password.c_str());
    jstring jSalt = env->NewStringUTF(salt.c_str());
    jstring jMod = env->NewStringUTF(modulus.c_str());
    jstring jEph = env->NewStringUTF(serverEphemeral.c_str());

    jobject jProofs = env->CallObjectMethod(cryptoObj, proofsMethod, jUser, jPass, jSalt, jMod, jEph);

    if (!jProofs) {
        return {false, 500, "", "", anonUid, "", {}, "SRP proof generation failed"};
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
        {"Payload", anonJson["Payload"]}
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
        return res;
    }

    return {false, loginResp.statusCode, "", "", anonUid, "", {}, "Login failed"};
}

LoginResult AuthManager::loginAnonymous(JNIEnv* env, const std::string& captchaToken) {
    // Implementation for anonymous login
    return {false, 500, "", "", "", "", {}, "Not implemented yet"};
}

LoginResult AuthManager::refreshSession(JNIEnv* env, const std::string& sessionId, const std::string& refreshToken) {
    json req = {
        {"UID", sessionId},
        {"RefreshToken", refreshToken},
        {"ResponseType", "token"},
        {"GrantType", "refresh_token"},
        {"RedirectURI", "http://protonmail.ch"}
    };

    auto resp = ApiClient::post(env, "auth/v4/refresh", req.dump());
    if (resp.statusCode == 200) {
        auto j = json::parse(resp.body);
        LoginResult res;
        res.success = true;
        res.accessToken = j.value("AccessToken", "");
        res.refreshToken = j.value("RefreshToken", refreshToken);
        return res;
    }
    return {false, resp.statusCode, "", "", "", "", {}, "Refresh failed"};
}

} // namespace next
