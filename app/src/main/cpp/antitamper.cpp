#include "antitamper.h"
#include "connection.h"
#include "security_metadata.h"
#include "obfuscation.h"
#include <android/log.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <sstream>
#include <chrono>
#include <vector>
#include <iomanip>
#include <fstream>
#include <sys/stat.h>
#include <unistd.h>
#include <link.h>
#include <elf.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include "imgui.h"
#include "imgui_impl_opengl3.h"
#include <sentry.h>

#define LOG_TAG "NextAntitamper"
#define LOGD(...) if (AntiTamper::isLogcatEnabled()) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) if (AntiTamper::isLogcatEnabled()) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define STRINGIFY(x) #x
#define TOSTRING(x) STRINGIFY(x)

// =============================================================================
// DEBUG SETTINGS - CHANGE THESE TO TRUE FOR QUICK TESTING
// =============================================================================
static const bool TEST_FORCE_DETECTION = false; // Show warning overlay
static const bool TEST_FORCE_CRASH     = false; // Simulate bypass attempt (Crash)
// =============================================================================

// ZIP parsing structures
#pragma pack(push, 1)
struct ZipLocalFileHeader {
    uint32_t signature; // 0x04034b50
    uint16_t version;
    uint16_t flags;
    uint16_t compression;
    uint16_t modTime;
    uint16_t modDate;
    uint32_t crc32;
    uint32_t compressedSize;
    uint32_t uncompressedSize;
    uint16_t fileNameLength;
    uint16_t extraFieldLength;
};

struct ZipCentralDirectoryHeader {
    uint32_t signature; // 0x02014b50
    uint16_t versionMadeBy;
    uint16_t versionNeeded;
    uint16_t flags;
    uint16_t compression;
    uint16_t modTime;
    uint16_t modDate;
    uint32_t crc32;
    uint32_t compressedSize;
    uint32_t uncompressedSize;
    uint16_t fileNameLength;
    uint16_t extraFieldLength;
    uint16_t fileCommentLength;
    uint16_t diskNumberStart;
    uint16_t internalAttributes;
    uint32_t externalAttributes;
    uint32_t localHeaderOffset;
};

struct ZipEndOfCentralDirectory {
    uint32_t signature; // 0x06054b50
    uint16_t diskNumber;
    uint16_t diskWithStartCD;
    uint16_t numEntriesOnDisk;
    uint16_t numEntriesTotal;
    uint32_t cdSize;
    uint32_t cdOffset;
    uint16_t commentLength;
};
#pragma pack(pop)

