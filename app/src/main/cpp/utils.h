#ifndef NEXT_UTILS_H
#define NEXT_UTILS_H

#include <string>
#include <vector>
#include <cstdint>

namespace next {

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

} // namespace next

#endif // NEXT_UTILS_H
