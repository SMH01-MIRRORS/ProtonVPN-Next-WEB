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

# The fork's generic mobile target currently omits the feature gate even though
# AWG sources are present. Keep this explicit until upstream includes it.
python3 - "$WORK/cmd/internal/build_libbox/main.go" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
needle = '"with_gvisor", "with_quic", "with_wireguard",'
replacement = '"with_gvisor", "with_quic", "with_wireguard", "with_awg",'
if '"with_awg"' not in text:
    if needle not in text:
        raise SystemExit("Unable to locate libbox build tags")
    path.write_text(text.replace(needle, replacement, 1))
PY

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