namespace next {

static bool g_warning_triggered = false;
std::atomic<bool> AntiTamper::g_overlay_active(false);
#ifdef ALLOW_LOGCAT
  #if ALLOW_LOGCAT == 1
    std::atomic<bool> AntiTamper::g_logcat_enabled(true);
  #else
    std::atomic<bool> AntiTamper::g_logcat_enabled(false);
  #endif
#else
    std::atomic<bool> AntiTamper::g_logcat_enabled(false); // DISABLED BY DEFAULT IN RELEASE
#endif
std::thread AntiTamper::g_render_thread;
ANativeWindow* AntiTamper::g_native_window = nullptr;
std::mutex AntiTamper::g_window_mutex;
float AntiTamper::g_touch_x = 0;
float AntiTamper::g_touch_y = 0;
int AntiTamper::g_touch_action = -1;
std::atomic<bool> AntiTamper::g_touch_pending(false);
std::atomic<bool> AntiTamper::g_download_clicked(false);
std::atomic<bool> AntiTamper::g_accept_clicked(false);
std::atomic<bool> AntiTamper::g_force_detection(false);
std::atomic<bool> AntiTamper::g_force_error(false);
std::atomic<OverlayView> AntiTamper::g_current_view(OverlayView::WARNING);
std::string AntiTamper::g_current_locale = "en";
std::string AntiTamper::g_url_to_open = "";
std::string AntiTamper::g_challenge = "";
int AntiTamper::g_countdown = 10;
AAssetManager* AntiTamper::g_asset_manager = nullptr;

void AntiTamper::setAssetManager(AAssetManager* manager) {
    g_asset_manager = manager;
}

std::string AntiTamper::getExpectedSignature() {
#ifdef EXPECTED_SIGNATURE
    return EXPECTED_SIGNATURE;
#else
    return XOR_STR("23:8E:00:94:89:D8:1A:13:E4:93:9D:42:59:8F:FA:FA:22:1B:80:52:4E:4B:61:7C:E6:E1:2B:6B:FE:C9:A1:91");
#endif
}

std::string AntiTamper::getExpectedPackageName() {
    return XOR_STR("ru.protonmod.next");
}

int AntiTamper::getExpectedVersionCode() {
    return next::getVersionCode();
}

std::string AntiTamper::getExpectedVersionName() {
#ifdef EXPECTED_VERSION_NAME
    return EXPECTED_VERSION_NAME;
#else
    return XOR_STR("12.0.0");
#endif
}

struct DlContext {
    std::vector<std::string>* detectedLibs;
    const std::string& pkgName;
};

static int dl_callback(struct dl_phdr_info *info, size_t size, void *data) {
    (void)size;
    auto* ctx = static_cast<DlContext*>(data);

    const char* soname = nullptr;
    // Extract SONAME from dynamic section for accurate identification
    for (int i = 0; i < info->dlpi_phnum; i++) {
        if (info->dlpi_phdr[i].p_type == PT_DYNAMIC) {
            ElfW(Dyn)* dyn = (ElfW(Dyn)*)(info->dlpi_addr + info->dlpi_phdr[i].p_vaddr);
            const char* strtab = nullptr;
            for (ElfW(Dyn)* d = dyn; d->d_tag != DT_NULL; d++) {
                if (d->d_tag == DT_STRTAB) {
                    strtab = (const char*)(info->dlpi_addr + d->d_un.d_ptr);
                }
            }
            if (strtab) {
                for (ElfW(Dyn)* d = dyn; d->d_tag != DT_NULL; d++) {
                    if (d->d_tag == DT_SONAME) {
                        soname = strtab + d->d_un.d_val;
                        break;
                    }
                }
            }
            break;
        }
    }

    std::string name;
    if (soname) {
        name = soname;
        LOGD("AntiTamper: Scanning library (SONAME): %s", soname);
    } else if (info->dlpi_name && strlen(info->dlpi_name) > 0) {
        name = info->dlpi_name;
        LOGD("AntiTamper: Scanning library (PATH): %s", name.c_str());
    }

    if (name.empty()) return 0;

    auto officialLibs = next::getOfficialLibs();
    for (const auto& official : officialLibs) {
        if (name.find(official) != std::string::npos) {
            bool alreadySeen = false;
            for (const auto& l : *(ctx->detectedLibs)) {
                if (l == official) {
                    alreadySeen = true;
                    break;
                }
            }
            if (!alreadySeen) {
                LOGD("AntiTamper: Identified official library: %s", official.c_str());
                ctx->detectedLibs->push_back(official);
            }
        }
    }
    return 0;
}

bool AntiTamper::checkPtrace() {
    if (ptrace(PTRACE_TRACEME, 0, 1, 0) < 0) {
        return false;
    }
    ptrace(PTRACE_DETACH, 0, 1, 0);
    return true;
}

bool AntiTamper::checkTracerPid() {
    std::ifstream status(XOR_STR("/proc/self/status"));
    if (!status.is_open()) return true; // Fail safe
    std::string line;
    while (std::getline(status, line)) {
        if (line.find(XOR_STR("TracerPid:")) == 0) {
            int pid = std::stoi(line.substr(10));
            if (pid != 0) return false;
            break;
        }
    }
    return true;
}

bool AntiTamper::checkDebuggable(JNIEnv* env, jobject context) {
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getApplicationInfoMethod = env->GetMethodID(contextClass, XOR_STR("getApplicationInfo").c_str(), XOR_STR("()Landroid/content/pm/ApplicationInfo;").c_str());
    jobject appInfo = env->CallObjectMethod(context, getApplicationInfoMethod);
    jclass appInfoClass = env->GetObjectClass(appInfo);
    jfieldID flagsField = env->GetFieldID(appInfoClass, XOR_STR("flags").c_str(), XOR_STR("I").c_str());
    int flags = env->GetIntField(appInfo, flagsField);
    // FLAG_DEBUGGABLE = 2
    return (flags & 2) != 0;
}

bool AntiTamper::checkRoot() {
    const char* rootPaths[] = {
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su"
    };
    for (const char* path : rootPaths) {
        if (access(path, F_OK) == 0) return true;
    }
    return false;
}

bool AntiTamper::checkEnvironment(JNIEnv* env) {
    std::string pkgName = getExpectedPackageName();
    std::vector<std::string> detectedLibs;

    bool isSecurityTest = false;
#if defined(DEBUG_BUILD) || defined(ANTITAMPER_TEST_BUILD)
    isSecurityTest = true;
#endif

    // Advanced checks
    if (!checkPtrace()) {
        reportSecurityEvent(env, XOR_STR("Debugger detected (ptrace)"));
        if (!isSecurityTest) return false;
    }
    if (!checkTracerPid()) {
        reportSecurityEvent(env, XOR_STR("Debugger detected (TracerPid)"));
        if (!isSecurityTest) return false;
    }

    // Use dl_iterate_phdr for reliable library detection (including libs inside APK)
    DlContext dl_ctx = { &detectedLibs, pkgName };
    dl_iterate_phdr(dl_callback, &dl_ctx);

    int libCount = (int)detectedLibs.size();

    std::ifstream maps(XOR_STR("/proc/self/maps"));
    if (!maps.is_open()) return (libCount > 0);

    std::string line;
    bool apkMapped = false;
    auto officialLibs = next::getOfficialLibs();

    while (std::getline(maps, line)) {
        // 1. Frida/Xposed/Bypass Tools Detection
        if (line.find(XOR_STR("frida")) != std::string::npos ||
            line.find(XOR_STR("xposed")) != std::string::npos ||
            line.find(XOR_STR("libgadget")) != std::string::npos ||
            line.find(XOR_STR("substrate")) != std::string::npos) {
            LOGE("AntiTamper: Suspicious library detected in memory: %s", line.c_str());
            reportSecurityEvent(env, XOR_STR("Suspicious library detected: ") + line);
            return false;
        }

        // 2. Check for App APK Mapping
        if (line.find(XOR_STR("base.apk")) != std::string::npos ||
            line.find(pkgName) != std::string::npos) {
            apkMapped = true;
        }

        // 3. Detect potentially malicious injections
        if (line.find(XOR_STR(".so")) != std::string::npos &&
           (line.find(XOR_STR("/data/app/")) != std::string::npos || line.find(pkgName) != std::string::npos)) {

            bool isDebug = false;
#if defined(DEBUG_BUILD) || defined(ANTITAMPER_TEST_BUILD)
            isDebug = true;
#endif
            // Whitelist Android Studio debugger agents in debug builds
            if (isDebug && line.find(XOR_STR("code_cache/startup_agents/")) != std::string::npos) {
                continue;
            }

            bool foundInWhitelist = false;
            for (const auto& official : officialLibs) {
                if (line.find(official) != std::string::npos) {
                    foundInWhitelist = true;
                    break;
                }
            }

            // If it's an .so in our app context but NOT in our whitelist
            if (!foundInWhitelist) {
                LOGE("AntiTamper: Unofficial library mapping detected: %s", line.c_str());
                reportSecurityEvent(env, XOR_STR("Unofficial library mapping: ") + line);
                return false;
            }
        }
    }

    LOGD("AntiTamper: Scan results - Libs: %d/%d, APK Mapped: %d", libCount, EXPECTED_LIB_COUNT, apkMapped);

    // Success if we found all libraries OR if the APK is mapped (meaning libs are likely inside it)
    if (libCount == 0 && !apkMapped) {
        LOGE("AntiTamper: Critical failure - no app libraries or APK found in memory scan.");
        reportSecurityEvent(env, XOR_STR("No app integrity found in memory scan"));
        return false;
    }

    if (libCount > EXPECTED_LIB_COUNT) {
        LOGE("AntiTamper: Library count mismatch! Found: %d, Expected: %d", libCount, EXPECTED_LIB_COUNT);
        reportSecurityEvent(env, XOR_STR("Lib count mismatch: ") + std::to_string(libCount));
        return false;
    }

    return true;
}

bool AntiTamper::checkHooks(JNIEnv* env, jobject context) {
    // 1. Check for Mocked PackageManager
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageManagerMethod = env->GetMethodID(contextClass, XOR_STR("getPackageManager").c_str(), XOR_STR("()Landroid/content/pm/PackageManager;").c_str());
    jobject packageManager = env->CallObjectMethod(context, getPackageManagerMethod);
    jclass pmClass = env->GetObjectClass(packageManager);
    jmethodID getNameMethod = env->GetMethodID(env->FindClass(XOR_STR("java/lang/Class").c_str()), XOR_STR("getName").c_str(), XOR_STR("()Ljava/lang/String;").c_str());
    jstring className = (jstring)env->CallObjectMethod(pmClass, getNameMethod);
    const char* classNameChars = env->GetStringUTFChars(className, nullptr);
    std::string pmClassName(classNameChars);
    env->ReleaseStringUTFChars(className, classNameChars);

    LOGD("PackageManager class: %s", pmClassName.c_str());
    // Official class is usually android.app.ApplicationPackageManager
    if (pmClassName.find(XOR_STR("Proxy")) != std::string::npos ||
        pmClassName.find(XOR_STR("Mock")) != std::string::npos ||
        pmClassName.find(XOR_STR("wrapper")) != std::string::npos) {
        LOGE("AntiTamper: Hooked PackageManager detected!");
        reportSecurityEvent(env, XOR_STR("Hooked PackageManager: ") + pmClassName);
        return false;
    }

    return true;
}

std::string AntiTamper::getApkPathFromMaps() {
    std::ifstream maps(XOR_STR("/proc/self/maps"));
    std::string line;
    std::string pkgName = getExpectedPackageName();
    while (std::getline(maps, line)) {
        if (line.find(pkgName) != std::string::npos && line.find(XOR_STR(".apk")) != std::string::npos) {
            size_t pos = line.find('/');
            if (pos != std::string::npos) {
                return line.substr(pos);
            }
        }
    }
    return "";
}

bool AntiTamper::verifyApkArchive(JNIEnv* env) {
    std::string apkPath = getApkPathFromMaps();
    if (apkPath.empty()) {
        LOGE("AntiTamper: Could not find APK path in memory maps");
        return false;
    }

    LOGD("AntiTamper: Verifying APK at: %s", apkPath.c_str());

    int fd = open(apkPath.c_str(), O_RDONLY);
    if (fd < 0) {
        LOGE("AntiTamper: Failed to open APK file: %s", apkPath.c_str());
        return false;
    }

    struct stat st;
    fstat(fd, &st);
    size_t size = st.st_size;

    void* mapped = mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);

    if (mapped == MAP_FAILED) {
        LOGE("AntiTamper: Failed to mmap APK");
        return false;
    }

    const uint8_t* data = static_cast<const uint8_t*>(mapped);
    bool success = false;

