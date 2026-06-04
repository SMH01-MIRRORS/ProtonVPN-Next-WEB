#ifndef NEXT_SYSCALLS_H
#define NEXT_SYSCALLS_H

#include <sys/types.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <cstdint>
#include <fcntl.h>
#include <string>

namespace next {

#if defined(__aarch64__)

inline long sys_openat(int dirfd, const char* pathname, int flags, mode_t mode) {
    register long x0 __asm__("x0") = dirfd;
    register long x1 __asm__("x1") = reinterpret_cast<long>(pathname);
    register long x2 __asm__("x2") = flags;
    register long x3 __asm__("x3") = mode;
    register long x8 __asm__("x8") = __NR_openat;
    __asm__ __volatile__(
        "svc #0"
        : "=r"(x0)
        : "r"(x0), "r"(x1), "r"(x2), "r"(x3), "r"(x8)
        : "memory"
    );
    return x0;
}

inline long sys_read(int fd, void* buf, size_t count) {
    register long x0 __asm__("x0") = fd;
    register long x1 __asm__("x1") = reinterpret_cast<long>(buf);
    register long x2 __asm__("x2") = static_cast<long>(count);
    register long x8 __asm__("x8") = __NR_read;
    __asm__ __volatile__(
        "svc #0"
        : "=r"(x0)
        : "r"(x0), "r"(x1), "r"(x2), "r"(x8)
        : "memory"
    );
    return x0;
}

inline long sys_close(int fd) {
    register long x0 __asm__("x0") = fd;
    register long x8 __asm__("x8") = __NR_close;
    __asm__ __volatile__(
        "svc #0"
        : "=r"(x0)
        : "r"(x0), "r"(x8)
        : "memory"
    );
    return x0;
}

#elif defined(__x86_64__)

inline long sys_openat(int dirfd, const char* pathname, int flags, mode_t mode) {
    long ret;
    register long r10 __asm__("r10") = static_cast<long>(mode);
    __asm__ __volatile__(
        "syscall"
        : "=a"(ret)
        : "a"(__NR_openat), "D"(dirfd), "S"(pathname), "d"(flags), "r"(r10)
        : "rcx", "r11", "memory"
    );
    return ret;
}

inline long sys_read(int fd, void* buf, size_t count) {
    long ret;
    __asm__ __volatile__(
        "syscall"
        : "=a"(ret)
        : "a"(__NR_read), "D"(fd), "S"(buf), "d"(count)
        : "rcx", "r11", "memory"
    );
    return ret;
}

inline long sys_close(int fd) {
    long ret;
    __asm__ __volatile__(
        "syscall"
        : "=a"(ret)
        : "a"(__NR_close), "D"(fd)
        : "rcx", "r11", "memory"
    );
    return ret;
}

#else
// Fallback for other architectures (using standard libc for now, but logged as potentially unsafe)
inline long sys_openat(int dirfd, const char* pathname, int flags, mode_t mode) { return openat(dirfd, pathname, flags, mode); }
inline long sys_read(int fd, void* buf, size_t count) { return read(fd, buf, count); }
inline long sys_close(int fd) { return close(fd); }
#endif

// Utility to read a small file into a string using syscalls
[[maybe_unused]] inline std::string read_file_sys(const char* path) {
    int fd = static_cast<int>(sys_openat(AT_FDCWD, path, O_RDONLY, 0));
    if (fd < 0) return "";

    std::string result;
    char buffer[1024];
    long n;
    while ((n = sys_read(fd, buffer, sizeof(buffer))) > 0) {
        result.append(buffer, static_cast<size_t>(n));
    }
    sys_close(fd);
    return result;
}

} // namespace next

#endif // NEXT_SYSCALLS_H
