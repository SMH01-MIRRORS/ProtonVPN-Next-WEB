#include <string.h>
#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <setjmp.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <unistd.h>
#include <android/log.h>

#include "byedpi/error.h"
#include "main.h"

#define LOG_TAG "ByeDpiNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern int server_fd;
static int g_proxy_running = 0;

struct params default_params = {
        .await_int = 10,
        .ipv6 = 1,
        .resolve = 1,
        .udp = 1,
        .max_open = 512,
        .bfsize = 16384,
        .baddr = {
            .in6 = { .sin6_family = AF_INET6 }
        },
        .laddr = {
            .in = { .sin_family = AF_INET }
        },
        .debug = 0
};

void reset_params(void) {
    clear_params(NULL, NULL);
    params = default_params;
}

JNIEXPORT jint JNICALL
Java_ru_protonmod_next_data_network_byedpi_ByeDpiProxy_jniStartProxy(JNIEnv *env, jobject thiz, jobjectArray args) {
    if (g_proxy_running) {
        LOGI("proxy already running");
        return -1;
    }

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc(argc, sizeof(char *));

    if (!argv) {
        LOGE("failed to allocate memory for argv");
        return -1;
    }

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);

        if (!arg) {
            argv[i] = NULL;
            continue;
        }

        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        argv[i] = arg_str ? strdup(arg_str) : NULL;

        if (arg_str) (*env)->ReleaseStringUTFChars(env, arg, arg_str);

        (*env)->DeleteLocalRef(env, arg);
    }

    LOGI("starting proxy with %d args", argc);
    reset_params();
    g_proxy_running = 1;
    optind = 1;

    int result = main(argc, argv);

    LOGI("proxy return code %d", result);
    g_proxy_running = 0;

    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);

    return result;
}

JNIEXPORT jint JNICALL
Java_ru_protonmod_next_data_network_byedpi_ByeDpiProxy_jniStopProxy(JNIEnv *env, jobject thiz) {
    LOGI("send shutdown to proxy");

    if (!g_proxy_running) {
        LOGI("proxy is not running");
        return -1;
    }

    shutdown(server_fd, SHUT_RDWR);

    return 0;
}

JNIEXPORT jint JNICALL
Java_ru_protonmod_next_data_network_byedpi_ByeDpiProxy_jniForceClose(JNIEnv *env, jobject thiz) {
    LOGI("closing server socket (fd: %d)", server_fd);

    if (close(server_fd) == -1) {
        LOGE("failed to close server socket (fd: %d)", server_fd);
        return -1;
    }

    LOGI("proxy socket force close");

    return 0;
}