    // Find EOCD by scanning from the end (max comment size is 64KB)
    const uint32_t eocd_sig = 0x06054b50;
    size_t scan_limit = (size > 65535 + 22) ? size - 65535 - 22 : 0;
    const ZipEndOfCentralDirectory* eocd = nullptr;

    for (size_t i = size - 22; i >= scan_limit; --i) {
        if (*(uint32_t*)(data + i) == eocd_sig) {
            eocd = (const ZipEndOfCentralDirectory*)(data + i);
            break;
        }
    }

    if (!eocd) {
        LOGE("AntiTamper: Could not find ZIP EOCD");
        munmap(mapped, size);
        return false;
    }

    const uint8_t* cd_ptr = data + eocd->cdOffset;
    std::vector<std::string> foundOfficialLibs;
    bool unauthorizedLibFound = false;
    auto officialLibs = next::getOfficialLibs();

    for (int i = 0; i < eocd->numEntriesTotal; ++i) {
        const ZipCentralDirectoryHeader* header = (const ZipCentralDirectoryHeader*)cd_ptr;
        if (header->signature != 0x02014b50) break;

        std::string fileName((const char*)(cd_ptr + sizeof(ZipCentralDirectoryHeader)), header->fileNameLength);

        // Check for libraries in lib/ directory
        if (fileName.find(XOR_STR("lib/")) == 0 && fileName.find(XOR_STR(".so")) != std::string::npos) {
            LOGD("AntiTamper: Found lib in APK: %s", fileName.c_str());

            bool isOfficial = false;
            for (const auto& official : officialLibs) {
                if (fileName.find(official) != std::string::npos) {
                    isOfficial = true;
                    foundOfficialLibs.push_back(official);
                    break;
                }
            }

            if (!isOfficial) {
                // Potential unauthorized library
                LOGE("AntiTamper: Unauthorized library found in APK: %s", fileName.c_str());
                unauthorizedLibFound = true;
                break;
            }
        }

        cd_ptr += sizeof(ZipCentralDirectoryHeader) + header->fileNameLength + header->extraFieldLength + header->fileCommentLength;
    }

    if (unauthorizedLibFound) {
        reportSecurityEvent(env, XOR_STR("Unauthorized library in APK archive"));
        success = false;
    } else {
        // Verify all official libs are present (at least for one architecture)
        int uniqueOfficialFound = 0;
        for (const auto& official : officialLibs) {
            for (const auto& found : foundOfficialLibs) {
                if (found == official) {
                    uniqueOfficialFound++;
                    break;
                }
            }
        }

        LOGD("AntiTamper: Official libs in APK: %d/%d", uniqueOfficialFound, EXPECTED_LIB_COUNT);
        if (uniqueOfficialFound < EXPECTED_LIB_COUNT) {
            LOGE("AntiTamper: Missing official libraries in APK archive!");
            reportSecurityEvent(env, XOR_STR("Missing official libraries in APK"));
            success = false;
        } else {
            success = true;
        }
    }

    munmap(mapped, size);
    return success;
}

bool AntiTamper::checkStringIntegrity(JNIEnv* env, jobject context) {
    struct KeyPair {
        std::string key;
        std::string expected;
    };
    // Integrity check is performed against English locale as a baseline
    std::vector<KeyPair> checks = {
        {XOR_STR("tamper_warning_title"), getProtectedString(XOR_STR("en"), XOR_STR("tamper_warning_title"))},
        {XOR_STR("tamper_warning_desc"), getProtectedString(XOR_STR("en"), XOR_STR("tamper_warning_desc"))},
        {XOR_STR("tamper_btn_accept_risks"), getProtectedString(XOR_STR("en"), XOR_STR("tamper_btn_accept_risks"))},
        {XOR_STR("tamper_btn_download_official"), getProtectedString(XOR_STR("en"), XOR_STR("tamper_btn_download_official"))}
    };

    bool allGood = true;
    for (const auto& pair : checks) {
        std::string resStr = getStringFromResources(env, context, pair.key);
        if (!resStr.empty() && resStr != pair.expected) {
            reportStringMismatch(env, pair.key, pair.expected, resStr);
            allGood = false;
        }
    }
    return allGood;
}

std::string AntiTamper::getStringFromResources(JNIEnv* env, jobject context, const std::string& key) {
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getResourcesMethod = env->GetMethodID(contextClass, XOR_STR("getResources").c_str(), XOR_STR("()Landroid/content/res/Resources;").c_str());
    jobject resources = env->CallObjectMethod(context, getResourcesMethod);
    if (!resources) return "";

    jclass resourcesClass = env->GetObjectClass(resources);
    jmethodID getIdentifierMethod = env->GetMethodID(resourcesClass, XOR_STR("getIdentifier").c_str(), XOR_STR("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I").c_str());

    jstring jkey = env->NewStringUTF(key.c_str());
    jstring jtype = env->NewStringUTF(XOR_STR("string").c_str());
    jstring jpkg = env->NewStringUTF(getExpectedPackageName().c_str());

    int id = env->CallIntMethod(resources, getIdentifierMethod, jkey, jtype, jpkg);

    env->DeleteLocalRef(jkey);
    env->DeleteLocalRef(jtype);
    env->DeleteLocalRef(jpkg);

    if (id == 0) return "";

    jmethodID getStringMethod = env->GetMethodID(resourcesClass, XOR_STR("getString").c_str(), XOR_STR("(I)Ljava/lang/String;").c_str());
    jstring jval = (jstring)env->CallObjectMethod(resources, getStringMethod, id);
    if (!jval) return "";

    const char* valChars = env->GetStringUTFChars(jval, nullptr);
    std::string val(valChars);
    env->ReleaseStringUTFChars(jval, valChars);
    env->DeleteLocalRef(jval);

    return val;
}

