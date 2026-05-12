#include "api.h"
#include "obfuscation.h"
#include <android/log.h>

#define LOG_TAG "NextApi"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace next {

std::string ApiClient::getBaseUrl() {
    // In a real app, this would be obfuscated or dynamically selected
    return XOR_STR("https://api.protonmail.ch/");
}

ApiClient::Response ApiClient::performRequest(
    JNIEnv* env,
    const std::string& method,
    const std::string& url,
    const std::map<std::string, std::string>& headers,
    const std::string& body
) {
    jclass managerClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/NextVpnManager").c_str());
    if (!managerClass) {
        LOGE("Could not find NextVpnManager class");
        return {500, "", {}};
    }

    jmethodID requestMethod = env->GetStaticMethodID(managerClass, XOR_STR("performNativeRequest").c_str(),
        XOR_STR("(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;)Lru/protonmod/next/vpn/NextVpnManager$NativeResponse;").c_str());

    if (!requestMethod) {
        LOGE("Could not find performNativeRequest method");
        return {500, "", {}};
    }

    // Convert headers map to Java Map
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jobject hashMap = env->NewObject(hashMapClass, hashMapInit);
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    for (const auto& [key, value] : headers) {
        jstring jKey = env->NewStringUTF(key.c_str());
        jstring jValue = env->NewStringUTF(value.c_str());
        env->CallObjectMethod(hashMap, putMethod, jKey, jValue);
        env->DeleteLocalRef(jKey);
        env->DeleteLocalRef(jValue);
    }

    jstring jMethod = env->NewStringUTF(method.c_str());
    jstring jUrl = env->NewStringUTF(url.c_str());
    jstring jBody = env->NewStringUTF(body.c_str());

    jobject nativeResponse = env->CallStaticObjectMethod(managerClass, requestMethod, jMethod, jUrl, hashMap, jBody);

    env->DeleteLocalRef(jMethod);
    env->DeleteLocalRef(jUrl);
    env->DeleteLocalRef(jBody);
    env->DeleteLocalRef(hashMap);

    if (!nativeResponse) {
        LOGE("Native request returned null");
        return {500, "", {}};
    }

    jclass responseClass = env->GetObjectClass(nativeResponse);
    jfieldID codeField = env->GetFieldID(responseClass, "code", "I");
    jfieldID bodyField = env->GetFieldID(responseClass, "body", "Ljava/lang/String;");

    int code = env->GetIntField(nativeResponse, codeField);
    jstring jResponseBody = (jstring)env->GetObjectField(nativeResponse, bodyField);

    const char* bodyChars = env->GetStringUTFChars(jResponseBody, nullptr);
    std::string responseBody(bodyChars);
    env->ReleaseStringUTFChars(jResponseBody, bodyChars);

    return {code, responseBody, {}};
}

ApiClient::Response ApiClient::post(JNIEnv* env, const std::string& path, const std::string& jsonBody, const std::map<std::string, std::string>& extraHeaders) {
    std::map<std::string, std::string> headers = extraHeaders;
    headers["Content-Type"] = "application/json";
    headers["Accept"] = "application/vnd.protonmail.v1+json";
    return performRequest(env, "POST", getBaseUrl() + path, headers, jsonBody);
}

ApiClient::Response ApiClient::get(JNIEnv* env, const std::string& path, const std::map<std::string, std::string>& extraHeaders) {
    std::map<std::string, std::string> headers = extraHeaders;
    headers["Accept"] = "application/vnd.protonmail.v1+json";
    return performRequest(env, "GET", getBaseUrl() + path, headers);
}

ApiClient::Response ApiClient::del(JNIEnv* env, const std::string& path, const std::map<std::string, std::string>& extraHeaders) {
    std::map<std::string, std::string> headers = extraHeaders;
    headers["Accept"] = "application/vnd.protonmail.v1+json";
    return performRequest(env, "DELETE", getBaseUrl() + path, headers);
}

} // namespace next
