# amnezia-box binary notice

`app/libs/libbox-awgbox-v1.13.13-awg2.1.aar` is built from
[hoaxisr/amnezia-box](https://github.com/hoaxisr/amnezia-box), release
`v1.13.13-awg2.1`, with a minimal mobile feature set: `with_awg`, `with_utls`,
`badlinkname` and `tfogo_checklinkname0`.

VLESS, VMess, SOCKS/HTTP and proxy chaining are included in the base sing-box
build. Optional QUIC (Hysteria2/TUIC), gVisor, standard WireGuard, Tailscale,
Naive outbound and Clash API components are excluded. The TUN uses Android's
system stack instead of gVisor.

The AAR is restricted to `android/arm64`, matching ProtonVPN-Next's supported ABI.
It contains sing-box, amneziawg-go and generated gomobile bindings. See `LICENSE`
and the upstream dependency licenses. The reproducible build entry point is
`scripts/build-awgbox-lib.sh`.