bool AntiTamper::check(JNIEnv* env, jobject context) {
    LOGD("Check started");

    // Advanced Checks
    if (checkDebuggable(env, context)) {
        reportSecurityEvent(env, XOR_STR("Application marked as debuggable"));
#if !defined(DEBUG_BUILD) && !defined(ANTITAMPER_TEST_BUILD)
        return false;
#endif
    }

    // Environment & Hook Checks
    if (!checkEnvironment(env)) return false;
    if (!checkHooks(env, context)) return false;

    // APK Archive Integrity Check
    if (!verifyApkArchive(env)) return false;

    // String Integrity Check
    if (!checkStringIntegrity(env, context)) {
        g_current_view = OverlayView::INTEGRITY_FAILURE;
        return false;
    }

    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageNameMethod = env->GetMethodID(contextClass, XOR_STR("getPackageName").c_str(), XOR_STR("()Ljava/lang/String;").c_str());
    jstring packageName = (jstring)env->CallObjectMethod(context, getPackageNameMethod);
    const char* packageNameChars = env->GetStringUTFChars(packageName, nullptr);
    std::string currentPackageName(packageNameChars);
    env->ReleaseStringUTFChars(packageName, packageNameChars);

    LOGD("Package name: %s", currentPackageName.c_str());

    std::string expectedPkg = getExpectedPackageName();
    if (currentPackageName != expectedPkg && currentPackageName != expectedPkg + XOR_STR(".nightly")) {
        LOGE("AntiTamper: Package name mismatch! Found: %s", currentPackageName.c_str());
        reportSecurityEvent(env, XOR_STR("Package name mismatch: ") + currentPackageName);
        return false;
    }

    // 3. APK Size Check
    jmethodID getPackageResourcePathMethod = env->GetMethodID(contextClass, XOR_STR("getPackageResourcePath").c_str(), XOR_STR("()Ljava/lang/String;").c_str());
    jstring apkPath = (jstring)env->CallObjectMethod(context, getPackageResourcePathMethod);
    const char* apkPathChars = env->GetStringUTFChars(apkPath, nullptr);
    struct stat st;
    if (stat(apkPathChars, &st) == 0) {
        bool isDebugLike = false;
#if defined(DEBUG_BUILD) || defined(ANTITAMPER_TEST_BUILD)
        isDebugLike = true;
#endif
        long long maxSize = isDebugLike ? next::getDebugApkSize() : next::getReleaseApkSize();
        LOGD("APK size: %lld bytes (Limit: %lld)", (long long)st.st_size, maxSize);
        if (st.st_size > maxSize) {
            LOGE("AntiTamper: APK size exceeded limit!");
            reportSecurityEvent(env, XOR_STR("APK size limit exceeded: ") + std::to_string(st.st_size));
            env->ReleaseStringUTFChars(apkPath, apkPathChars);
            return false;
        }
    }
    env->ReleaseStringUTFChars(apkPath, apkPathChars);

    // 4. Asset Integrity Check
    if (g_asset_manager) {
        AAsset* bypassApk = AAssetManager_open(g_asset_manager, XOR_STR("base.apk").c_str(), AASSET_MODE_BUFFER);
        if (bypassApk) {
            LOGE("AntiTamper: Illegal 'base.apk' found in assets!");
            reportSecurityEvent(env, XOR_STR("Illegal base.apk found in assets"));
            AAsset_close(bypassApk);
            return false;
        }
    }

    // Check Version Code and Name
    jmethodID getPackageManagerMethod = env->GetMethodID(contextClass, XOR_STR("getPackageManager").c_str(), XOR_STR("()Landroid/content/pm/PackageManager;").c_str());
    jobject packageManager = env->CallObjectMethod(context, getPackageManagerMethod);
    jclass packageManagerClass = env->GetObjectClass(packageManager);
    jmethodID getPackageInfoMethod = env->GetMethodID(packageManagerClass, XOR_STR("getPackageInfo").c_str(), XOR_STR("(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;").c_str());

    // GET_SIGNING_CERTIFICATES = 0x08000000 (API 28+)
    jobject packageInfo = env->CallObjectMethod(packageManager, getPackageInfoMethod, packageName, 0x08000000);
    if (!packageInfo) {
        LOGE("AntiTamper: Could not get package info");
        reportSecurityEvent(env, XOR_STR("Could not get package info"));
        return false;
    }
    jclass packageInfoClass = env->GetObjectClass(packageInfo);

    jfieldID versionCodeField = env->GetFieldID(packageInfoClass, XOR_STR("versionCode").c_str(), XOR_STR("I").c_str());
    int currentVersionCode = env->GetIntField(packageInfo, versionCodeField);
    LOGD("Version code: %d, Expected: %d", currentVersionCode, getExpectedVersionCode());

    if (currentVersionCode != getExpectedVersionCode()) {
        LOGE("AntiTamper: Version code mismatch!");
        reportSecurityEvent(env, XOR_STR("Version code mismatch. Found: ") + std::to_string(currentVersionCode) + XOR_STR(", Expected: ") + std::to_string(getExpectedVersionCode()));
        return false;
    }

    jfieldID versionNameField = env->GetFieldID(packageInfoClass, XOR_STR("versionName").c_str(), XOR_STR("Ljava/lang/String;").c_str());
    jstring versionName = (jstring)env->GetObjectField(packageInfo, versionNameField);
    const char* versionNameChars = env->GetStringUTFChars(versionName, nullptr);
    std::string currentVersionName(versionNameChars);
    env->ReleaseStringUTFChars(versionName, versionNameChars);

    std::string expectedVersion = getExpectedVersionName();
    LOGD("Version name: %s, Expected: %s", currentVersionName.c_str(), expectedVersion.c_str());

    if (currentVersionName != expectedVersion && currentVersionName != expectedVersion + XOR_STR("-nightly")) {
        LOGE("AntiTamper: Version name mismatch!");
        reportSecurityEvent(env, XOR_STR("Version name mismatch. Found: ") + currentVersionName + XOR_STR(", Expected: ") + expectedVersion);
        return false;
    }

    // Check Signature
    jfieldID signingInfoField = env->GetFieldID(packageInfoClass, XOR_STR("signingInfo").c_str(), XOR_STR("Landroid/content/pm/SigningInfo;").c_str());
    jobject signingInfo = env->GetObjectField(packageInfo, signingInfoField);
    if (!signingInfo) {
        LOGE("AntiTamper: Could not get signing info");
        reportSecurityEvent(env, XOR_STR("Could not get signing info"));
        return false;
    }
    jclass signingInfoClass = env->GetObjectClass(signingInfo);
    jmethodID getApkContentsSignersMethod = env->GetMethodID(signingInfoClass, XOR_STR("getApkContentsSigners").c_str(), XOR_STR("()[Landroid/content/pm/Signature;").c_str());
    jobjectArray signers = (jobjectArray)env->CallObjectMethod(signingInfo, getApkContentsSignersMethod);
    if (!signers || env->GetArrayLength(signers) == 0) {
        LOGE("AntiTamper: No signers found");
        reportSecurityEvent(env, XOR_STR("No signers found"));
        return false;
    }

    jobject firstSigner = env->GetObjectArrayElement(signers, 0);
    jclass signatureClass = env->GetObjectClass(firstSigner);
    jmethodID toByteArrayMethod = env->GetMethodID(signatureClass, XOR_STR("toByteArray").c_str(), XOR_STR("()[B").c_str());
    jbyteArray certBytes = (jbyteArray)env->CallObjectMethod(firstSigner, toByteArrayMethod);

    // Calculate SHA-256 of the certificate
    jclass messageDigestClass = env->FindClass(XOR_STR("java/security/MessageDigest").c_str());
    jmethodID getInstanceMethod = env->GetStaticMethodID(messageDigestClass, XOR_STR("getInstance").c_str(), XOR_STR("(Ljava/lang/String;)Ljava/security/MessageDigest;").c_str());
    jstring sha256String = env->NewStringUTF(XOR_STR("SHA-256").c_str());
    jobject digest = env->CallStaticObjectMethod(messageDigestClass, getInstanceMethod, sha256String);
    jmethodID digestMethod = env->GetMethodID(messageDigestClass, XOR_STR("digest").c_str(), XOR_STR("([B)[B").c_str());
    jbyteArray hashBytes = (jbyteArray)env->CallObjectMethod(digest, digestMethod, certBytes);

    jsize hashLen = env->GetArrayLength(hashBytes);
    jbyte* hashPtr = env->GetByteArrayElements(hashBytes, nullptr);

    std::stringstream ss;
    for (int i = 0; i < hashLen; ++i) {
        ss << std::uppercase << std::setfill('0') << std::setw(2) << std::hex << (int)(hashPtr[i] & 0xFF);
        if (i < hashLen - 1) ss << ":";
    }
    std::string currentSignature = ss.str();
    env->ReleaseByteArrayElements(hashBytes, hashPtr, JNI_ABORT);

    LOGD("Signature: %s", currentSignature.c_str());
    LOGD("Expected: %s", getExpectedSignature().c_str());

    if (currentSignature != getExpectedSignature()) {
        LOGE("AntiTamper: Signature mismatch!");
        reportSecurityEvent(env, XOR_STR("Signature mismatch. Found: ") + currentSignature);
        return false;
    }

    return true;
}

