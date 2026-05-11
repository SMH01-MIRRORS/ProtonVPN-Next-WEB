#ifndef NEXT_CONNECTION_H
#define NEXT_CONNECTION_H

#include <string>
#include <vector>
#include <set>

namespace next {

struct ObfuscationParams {
    int jc;
    int jmin;
    int jmax;
    int s1;
    int s2;
    int s3;
    int s4;
    std::string h1;
    std::string h2;
    std::string h3;
    std::string h4;
    std::string i1;
    std::string i2;
    std::string i3;
    std::string i4;
    std::string i5;
};

class ConfigGenerator {
public:
    static std::string buildConfig(
        const std::string& serverPublicKey,
        const std::string& privateKey,
        const std::string& localIp,
        const std::string& dnsServer,
        const std::string& targetIp,
        bool isIncludeMode,
        const std::set<std::string>& selectedApps,
        const std::set<std::string>& selectedIps,
        int port,
        const std::string& certificate,
        const ObfuscationParams& params
    );
};

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    VERIFYING,
    CONNECTED,
    DISCONNECTING
};

class VpnManager {
public:
    VpnManager();
    ~VpnManager();

    void setState(VpnState state);
    VpnState getState() const;

    bool canConnect() const;
    bool canDisconnect() const;

    // Orchestrate connection to prevent Smali bypasses
    std::string orchestrateConnection(
        const std::string& publicKey,
        const std::string& privateKey,
        const std::string& localIp,
        const std::string& dnsServer,
        const std::string& targetIp,
        int port,
        const ObfuscationParams& params
    );

    // Security state management
    void setTamperDetected(bool detected);
    bool isTamperDetected() const;
    void setWarningAcknowledged(bool acknowledged);
    bool isWarningAcknowledged() const;
    bool isWarningRequired() const;

private:
    VpnState currentState;
    bool tamperDetected;
    bool warningAcknowledged;
};

extern VpnManager g_vpn_manager;

} // namespace next

#endif // NEXT_CONNECTION_H
