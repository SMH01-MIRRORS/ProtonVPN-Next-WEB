#!/usr/bin/env bash
set -euo pipefail

REF="${AWGBOX_REF:-v1.13.13-awg2.1}"
REPO="${AWGBOX_REPO:-https://github.com/hoaxisr/amnezia-box.git}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${AWGBOX_WORK_DIR:-$ROOT/.artifacts/amnezia-box}"
OUTPUT="$ROOT/app/libs/libbox-awgbox-v1.13.13-awg2.1.aar"
CHECKSUM_FILE="$ROOT/third_party/amnezia-box/libbox-awgbox-v1.13.13-awg2.1.sha256"
FORCE_REBUILD="${AWGBOX_FORCE_REBUILD:-0}"

expected_sha256() {
  awk 'NR == 1 { print $1 }' "$CHECKSUM_FILE"
}

output_is_valid() {
  [[ -s "$OUTPUT" && -f "$CHECKSUM_FILE" ]] || return 1
  [[ "$(sha256sum "$OUTPUT" | awk '{ print $1 }')" == "$(expected_sha256)" ]]
}

if [[ "$FORCE_REBUILD" != "1" ]] && output_is_valid; then
  echo "AWGBox AAR is already available and verified: $OUTPUT"
  exit 0
fi

if [[ -e "$OUTPUT" ]]; then
  echo "Removing stale or unverified AWGBox AAR: $OUTPUT"
  rm -f "$OUTPUT"
fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK containing an NDK}"
export ANDROID_HOME
command -v git >/dev/null || { echo "git is required to build AWGBox" >&2; exit 1; }
command -v go >/dev/null || { echo "Go is required to build AWGBox" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required to build AWGBox" >&2; exit 1; }

mkdir -p "$ROOT/.artifacts" "$(dirname "$OUTPUT")"
LOCK_DIR="$ROOT/.artifacts/awgbox-build.lock"
while ! mkdir "$LOCK_DIR" 2>/dev/null; do
  if output_is_valid; then
    echo "AWGBox AAR was prepared by another Gradle process: $OUTPUT"
    exit 0
  fi
  if [[ ! -f "$LOCK_DIR/pid" ]] || ! kill -0 "$(cat "$LOCK_DIR/pid" 2>/dev/null)" 2>/dev/null; then
    rm -rf "$LOCK_DIR"
    continue
  fi
  sleep 1
done
echo "$$" > "$LOCK_DIR/pid"
trap 'rm -rf "$LOCK_DIR"' EXIT

# Another process may have completed while this process was waiting for the lock.
if [[ "$FORCE_REBUILD" != "1" ]] && output_is_valid; then
  echo "AWGBox AAR is already available and verified: $OUTPUT"
  exit 0
fi

rm -rf "$WORK"
git clone --depth 1 --branch "$REF" "$REPO" "$WORK"
git -C "$WORK" submodule update --init --depth 1

GOBIN_DIR="$WORK/.bin"
mkdir -p "$GOBIN_DIR"
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
  export GOPATH="$GOPATH_DIR"
  export PATH="$GOPATH_DIR/bin:$PATH"
  command -v gomobile >/dev/null || { echo "gomobile is missing from GOPATH/bin" >&2; exit 1; }
  command -v gobind >/dev/null || { echo "gobind is missing from GOPATH/bin" >&2; exit 1; }
  go run ./cmd/internal/build_libbox \
    -target android -platform android/arm64
)

TEMP_OUTPUT="$OUTPUT.tmp"
cp "$WORK/libbox.aar" "$TEMP_OUTPUT"
actual_sha256="$(sha256sum "$TEMP_OUTPUT" | awk '{ print $1 }')"
expected_sha256="$(expected_sha256)"
if [[ "$actual_sha256" != "$expected_sha256" ]]; then
  rm -f "$TEMP_OUTPUT"
  echo "AWGBox reproducibility check failed" >&2
  echo "Expected: $expected_sha256" >&2
  echo "Actual:   $actual_sha256" >&2
  exit 1
fi
mv "$TEMP_OUTPUT" "$OUTPUT"
echo "$actual_sha256  ${OUTPUT#$ROOT/}"