std::string AntiTamper::getProtectedString(const std::string& locale, const std::string& key) {
    auto l = locale;
    if (l != XOR_STR("ru") && l != XOR_STR("fa") && l != XOR_STR("be") && l != XOR_STR("uk") && l != XOR_STR("kk") && l != XOR_STR("zh")) l = XOR_STR("en");

    if (key == XOR_STR("tamper_warning_title")) {
        if (l == XOR_STR("ru")) return XOR_STR("ОБНАРУЖЕНА НЕОФИЦИАЛЬНАЯ СБОРКА!");
        if (l == XOR_STR("fa")) return XOR_STR("نسخه غیررسمی شناسایی شد!");
        if (l == XOR_STR("be")) return XOR_STR("ВЫЯЎЛЕНА НЕАФІЦЫЙНАЯ ЗБОРКА!");
        if (l == XOR_STR("uk")) return XOR_STR("ВИЯВЛЕНО НЕОФІЦІЙНУ ЗБІРКУ!");
        if (l == XOR_STR("kk")) return XOR_STR("РЕСМИ ЕМЕС ЖИНАҚ АНЫҚТАЛДЫ!");
        if (l == XOR_STR("zh")) return XOR_STR("检测到非官方版本！");
        return XOR_STR("UNOFFICIAL BUILD DETECTED!");
    }
    if (key == XOR_STR("tamper_warning_desc")) {
        if (l == XOR_STR("ru")) return XOR_STR("Эта версия Proton VPN-Next была модифицирована неизвестной третьей стороной. Ваша безопасность, конфиденциальность и данные находятся под ВЫСОКИМ РИСКОМ.");
        if (l == XOR_STR("fa")) return XOR_STR("این نسخه از Proton VPN-Next توسط یک شخص ثالث ناشناس اصلاح شده است. امنیت، حریم خصوصی و داده‌های شما در معرض خطر بسیار بالایی قرار دارند.");
        if (l == XOR_STR("be")) return XOR_STR("Гэтая версія Proton VPN-Next была мадыфікавана невядомым трэцім бокам. Ваша бяспека, прыватнасць і даныя знаходзяцца пад ВЫСОКАЙ РЫЗЫКАЙ.");
        if (l == XOR_STR("uk")) return XOR_STR("Ця версія Proton VPN-Next була модифікована невідомою третьою стороною. Ваша безпека, конфіденційність та дані знаходяться пад ВИСОКИМ РИЗИКОМ.");
        if (l == XOR_STR("kk")) return XOR_STR("Proton VPN-Next нұсқасын белгісіз үшінші тарап өзгерткен. Сіздің қауіпсіздігіңіз, құпиялылығыңыз бен деректеріңізге ЖОҒАРЫ ҚАУІП төніп тұр.");
        if (l == XOR_STR("zh")) return XOR_STR("此版本的 Proton VPN-Next 已被未知的第三方修改。您的安全、隐私 and 数据正面临高度风险。");
        return XOR_STR("This version of Proton VPN-Next has been modified by an unknown third party. Your security, privacy, and data are at HIGH RISK.");
    }
    if (key == XOR_STR("tamper_btn_accept_risks")) {
        if (l == XOR_STR("ru")) return XOR_STR("Я осознаю риск и принимаю последствия");
        if (l == XOR_STR("fa")) return XOR_STR("خطر را می‌پذیرم");
        if (l == XOR_STR("be")) return XOR_STR("Я ўсведамляю рызыку і прымаю наступствы");
        if (l == XOR_STR("uk")) return XOR_STR("Я усвідомлюю ризик і приймаю наслідки");
        if (l == XOR_STR("kk")) return XOR_STR("Мен қауіпті түсінемін және салдарын қабылдаймын");
        if (l == XOR_STR("zh")) return XOR_STR("我了解风险并接受后果");
        return XOR_STR("I Understand the Risk and Accept Consequences");
    }
    if (key == XOR_STR("tamper_btn_download_official")) {
        if (l == XOR_STR("ru")) return XOR_STR("Скачать оригинал");
        if (l == XOR_STR("fa")) return XOR_STR("دریافت نسخه اصلی");
        if (l == XOR_STR("be")) return XOR_STR("Спампаваць арыгінал");
        if (l == XOR_STR("uk")) return XOR_STR("Завантажити оригінал");
        if (l == XOR_STR("kk")) return XOR_STR("Түпнұсқаны жүктеп алу");
        if (l == XOR_STR("zh")) return XOR_STR("下载官方版本");
        return XOR_STR("Get Official App");
    }
    if (key == XOR_STR("tamper_btn_back")) {
        if (l == XOR_STR("ru")) return XOR_STR("Назад");
        if (l == XOR_STR("fa")) return XOR_STR("بازگشت");
        if (l == XOR_STR("be") || l == XOR_STR("uk")) return XOR_STR("Назад");
        if (l == XOR_STR("kk")) return XOR_STR("Артқа");
        if (l == XOR_STR("zh")) return XOR_STR("返回");
        return XOR_STR("Back");
    }
    if (key == XOR_STR("url_github")) return XOR_STR("https://github.com/SMH01-MIRRORS/ProtonVPN-Next-MIRROR");
    if (key == XOR_STR("url_codeberg")) return XOR_STR("https://codeberg.org/SMH01/ProtonVPN-Next");
    if (key == XOR_STR("url_telegram")) return XOR_STR("https://t.me/ProtonVPN_MOD");
    if (key == XOR_STR("url_website")) return XOR_STR("https://home.protonnext.qzz.io/");

    return "";
}

int AntiTamper::getTamperAckCount(JNIEnv* env, jobject context) {
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getSharedPreferencesMethod = env->GetMethodID(contextClass, XOR_STR("getSharedPreferences").c_str(), XOR_STR("(Ljava/lang/String;I)Landroid/content/SharedPreferences;").c_str());
    jstring prefName = env->NewStringUTF(XOR_STR("next_security_prefs").c_str());
    jobject sharedPrefs = env->CallObjectMethod(context, getSharedPreferencesMethod, prefName, 0);
    jclass sharedPrefsClass = env->GetObjectClass(sharedPrefs);
    jmethodID getIntMethod = env->GetMethodID(sharedPrefsClass, XOR_STR("getInt").c_str(), XOR_STR("(Ljava/lang/String;I)I").c_str());
    jstring key = env->NewStringUTF(XOR_STR("tamper_ack_count").c_str());
    int count = env->CallIntMethod(sharedPrefs, getIntMethod, key, 0);
    env->DeleteLocalRef(prefName);
    env->DeleteLocalRef(key);
    return count;
}

void AntiTamper::incrementTamperAckCount(JNIEnv* env, jobject context) {
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getSharedPreferencesMethod = env->GetMethodID(contextClass, XOR_STR("getSharedPreferences").c_str(), XOR_STR("(Ljava/lang/String;I)Landroid/content/SharedPreferences;").c_str());
    jstring prefName = env->NewStringUTF(XOR_STR("next_security_prefs").c_str());
    jobject sharedPrefs = env->CallObjectMethod(context, getSharedPreferencesMethod, prefName, 0);
    jclass sharedPrefsClass = env->GetObjectClass(sharedPrefs);
    jmethodID editMethod = env->GetMethodID(sharedPrefsClass, XOR_STR("edit").c_str(), XOR_STR("()Landroid/content/SharedPreferences$Editor;").c_str());
    jobject editor = env->CallObjectMethod(sharedPrefs, editMethod);
    jclass editorClass = env->GetObjectClass(editor);
    int currentCount = getTamperAckCount(env, context);
    jmethodID putIntMethod = env->GetMethodID(editorClass, XOR_STR("putInt").c_str(), XOR_STR("(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;").c_str());
    jstring key = env->NewStringUTF(XOR_STR("tamper_ack_count").c_str());
    env->CallObjectMethod(editor, putIntMethod, key, currentCount + 1);
    jmethodID applyMethod = env->GetMethodID(editorClass, XOR_STR("apply").c_str(), XOR_STR("()V").c_str());
    env->CallVoidMethod(editor, applyMethod);
    env->DeleteLocalRef(prefName);
    env->DeleteLocalRef(key);
}

