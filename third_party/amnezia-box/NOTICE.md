# amnezia-box binary notice

`app/libs/libbox-awgbox-v1.13.13-awg2.1.aar` is built from
[hoaxisr/amnezia-box](https://github.com/hoaxisr/amnezia-box), release
`v1.13.13-awg2.1`, with the upstream mobile build plus the `with_awg` build tag.

The AAR is restricted to `android/arm64`, matching ProtonVPN-Next's supported ABI.
It contains sing-box, amneziawg-go and generated gomobile bindings. See `LICENSE`
and the upstream dependency licenses. The reproducible build entry point is
`scripts/build-awgbox-lib.sh`.
