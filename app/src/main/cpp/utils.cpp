#include "utils.h"
#include <sstream>
#include <algorithm>

namespace next {

bool IpSubnetCalculator::isValidIpOrCidr(const std::string& input) {
    if (input.find(':') != std::string::npos) return false;
    return !input.empty();
}

std::string IpSubnetCalculator::normalizeIp(const std::string& ip) {
    if (ip.find('/') != std::string::npos) return ip;
    return ip + "/32";
}

uint32_t IpSubnetCalculator::ipToUint32(const std::string& ip) {
    uint32_t a, b, c, d;
    if (sscanf(ip.c_str(), "%u.%u.%u.%u", &a, &b, &c, &d) != 4) return 0;
    return (a << 24) | (b << 16) | (c << 8) | d;
}

std::string IpSubnetCalculator::uint32ToIp(uint32_t value) {
    std::stringstream ss;
    ss << ((value >> 24) & 0xFF) << "." << ((value >> 16) & 0xFF) << "." << ((value >> 8) & 0xFF) << "." << (value & 0xFF);
    return ss.str();
}

std::pair<uint32_t, uint32_t> IpSubnetCalculator::cidrToRange(const std::string& cidr) {
    size_t slashPos = cidr.find('/');
    if (slashPos == std::string::npos) return {0, 0};
    std::string ip = cidr.substr(0, slashPos);
    int prefix = std::stoi(cidr.substr(slashPos + 1));
    uint32_t ipLong = ipToUint32(ip);
    uint32_t mask = (prefix == 0) ? 0 : (0xFFFFFFFF << (32 - prefix));
    uint32_t start = ipLong & mask;
    uint32_t end = start | (~mask);
    return {start, end};
}

std::vector<std::string> IpSubnetCalculator::rangeToCidrs(uint32_t start, uint32_t end) {
    std::vector<std::string> result;
    uint64_t s = start;
    uint64_t e = end;
    while (s <= e) {
        int pref = (s != 0) ? (32 - __builtin_ctzll(s)) : 0;
        while (pref >= 0) {
            uint32_t mask = (pref == 0) ? 0 : (0xFFFFFFFF << (32 - pref));
            uint64_t blockSize = (static_cast<uint64_t>(~mask) & 0xFFFFFFFF) + 1;
            if (s + blockSize - 1 > e) { pref++; break; }
            if (pref == 0 || (s & mask) == s) {
                result.push_back(uint32ToIp(static_cast<uint32_t>(s)) + "/" + std::to_string(pref));
                s += blockSize;
                goto next_s;
            }
            pref--;
        }
        if (pref > 32) break;
        next_s:;
    }
    return result;
}

std::vector<std::string> IpSubnetCalculator::complementOfExcluded(const std::vector<std::string>& excludedCidrs) {
    std::vector<std::pair<uint32_t, uint32_t>> ranges;
    ranges.push_back({0, 0xFFFFFFFF});
    for (const auto& raw : excludedCidrs) {
        if (raw.find(':') != std::string::npos) continue;
        std::string cidr = normalizeIp(raw);
        auto excRange = cidrToRange(cidr);
        std::vector<std::pair<uint32_t, uint32_t>> newRanges;
        for (const auto& r : ranges) {
            uint32_t rs = r.first, re = r.second, es = excRange.first, ee = excRange.second;
            if (ee < rs || es > re) newRanges.push_back(r);
            else {
                if (es > rs) newRanges.push_back({rs, es - 1});
                if (ee < re) newRanges.push_back({ee + 1, re});
            }
        }
        ranges = newRanges;
        if (ranges.empty()) break;
    }
    std::vector<std::string> result;
    for (const auto& r : ranges) {
        auto cidrs = rangeToCidrs(r.first, r.second);
        result.insert(result.end(), cidrs.begin(), cidrs.end());
    }
    return result;
}

} // namespace next