std::string AntiTamper::generateChallenge() {
    uint64_t c = (static_cast<uint64_t>(rand()) << 32) | static_cast<uint64_t>(rand());
    std::stringstream ss;
    ss << std::hex << c;
    return ss.str();
}

bool AntiTamper::verifyResponse(const std::string& response) {
    (void)response;
    return true;
}

void AntiTamper::reportBypassAttempt(JNIEnv* env, const std::string& reason) {
    LOGE("CRITICAL SECURITY BREACH: %s", reason.c_str());
    reportSecurityEvent(env, XOR_STR("CRITICAL BREACH: ") + reason);
    abort();
}

void AntiTamper::reportStringMismatch(JNIEnv* env, const std::string& key, const std::string& expected, const std::string& got) {
    std::string msg = XOR_STR("Error: Someone tried to steal your work\nKey: ") + key +
                     XOR_STR("\nExpected (реальная строка): ") + expected +
                     XOR_STR("\nGot (строка мододела): ") + got;
    reportSecurityEvent(env, msg);
}

void AntiTamper::reportSecurityEvent(JNIEnv* env, const std::string& event) {
    // 1. Log to Sentry Native SDK (Always-On)
    sentry_value_t s_event = sentry_value_new_event();
    sentry_value_set_by_key(s_event, "message", sentry_value_new_string(event.c_str()));
    sentry_value_set_by_key(s_event, "level", sentry_value_new_string("fatal"));

    sentry_value_t tags = sentry_value_new_object();
    sentry_value_set_by_key(tags, "security", sentry_value_new_string("true"));
    sentry_value_set_by_key(tags, "native", sentry_value_new_string("true"));
    sentry_value_set_by_key(s_event, "tags", tags);

    sentry_capture_event(s_event);

    // 2. Log to Kotlin (Traditional way)
    env->ExceptionClear();
    jclass helperClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/NextVpnManager").c_str());
    if (helperClass) {
        jmethodID logMethod = env->GetStaticMethodID(helperClass, XOR_STR("logSecurityEvent").c_str(), XOR_STR("(Ljava/lang/String;)V").c_str());
        if (logMethod) {
            jstring jevent = env->NewStringUTF(event.c_str());
            env->CallStaticVoidMethod(helperClass, logMethod, jevent);
            env->DeleteLocalRef(jevent);
        }
    }
}

void AntiTamper::verifyCriticalIntegrity(JNIEnv* env) {
    // 1. Hook/Integrity Check for FlavorInitializer
    jclass flavorClass = env->FindClass(XOR_STR("ru/protonmod/next/FlavorInitializer").c_str());
    if (!flavorClass) {
        reportSecurityEvent(env, XOR_STR("CRITICAL: FlavorInitializer class NOT FOUND! (Likely cut by smali)"));
        env->ExceptionClear();
    } else {
        jmethodID initMethod = env->GetStaticMethodID(flavorClass, XOR_STR("initialize").c_str(), XOR_STR("(Landroid/content/Context;)V").c_str());
        if (!initMethod) {
            reportSecurityEvent(env, XOR_STR("CRITICAL: FlavorInitializer.initialize() NOT FOUND! (Likely cut by smali)"));
            env->ExceptionClear();
        }

        // Honeypot: Check if someone called/modified our fake method
        jmethodID fakeMethod = env->GetStaticMethodID(flavorClass, XOR_STR("verifySecurityEnvironment").c_str(), XOR_STR("(Landroid/content/Context;)V").c_str());
        if (!fakeMethod) {
             reportSecurityEvent(env, XOR_STR("Honeypot: FlavorInitializer.verifySecurityEnvironment() removed!"));
        }
    }

    // 2. Token Integrity Check (Honeypot)
    jclass managerClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/NextVpnManager").c_str());
    if (managerClass) {
        jfieldID tokenField = env->GetStaticFieldID(managerClass, XOR_STR("SECURITY_VERIFICATION_TOKEN").c_str(), XOR_STR("Ljava/lang/String;").c_str());
        if (tokenField) {
            jstring token = (jstring)env->GetStaticObjectField(managerClass, tokenField);
            const char* tokenChars = env->GetStringUTFChars(token, nullptr);
            std::string currentToken(tokenChars);
            env->ReleaseStringUTFChars(token, tokenChars);

            std::string expectedToken = XOR_STR("7b74cef88678ecb3e6047ac6b4abf139");
            if (currentToken != expectedToken) {
                reportSecurityEvent(env, XOR_STR("Honeypot: SECURITY_VERIFICATION_TOKEN tampered! Got: ") + currentToken);
            }
        } else {
             reportSecurityEvent(env, XOR_STR("Honeypot: NextVpnManager.SECURITY_VERIFICATION_TOKEN removed!"));
        }

        // Honeypot: Check for performLegacyIntegrityCheck method
        jmethodID legacyMethod = env->GetMethodID(managerClass, XOR_STR("performLegacyIntegrityCheck").c_str(), XOR_STR("()Z").c_str());
        if (!legacyMethod) {
             reportSecurityEvent(env, XOR_STR("Honeypot: NextVpnManager.performLegacyIntegrityCheck() removed!"));
        }
    }

    // 3. Protected String Integrity Verification
    struct StringVerify {
        std::string key;
        std::string expected;
    };

    std::vector<StringVerify> criticalStrings = {
        {XOR_STR("url_github"), XOR_STR("https://github.com/SMH01-MIRRORS/ProtonVPN-Next-MIRROR")},
        {XOR_STR("url_codeberg"), XOR_STR("https://codeberg.org/SMH01/ProtonVPN-Next")},
        {XOR_STR("url_telegram"), XOR_STR("https://t.me/ProtonVPN_MOD")},
        {XOR_STR("url_website"), XOR_STR("https://home.protonnext.qzz.io/")}
    };

    for (const auto& sv : criticalStrings) {
        std::string actual = getProtectedString(XOR_STR("en"), sv.key);
        if (actual != sv.expected) {
            reportSecurityEvent(env, XOR_STR("CRITICAL: Protected string tampered! Key: ") + sv.key + XOR_STR(", Actual: ") + actual);
        }
    }
}

void AntiTamper::onActivityResumed(JNIEnv* env, jobject activity) {
    LOGD("onActivityResumed triggered");

    // Perform critical integrity checks on resume
    verifyCriticalIntegrity(env);

    // Detect locale
    jclass localeClass = env->FindClass(XOR_STR("java/util/Locale").c_str());
    jmethodID getDefaultMethod = env->GetStaticMethodID(localeClass, "getDefault", "()Ljava/util/Locale;");
    jobject localeObj = env->CallStaticObjectMethod(localeClass, getDefaultMethod);
    jmethodID getLanguageMethod = env->GetMethodID(localeClass, "getLanguage", "()Ljava/lang/String;");
    jstring lang = (jstring)env->CallObjectMethod(localeObj, getLanguageMethod);
    const char* langChars = env->GetStringUTFChars(lang, nullptr);
    g_current_locale = std::string(langChars);
    env->ReleaseStringUTFChars(lang, langChars);
    LOGD("Current locale detected: %s", g_current_locale.c_str());

    g_current_view = OverlayView::WARNING;

    if (g_force_error || TEST_FORCE_CRASH) {
        reportBypassAttempt(env, XOR_STR("Forced security error for debugging"));
    }

    bool result = check(env, activity);
    if (g_force_detection || TEST_FORCE_DETECTION) {
        LOGD("Forcing tamper detection for debugging");
        result = false;
    }
    LOGD("Integrity check result: %d", result);
    g_vpn_manager.setTamperDetected(!result);
    if (!result) {
        reportSecurityEvent(env, XOR_STR("Tamper detected - showing native overlay"));
        int ackCount = getTamperAckCount(env, activity);
        LOGD("Tamper ack count: %d", ackCount);
        if (ackCount < 2) {
            g_warning_triggered = true;
            LOGD("Triggering native overlay...");
            showNativeOverlay(env, activity);
        } else {
            LOGD("Warning already acknowledged (ackCount >= 2)");
            g_vpn_manager.setWarningAcknowledged(true);
        }
    }
}

