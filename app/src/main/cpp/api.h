#ifndef NEXT_API_H
#define NEXT_API_H

#include <string>
#include <vector>
#include <map>
#include <jni.h>

namespace next {

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
        const std::map<std::string, std::string>& headers,
        const std::string& body = ""
    );

    // Proton specific helpers
    static Response post(JNIEnv* env, const std::string& path, const std::string& jsonBody, const std::map<std::string, std::string>& extraHeaders = {});
    static Response get(JNIEnv* env, const std::string& path, const std::map<std::string, std::string>& extraHeaders = {});
    static Response del(JNIEnv* env, const std::string& path, const std::map<std::string, std::string>& extraHeaders = {});

private:
    static std::string getBaseUrl();
};

} // namespace next

#endif // NEXT_API_H
