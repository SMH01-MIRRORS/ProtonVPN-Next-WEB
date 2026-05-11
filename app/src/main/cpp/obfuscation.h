#ifndef NEXT_OBFUSCATION_H
#define NEXT_OBFUSCATION_H

#include <string>
#include <array>

namespace next {

template<size_t N>
class XorString {
public:
    constexpr XorString(const char* str) : _key(static_cast<char>(N & 0xFF)) {
        for (size_t i = 0; i < N; ++i) {
            _data[i] = str[i] ^ _key;
        }
    }

    std::string decrypt() const {
        std::string result;
        result.reserve(N);
        for (size_t i = 0; i < N; ++i) {
            result += static_cast<char>(_data[i] ^ _key);
        }
        return result;
    }

private:
    std::array<char, N> _data{};
    const char _key;
};

// Macro to create an obfuscated string on the stack
#define XOR_STR(s) ([] { \
    static constexpr next::XorString<sizeof(s) - 1> encrypted(s); \
    return encrypted.decrypt(); \
}())

// Macro for use in static arrays/initializers (returns decrypted std::string immediately)
#define XOR_STR_S(s) (next::XorString<sizeof(s) - 1>(s).decrypt())

} // namespace next

#endif // NEXT_OBFUSCATION_H