void AntiTamper::showNativeOverlay(JNIEnv* env, jobject activity) {
    if (g_overlay_active) return;
    jclass helperClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/NextVpnManager").c_str());
    if (helperClass) {
        jmethodID showOverlayMethod = env->GetStaticMethodID(helperClass, XOR_STR("createNativeOverlay").c_str(), XOR_STR("(Landroid/app/Activity;)V").c_str());
        if (showOverlayMethod) {
            env->CallStaticVoidMethod(helperClass, showOverlayMethod, activity);
        }
    }
}

void AntiTamper::initImGui(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(g_window_mutex);
    if (g_native_window != nullptr) ANativeWindow_release(g_native_window);
    g_native_window = window;
    if (g_native_window != nullptr) {
        ANativeWindow_acquire(g_native_window);
        if (!g_overlay_active) {
            g_overlay_active = true;
            if (g_render_thread.joinable()) g_render_thread.join();
            g_render_thread = std::thread(renderLoop);
        }
    }
}

void AntiTamper::handleInputEvent(float x, float y, int action) {
    g_touch_x = x;
    g_touch_y = y;
    g_touch_action = action;
    g_touch_pending = true;
}

void AntiTamper::renderLoop() {
    LOGD("renderLoop started");
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) { LOGE("eglGetDisplay failed"); return; }

    if (!eglInitialize(display, nullptr, nullptr)) { LOGE("eglInitialize failed"); return; }

    const EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_BLUE_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_RED_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 16,
        EGL_NONE
    };

    EGLConfig config;
    EGLint numConfigs;
    if (!eglChooseConfig(display, configAttribs, &config, 1, &numConfigs)) { LOGE("eglChooseConfig failed"); return; }

    const EGLint contextAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    if (context == EGL_NO_CONTEXT) { LOGE("eglCreateContext failed"); return; }

    EGLSurface surface = EGL_NO_SURFACE;
    bool imgui_initialized = false;

    g_challenge = generateChallenge();
    g_countdown = 10;
    auto last_tick = std::chrono::steady_clock::now();

    LOGD("Entering render loop main part");
    while (g_overlay_active) {
        {
            std::lock_guard<std::mutex> lock(g_window_mutex);
            if (g_native_window != nullptr && surface == EGL_NO_SURFACE) {
                LOGD("Creating window surface...");
                surface = eglCreateWindowSurface(display, config, g_native_window, nullptr);
                if (surface != EGL_NO_SURFACE) {
                    if (eglMakeCurrent(display, surface, surface, context)) {
                        LOGD("eglMakeCurrent success");

                        int width, height;
                        eglQuerySurface(display, surface, EGL_WIDTH, &width);
                        eglQuerySurface(display, surface, EGL_HEIGHT, &height);

                        if (!imgui_initialized) {
                            LOGD("Initializing ImGui inside render thread...");
                            IMGUI_CHECKVERSION();
                            ImGui::CreateContext();
                            ImGuiIO& io = ImGui::GetIO();

                            // Load custom font from assets for Cyrillic support
                            if (g_asset_manager) {
                                LOGD("Loading Roboto font from assets...");
                                AAsset* fontAsset = AAssetManager_open(g_asset_manager, XOR_STR("fonts/Roboto-Regular.ttf").c_str(), AASSET_MODE_BUFFER);
                                if (fontAsset) {
                                    size_t fontSize = AAsset_getLength(fontAsset);
                                    void* fontData = malloc(fontSize);
                                    AAsset_read(fontAsset, fontData, fontSize);
                                    AAsset_close(fontAsset);

                                    float scale = (float)width / 1080.0f; // Simple base scale
                                    if (scale < 1.0f) scale = 1.0f;

                                    ImFontConfig fontConfig;
                                    fontConfig.FontDataOwnedByAtlas = true;
                                    io.Fonts->AddFontFromMemoryTTF(fontData, (int)fontSize, 50.0f * scale, &fontConfig, io.Fonts->GetGlyphRangesCyrillic());
                                    LOGD("Font loaded with scale: %f", scale);
                                } else {
                                    LOGE("Could not open font asset!");
                                }
                            } else {
                                LOGE("AssetManager is null!");
                            }

                            ImGui_ImplOpenGL3_Init("#version 300 es");
                            imgui_initialized = true;
                            LOGD("ImGui initialized");
                        }
                    } else {
                        LOGE("eglMakeCurrent failed");
                    }
                } else {
                    LOGE("eglCreateWindowSurface failed");
                }
            } else if (g_native_window == nullptr && surface != EGL_NO_SURFACE) {
                LOGD("Destroying surface...");
                eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
                eglDestroySurface(display, surface);
                surface = EGL_NO_SURFACE;
            }
        }

        if (surface == EGL_NO_SURFACE || !imgui_initialized) {
            std::this_thread::sleep_for(std::chrono::milliseconds(16));
            continue;
        }

        ImGuiIO& io = ImGui::GetIO();
        int width, height;
        eglQuerySurface(display, surface, EGL_WIDTH, &width);
        eglQuerySurface(display, surface, EGL_HEIGHT, &height);
        io.DisplaySize = ImVec2((float)width, (float)height);

        if (g_touch_pending) {
            io.AddMousePosEvent(g_touch_x, g_touch_y);
            if (g_touch_action == 0) io.AddMouseButtonEvent(0, true);
            else if (g_touch_action == 1) io.AddMouseButtonEvent(0, false);
            g_touch_pending = false;
        }

        auto now = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::seconds>(now - last_tick).count() >= 1) {
            if (g_countdown > 0) g_countdown--;
            last_tick = now;
        }

        // Perform periodic background environment check (every 5 seconds)
        static auto last_security_scan = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::seconds>(now - last_security_scan).count() >= 5) {
            if (!checkPtrace() || !checkTracerPid()) {
                LOGE("AntiTamper: Late-attached debugger detected!");
#if !defined(DEBUG_BUILD) && !defined(ANTITAMPER_TEST_BUILD)
                g_force_error = true;
#endif
            }

            std::ifstream maps(XOR_STR("/proc/self/maps"));
            std::string line;
            while (std::getline(maps, line)) {
                if (line.find(XOR_STR("frida")) != std::string::npos ||
                    line.find(XOR_STR("xposed")) != std::string::npos ||
                    line.find(XOR_STR("libgadget")) != std::string::npos) {
                    LOGE("AntiTamper: Late-attached hook detected in memory!");
                    g_force_error = true;
                    break;
                }
            }
            last_security_scan = now;
        }

        ImGui_ImplOpenGL3_NewFrame();
        ImGui::NewFrame();

        ImGui::SetNextWindowPos(ImVec2(0, 0));
        ImGui::SetNextWindowSize(io.DisplaySize);
        ImGui::Begin(XOR_STR("TamperOverlay").c_str(), nullptr, ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoBackground | ImGuiWindowFlags_NoScrollbar);

        // Darker, more opaque background
        ImGui::GetWindowDrawList()->AddRectFilled(ImVec2(0, 0), io.DisplaySize, IM_COL32(20, 0, 0, 252));

        float center_x = io.DisplaySize.x * 0.5f;
        float center_y = io.DisplaySize.y * 0.5f;
        float base_scale = io.DisplaySize.x / 1080.0f;
        if (base_scale < 1.0f) base_scale = 1.0f;

        if (g_current_view == OverlayView::WARNING || g_current_view == OverlayView::INTEGRITY_FAILURE) {
            // Warning Icon
            ImGui::SetCursorPosY(center_y - 400 * base_scale);
            ImGui::SetWindowFontScale(3.0f * base_scale);
            float tw = ImGui::CalcTextSize("!").x;
            ImGui::SetCursorPosX(center_x - tw * 0.5f);
            ImGui::TextColored(ImVec4(1, 0, 0, 1), "!");

            // Title
            ImGui::SetWindowFontScale(1.3f * base_scale);
            std::string title = getProtectedString(g_current_locale, XOR_STR("tamper_warning_title"));
            if (g_current_view == OverlayView::INTEGRITY_FAILURE) {
                 title = XOR_STR("CRITICAL INTEGRITY FAILURE");
            }
            tw = ImGui::CalcTextSize(title.c_str()).x;
            if (tw > io.DisplaySize.x - 40.0f * base_scale) {
                ImGui::PushTextWrapPos(io.DisplaySize.x - 40.0f * base_scale);
                ImGui::SetCursorPosX(20.0f * base_scale);
                ImGui::TextColored(ImVec4(1, 0.2f, 0.2f, 1), "%s", title.c_str());
                ImGui::PopTextWrapPos();
            } else {
                ImGui::SetCursorPosX(center_x - tw * 0.5f);
                ImGui::TextColored(ImVec4(1, 0.2f, 0.2f, 1), "%s", title.c_str());
            }

            ImGui::SetWindowFontScale(1.0f * base_scale);
            ImGui::Spacing();
            ImGui::Spacing();

            // Description
            ImGui::PushTextWrapPos(io.DisplaySize.x - 80.0f * base_scale);
            ImGui::SetCursorPosX(40.0f * base_scale);
            if (g_current_view == OverlayView::INTEGRITY_FAILURE) {
                ImGui::Text("%s", XOR_STR("This application has been tampered with and is no longer safe to use. Mismatched resources detected.").c_str());
            } else {
                ImGui::Text("%s", getProtectedString(g_current_locale, XOR_STR("tamper_warning_desc")).c_str());
            }
            ImGui::PopTextWrapPos();

            // Buttons at the bottom
            float btn_width = io.DisplaySize.x - 120 * base_scale;
            ImGui::SetCursorPosY(io.DisplaySize.y - 350 * base_scale);

            ImGui::SetCursorPosX(60 * base_scale);
            if (ImGui::Button(getProtectedString(g_current_locale, XOR_STR("tamper_btn_download_official")).c_str(), ImVec2(btn_width, 70 * base_scale))) {
                g_current_view = OverlayView::DOWNLOAD;
            }

            ImGui::Spacing();
            ImGui::Spacing();

            if (g_current_view != OverlayView::INTEGRITY_FAILURE) {
                bool can_accept = (g_countdown == 0);
                std::string accept_text = getProtectedString(g_current_locale, XOR_STR("tamper_btn_accept_risks"));
                if (!can_accept) accept_text += " (" + std::to_string(g_countdown) + ")";

                if (!can_accept) ImGui::BeginDisabled();
                ImGui::SetCursorPosX(60 * base_scale);
                if (ImGui::Button(accept_text.c_str(), ImVec2(btn_width, 60 * base_scale))) {
                    g_vpn_manager.setWarningAcknowledged(true);
                    g_overlay_active = false;
                    g_accept_clicked = true;
                }
                if (!can_accept) ImGui::EndDisabled();
            } else {
                ImGui::SetCursorPosX(60 * base_scale);
                if (ImGui::Button(XOR_STR("EXIT").c_str(), ImVec2(btn_width, 60 * base_scale))) {
                    abort();
                }
            }
        } else {
            // Download Sources View
            ImGui::SetCursorPosY(100 * base_scale);
            ImGui::SetWindowFontScale(1.5f * base_scale);
            std::string title = getProtectedString(g_current_locale, XOR_STR("tamper_btn_download_official"));
            float tw = ImGui::CalcTextSize(title.c_str()).x;
            ImGui::SetCursorPosX(center_x - tw * 0.5f);
            ImGui::TextColored(ImVec4(1, 1, 1, 1), "%s", title.c_str());

            ImGui::SetWindowFontScale(1.0f * base_scale);
            ImGui::Spacing();
            ImGui::Spacing();
            ImGui::Separator();
            ImGui::Spacing();

            float btn_width = io.DisplaySize.x - 120 * base_scale;
            float btn_height = 80 * base_scale;

            auto linkButton = [&](const char* label, const std::string& url) {
                ImGui::SetCursorPosX(60 * base_scale);
                if (ImGui::Button(label, ImVec2(btn_width, btn_height))) {
                    g_url_to_open = url;
                    g_download_clicked = true;
                }
            };

            linkButton(XOR_STR("GitHub").c_str(), getProtectedString(g_current_locale, XOR_STR("url_github")));
            ImGui::Spacing();
            linkButton(XOR_STR("Codeberg").c_str(), getProtectedString(g_current_locale, XOR_STR("url_codeberg")));
            ImGui::Spacing();
            linkButton(XOR_STR("Telegram").c_str(), getProtectedString(g_current_locale, XOR_STR("url_telegram")));
            ImGui::Spacing();
            linkButton(XOR_STR("Official Website").c_str(), getProtectedString(g_current_locale, XOR_STR("url_website")));

            ImGui::SetCursorPosY(io.DisplaySize.y - 200 * base_scale);
            ImGui::SetCursorPosX(60 * base_scale);
            if (ImGui::Button(getProtectedString(g_current_locale, XOR_STR("tamper_btn_back")).c_str(), ImVec2(btn_width, 60 * base_scale))) {
                g_current_view = OverlayView::WARNING;
            }
        }

        ImGui::End();
        ImGui::Render();
        glViewport(0, 0, width, height);
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
        eglSwapBuffers(display, surface);
    }

    LOGD("Exiting render loop...");
    if (imgui_initialized) {
        ImGui_ImplOpenGL3_Shutdown();
        ImGui::DestroyContext();
    }
    if (surface != EGL_NO_SURFACE) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(display, surface);
    }
    eglDestroyContext(display, context);
    eglTerminate(display);
    LOGD("renderLoop finished");
}

