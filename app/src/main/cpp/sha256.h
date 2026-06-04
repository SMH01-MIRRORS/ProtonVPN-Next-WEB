#ifndef SHA256_H
#define SHA256_H

#include <string>
#include <vector>
#include <cstdint>

namespace next {

class SHA256 {
public:
    SHA256();
    void update(const uint8_t* data, size_t length);
    void update(const std::string& data);
    std::vector<uint8_t> digest();
    static std::string toString(const std::vector<uint8_t>& digest);

private:
    uint32_t m_state[8];
    uint32_t m_bitlen[2];
    uint8_t m_data[64];
    uint32_t m_datalen;

    void transform(const uint8_t* data);
};

} // namespace next

#endif // SHA256_H
