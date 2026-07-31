#include "connection.h"
#include <sstream>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "NextConnection"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace next {

VpnManager g_vpn_manager;

VpnManager::VpnManager() : currentState(VpnState::DISCONNECTED), tamperDetected(false), warningAcknowledged(false) {}
VpnManager::~VpnManager() {}

void VpnManager::setState(VpnState state) { currentState = state; }
VpnState VpnManager::getState() const { return currentState; }
bool VpnManager::canConnect() const { return currentState == VpnState::DISCONNECTED; }
bool VpnManager::canDisconnect() const { return currentState == VpnState::CONNECTED || currentState == VpnState::VERIFYING || currentState == VpnState::CONNECTING; }
void VpnManager::setTamperDetected(bool detected) { tamperDetected = detected; }
bool VpnManager::isTamperDetected() const { return tamperDetected; }
void VpnManager::setWarningAcknowledged(bool acknowledged) { warningAcknowledged = acknowledged; }
bool VpnManager::isWarningAcknowledged() const { return warningAcknowledged; }
bool VpnManager::isWarningRequired() const { return tamperDetected && !warningAcknowledged; }

std::string ConfigGenerator::buildConfig(
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
) {
    std::stringstream config;
    config << "[Interface]\n";
    config << "PrivateKey = " << privateKey << "\n";
    config << "Address = " << localIp << "/32\n";
    config << "DNS = " << dnsServer << "\n";
    config << "MTU = 1280\n";

    int safeJc = std::max(0, std::min(128, params.jc));
    int safeJmin = std::max(0, std::min(1279, params.jmin));
    int safeJmax = std::max(0, std::min(1280, params.jmax));
    int safeS1 = std::max(0, std::min(1280, params.s1));
    int safeS2 = std::max(0, std::min(1280, params.s2));
    int safeS3 = std::max(0, std::min(1280, params.s3));
    int safeS4 = std::max(0, std::min(1280, params.s4));

    config << "Jc = " << safeJc << "\n";
    config << "Jmin = " << safeJmin << "\n";
    config << "Jmax = " << safeJmax << "\n";
    config << "S1 = " << safeS1 << "\n";
    config << "S2 = " << safeS2 << "\n";
    config << "S3 = " << safeS3 << "\n";
    config << "S4 = " << safeS4 << "\n";

    (void)certificate;

    if (!params.h1.empty()) config << "H1 = " << params.h1 << "\n";
    if (!params.h2.empty()) config << "H2 = " << params.h2 << "\n";
    if (!params.h3.empty()) config << "H3 = " << params.h3 << "\n";
    if (!params.h4.empty()) config << "H4 = " << params.h4 << "\n";

    if (!params.i1.empty()) config << "I1 = " << params.i1 << "\n";
    if (!params.i2.empty()) config << "I2 = " << params.i2 << "\n";
    if (!params.i3.empty()) config << "I3 = " << params.i3 << "\n";
    if (!params.i4.empty()) config << "I4 = " << params.i4 << "\n";
    if (!params.i5.empty()) config << "I5 = " << params.i5 << "\n";

    if (!params.header_protection_key.empty()) config << "HeaderProtectionKey = " << params.header_protection_key << "\n";
    if (!params.content_padding_addition.empty()) config << "ContentPaddingAddition = " << params.content_padding_addition << "\n";
    if (!params.rekey_after_time.empty()) config << "RekeyAfterTime = " << params.rekey_after_time << "\n";
    if (!params.rekey_timeout.empty()) config << "RekeyTimeout = " << params.rekey_timeout << "\n";
    if (!params.reject_after_time.empty()) config << "RejectAfterTime = " << params.reject_after_time << "\n";
    if (!params.keepalive_timeout.empty()) config << "KeepaliveTimeout = " << params.keepalive_timeout << "\n";
    if (!params.max_handshake_attempts.empty()) config << "MaxHandshakeAttempts = " << params.max_handshake_attempts << "\n";

    if (!selectedApps.empty()) {
        std::string apps;
        for (auto it = selectedApps.begin(); it != selectedApps.end(); ++it) {
            if (it != selectedApps.begin()) apps += ",";
            apps += *it;
        }
        if (isIncludeMode) config << "IncludedApplications = " << apps << "\n";
        else config << "ExcludedApplications = " << apps << "\n";
    }

    config << "\n[Peer]\n";
    config << "PublicKey = " << serverPublicKey << "\n";
    std::string endpoint = targetIp;
    if (endpoint.find(':') != std::string::npos && endpoint.find('[') == std::string::npos) endpoint = "[" + endpoint + "]";
    config << "Endpoint = " << endpoint << ":" << port << "\n";
    config << "AllowedIPs = ";
    if (selectedIps.empty()) config << "0.0.0.0/0";
    else {
        for (auto it = selectedIps.begin(); it != selectedIps.end(); ++it) {
            if (it != selectedIps.begin()) config << ", ";
            config << *it;
        }
    }
    config << "\n";
    if (!params.persistent_keepalive.empty()) config << "PersistentKeepalive = " << params.persistent_keepalive << "\n";
    else config << "PersistentKeepalive = 60\n";
    return config.str();
}

std::string VpnManager::orchestrateConnection(
    const std::string& publicKey,
    const std::string& privateKey,
    const std::string& localIp,
    const std::string& dnsServer,
    const std::string& targetIp,
    int port,
    const ObfuscationParams& params
) {
    if (tamperDetected && !warningAcknowledged) {
        LOGE("Orchestration: Connection blocked due to unacknowledged tamper warning");
        return "[Interface]\nPrivateKey = tamper_detected_and_unacknowledged\n";
    }
    return ConfigGenerator::buildConfig(publicKey, privateKey, localIp, dnsServer, targetIp, false, {}, {}, port, "", params);
}

} // namespace next
