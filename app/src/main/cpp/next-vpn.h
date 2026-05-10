#ifndef NEXT_VPN_H
#define NEXT_VPN_H

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

class IpSubnetCalculator {
public:
    static bool isValidIpOrCidr(const std::string& input);
    static std::string normalizeIp(const std::string& ip);
    static std::vector<std::string> complementOfExcluded(const std::vector<std::string>& excludedCidrs);

private:
    static uint32_t ipToUint32(const std::string& ip);
    static std::string uint32ToIp(uint32_t value);
    static std::pair<uint32_t, uint32_t> cidrToRange(const std::string& cidr);
    static std::vector<std::string> rangeToCidrs(uint32_t start, uint32_t end);
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

private:
    VpnState currentState;
};

} // namespace next

#endif // NEXT_VPN_H