void AntiTamper::handleAcceptRisks(JNIEnv* env, jobject context, const std::string& challenge, const std::string& response) {
    (void)challenge; (void)response;
    reportSecurityEvent(env, XOR_STR("User accepted risks after warning"));
    incrementTamperAckCount(env, context);
    g_vpn_manager.setWarningAcknowledged(true);
}

void AntiTamper::handleDownloadOfficial(JNIEnv* env, jobject activity) {
    if (!g_url_to_open.empty()) {
        reportSecurityEvent(env, XOR_STR("User clicked download link: ") + g_url_to_open);
        openUrl(env, activity, g_url_to_open);
        g_url_to_open = ""; // Clear after opening
    }
}

void AntiTamper::setLogcatEnabled(bool enabled) {
    g_logcat_enabled = enabled;
}

bool AntiTamper::isLogcatEnabled() {
    return g_logcat_enabled;
}

void AntiTamper::openUrl(JNIEnv* env, jobject context, const std::string& url) {
    jclass helperClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/NextVpnManager").c_str());
    if (helperClass) {
        jmethodID openUrlMethod = env->GetStaticMethodID(helperClass, XOR_STR("openUrl").c_str(), XOR_STR("(Landroid/content/Context;Ljava/lang/String;)V").c_str());
        if (openUrlMethod) {
            jstring jurl = env->NewStringUTF(url.c_str());
            env->CallStaticVoidMethod(helperClass, openUrlMethod, context, jurl);
            env->DeleteLocalRef(jurl);
        }
    }
}

} // namespace next
