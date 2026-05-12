#ifndef NEXT_AUTH_H
#define NEXT_AUTH_H

#include <string>
#include <jni.h>
#include "api.h"

namespace next {

struct LoginResult {
    bool success;
    int code;
    std::string accessToken;
    std::string refreshToken;
    std::string sessionId;
    std::string userId;
    std::vector<std::string> scopes;
    std::string error;

    // Captcha info
    bool captchaRequired;
    std::string captchaUrl;
    std::string captchaToken;
};

class AuthManager {
public:
    static LoginResult login(
        JNIEnv* env,
        const std::string& username,
        const std::string& password,
        const std::string& captchaToken = ""
    );

    static LoginResult loginAnonymous(
        JNIEnv* env,
        const std::string& captchaToken = ""
    );

    static LoginResult refreshSession(
        JNIEnv* env,
        const std::string& sessionId,
        const std::string& refreshToken
    );

private:
    static std::string buildChallengePayload();
};

} // namespace next

#endif // NEXT_AUTH_H
