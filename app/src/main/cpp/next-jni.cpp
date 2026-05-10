#include <jni.h>
#include <string>
#include <vector>
#include <set>
#include "next-vpn.h"

extern "C" {

JNIEXPORT jstring JNICALL
Java_ru_protonmod_next_vpn_NextConfigGenerator_generateConfigNative(
    JNIEnv* env,
    jobject thiz,
    jstring server_public_key,
    jstring private_key,
    jstring local_ip,
    jstring dns_server,
    jstring target_ip,
    jboolean is_include_mode,
    jobjectArray selected_apps,
    jobjectArray selected_ips,
    jint port,
    jstring certificate,
    jobject obfuscation_params
) {
    auto jstringToString = [&](jstring jstr) -> std::string {
        if (!jstr) return "";
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        std::string str(chars);
        env->ReleaseStringUTFChars(jstr, chars);
        return str;
    };

    auto jobjectArrayToSet = [&](jobjectArray array) -> std::set<std::string> {
        std::set<std::string> result;
        if (!array) return result;
        jsize length = env->GetArrayLength(array);
        for (jsize i = 0; i < length; ++i) {
            jstring jstr = (jstring)env->GetObjectArrayElement(array, i);
            result.insert(jstringToString(jstr));
            env->DeleteLocalRef(jstr);
        }
        return result;
    };

    next::ObfuscationParams params;
    jclass params_class = env->GetObjectClass(obfuscation_params);

    auto getIntField = [&](const char* field_name) -> int {
        jfieldID field_id = env->GetFieldID(params_class, field_name, "I");
        return env->GetIntField(obfuscation_params, field_id);
    };

    auto getStringField = [&](const char* field_name) -> std::string {
        jfieldID field_id = env->GetFieldID(params_class, field_name, "Ljava/lang/String;");
        return jstringToString((jstring)env->GetObjectField(obfuscation_params, field_id));
    };

    params.jc = getIntField("jc");
    params.jmin = getIntField("jmin");
    params.jmax = getIntField("jmax");
    params.s1 = getIntField("s1");
    params.s2 = getIntField("s2");
    params.s3 = getIntField("s3");
    params.s4 = getIntField("s4");
    params.h1 = getStringField("h1");
    params.h2 = getStringField("h2");
    params.h3 = getStringField("h3");
    params.h4 = getStringField("h4");
    params.i1 = getStringField("i1");
    params.i2 = getStringField("i2");
    params.i3 = getStringField("i3");
    params.i4 = getStringField("i4");
    params.i5 = getStringField("i5");

    std::string config = next::ConfigGenerator::buildConfig(
        jstringToString(server_public_key),
        jstringToString(private_key),
        jstringToString(local_ip),
        jstringToString(dns_server),
        jstringToString(target_ip),
        (bool)is_include_mode,
        jobjectArrayToSet(selected_apps),
        jobjectArrayToSet(selected_ips),
        (int)port,
        jstringToString(certificate),
        params
    );

    return env->NewStringUTF(config.c_str());
}

JNIEXPORT jboolean JNICALL
Java_ru_protonmod_next_vpn_NextIpSubnetCalculator_isValidIpOrCidrNative(JNIEnv* env, jobject thiz, jstring input) {
    const char* chars = env->GetStringUTFChars(input, nullptr);
    bool result = next::IpSubnetCalculator::isValidIpOrCidr(chars);
    env->ReleaseStringUTFChars(input, chars);
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_ru_protonmod_next_vpn_NextIpSubnetCalculator_complementOfExcludedNative(JNIEnv* env, jobject thiz, jobjectArray excluded_cidrs) {
    std::vector<std::string> excluded;
    jsize length = env->GetArrayLength(excluded_cidrs);
    for (jsize i = 0; i < length; ++i) {
        jstring jstr = (jstring)env->GetObjectArrayElement(excluded_cidrs, i);
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        excluded.push_back(chars);
        env->ReleaseStringUTFChars(jstr, chars);
        env->DeleteLocalRef(jstr);
    }

    std::vector<std::string> result = next::IpSubnetCalculator::complementOfExcluded(excluded);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(result.size(), stringClass, nullptr);
    for (size_t i = 0; i < result.size(); ++i) {
        env->SetObjectArrayElement(resultArray, i, env->NewStringUTF(result[i].c_str()));
    }
    return resultArray;
}

static next::VpnManager g_vpn_manager;

JNIEXPORT void JNICALL
Java_ru_protonmod_next_vpn_NextVpnManager_setStateNative(JNIEnv* env, jobject thiz, jint state) {
    g_vpn_manager.setState(static_cast<next::VpnState>(state));
}

JNIEXPORT jint JNICALL
Java_ru_protonmod_next_vpn_NextVpnManager_getStateNative(JNIEnv* env, jobject thiz) {
    return static_cast<jint>(g_vpn_manager.getState());
}

JNIEXPORT jboolean JNICALL
Java_ru_protonmod_next_vpn_NextVpnManager_canConnectNative(JNIEnv* env, jobject thiz) {
    return g_vpn_manager.canConnect();
}

JNIEXPORT jboolean JNICALL
Java_ru_protonmod_next_vpn_NextVpnManager_canDisconnectNative(JNIEnv* env, jobject thiz) {
    return g_vpn_manager.canDisconnect();
}

} // extern "C"
