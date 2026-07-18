#!/usr/bin/env bash
set -euo pipefail

REF="${AWGBOX_REF:-v1.13.13-awg2.1}"
REPO="${AWGBOX_REPO:-https://github.com/hoaxisr/amnezia-box.git}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${AWGBOX_WORK_DIR:-$ROOT/.artifacts/amnezia-box}"
OUTPUT="$ROOT/app/libs/libbox-awgbox-v1.13.13-awg2.1.aar"

: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK containing an NDK}"
rm -rf "$WORK"
git clone --depth 1 --branch "$REF" "$REPO" "$WORK"
git -C "$WORK" submodule update --init --depth 1

GOBIN_DIR="$WORK/.bin"
mkdir -p "$GOBIN_DIR" "$(dirname "$OUTPUT")"
GOBIN="$GOBIN_DIR" go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
GOBIN="$GOBIN_DIR" go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12

# Keep the mobile core intentionally small. VLESS, VMess, SOCKS/HTTP and proxy
# chaining are part of the base sing-box build. AWG and uTLS are the only
# optional protocol features required by ProtonVPN-Next. Clash API is retained
# because libbox CommandServer uses its internal tracker even without an external
# controller. This excludes QUIC (Hysteria2/TUIC), gVisor, WireGuard, Tailscale
# and Naive.
python3 - "$WORK/cmd/internal/build_libbox/main.go" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text()
text, count = re.subn(
    r'sharedTags = append\(sharedTags, "with_gvisor"[^\n]+',
    'sharedTags = append(sharedTags, "with_awg", "with_utls", "with_clash_api", "badlinkname", "tfogo_checklinkname0")',
    text,
    count=1,
)
if count != 1:
    raise SystemExit("Unable to locate the default libbox feature tags")
text = re.sub(
    r'\n\s*sharedTags = append\(sharedTags, "with_tailscale"[^\n]+',
    '',
    text,
    count=1,
)
# The project uses JDK 21; gomobile itself does not require the upstream helper's
# exact JDK 17 string check.
text = text.replace("\tcheckJavaVersion()", "\t// checkJavaVersion()")
path.write_text(text)
PY

gofmt -w "$WORK/cmd/internal/build_libbox/main.go"

# The generator locates gomobile through GOPATH/bin.
GOPATH_DIR="$WORK/.gopath"
mkdir -p "$GOPATH_DIR/bin"
cp "$GOBIN_DIR/gomobile" "$GOPATH_DIR/bin/"
cp "$GOBIN_DIR/gobind" "$GOPATH_DIR/bin/"
(
  cd "$WORK"
  GOPATH="$GOPATH_DIR" go run ./cmd/internal/build_libbox \
    -target android -platform android/arm64
)
cp "$WORK/libbox.aar" "$OUTPUT"
sha256sum "$OUTPUT"
