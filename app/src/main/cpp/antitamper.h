#ifndef NEXT_ANTITAMPER_H
#define NEXT_ANTITAMPER_H

#include <string>
#include <jni.h>
#include <android/native_window.h>
#include <thread>
#include <atomic>
#include <mutex>
#include <android/asset_manager.h>

namespace next {

enum class OverlayView {
    WARNING,
    DOWNLOAD,
    INTEGRITY_FAILURE
};

class AntiTamper {
public:
    static bool check(JNIEnv* env, jobject context);
    static bool checkEnvironment(JNIEnv* env);
    static bool checkHooks(JNIEnv* env, jobject context);
    static bool verifyApkArchive(JNIEnv* env);
    static std::string getApkPathFromMaps();
    static std::string getExpectedSignature();
    static std::string getExpectedPackageName();
    static int getExpectedVersionCode();
    static std::string getExpectedVersionName();

    // Advanced Checks
    static bool checkPtrace();
    static bool checkTracerPid();
    static bool checkDebuggable(JNIEnv* env, jobject context);
    static bool checkRoot();
    static bool checkStringIntegrity(JNIEnv* env, jobject context);

    // Get protected strings by locale
    static std::string getProtectedString(const std::string& locale, const std::string& key);
    static std::string getStringFromResources(JNIEnv* env, jobject context, const std::string& key);

    // Persistence for tamper acknowledgment
    static int getTamperAckCount(JNIEnv* env, jobject context);
    static void incrementTamperAckCount(JNIEnv* env, jobject context);

    // Advanced Anti-Tamper
    static std::string generateChallenge();
    static bool verifyResponse(const std::string& response);
    static void reportBypassAttempt(JNIEnv* env, const std::string& reason);
    static void reportSecurityEvent(JNIEnv* env, const std::string& event);
    static void reportStringMismatch(JNIEnv* env, const std::string& key, const std::string& expected, const std::string& got);

    // Native GUI Logic (ImGUI Overlay)
    static void onActivityResumed(JNIEnv* env, jobject activity);
    static void showNativeOverlay(JNIEnv* env, jobject activity);
    static void dismissNativeOverlay(JNIEnv* env);
    static void initImGui(ANativeWindow* window);
    static void renderLoop();
    static void handleInputEvent(float x, float y, int action);

    static void setAssetManager(AAssetManager* manager);

    static void handleAcceptRisks(JNIEnv* env, jobject context, const std::string& challenge, const std::string& response);
    static void handleDownloadOfficial(JNIEnv* env, jobject activity);
    static void openUrl(JNIEnv* env, jobject context, const std::string& url);
    static void setLogcatEnabled(bool enabled);
    static bool isLogcatEnabled();

    static void verifyCriticalIntegrity(JNIEnv* env);

    static void registerLifecycleCallbacks(JNIEnv* env, jobject application);

    static std::atomic<bool> g_force_detection;
    static std::atomic<bool> g_force_error;

    static std::atomic<bool> g_download_clicked;
    static std::atomic<bool> g_accept_clicked;
    static std::atomic<OverlayView> g_current_view;
    static std::string g_current_locale;
    static std::string g_url_to_open;

private:
    static std::atomic<bool> g_overlay_active;
    static std::atomic<bool> g_logcat_enabled;
    static std::thread g_render_thread;
    static ANativeWindow* g_native_window;
    static std::mutex g_window_mutex;
    static float g_touch_x;
    static float g_touch_y;
    static int g_touch_action;
    static std::atomic<bool> g_touch_pending;
    static std::string g_challenge;
    static int g_countdown;
    static AAssetManager* g_asset_manager;

    static jobject g_overlay_dialog;
    static jobject g_lifecycle_callback_proxy;
    static JavaVM* g_vm;
};

} // namespace next

#endif // NEXT_ANTITAMPER_H
