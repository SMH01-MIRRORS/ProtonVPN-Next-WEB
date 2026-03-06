#!/bin/bash

# ==============================================================================
# Sing-box AAR Build Script for Android Projects
# ==============================================================================
# Prerequisites:
# 1. Linux OS (e.g., Fedora) with Go installed.
# 2. Android Studio installed with SDK and NDK (version 29 is required).
# 3. OpenJDK 17 installed and set as the default java version (sdkman supported).
# 4. Default SDK path is assumed to be ~/Android/Sdk.
#    If your path differs, modify the ANDROID_HOME variable below.
#
# Usage Instructions:
# 1. Place this script in the ROOT directory of your Android project
#    (e.g., inside the ProtonVPN-Next folder, next to the 'app' folder).
# 2. Run the script from the terminal: bash build_singbox_android.sh
# 3. The script will automatically configure the environment, clone the
#    sing-box repository, compile libbox.aar, and copy it to app/libs/.
# ==============================================================================

export ANDROID_HOME=$HOME/Android/Sdk

if [ ! -d "$ANDROID_HOME" ]; then
    echo "Error: Android SDK not found at $ANDROID_HOME"
    exit 1
fi

NDK_DIR="$ANDROID_HOME/ndk"
if [ ! -d "$NDK_DIR" ] || [ -z "$(ls -A $NDK_DIR)" ]; then
    echo "Error: Android NDK not found in $NDK_DIR"
    exit 1
fi

LATEST_NDK=$(ls $ANDROID_HOME/ndk | sort -V | tail -1)
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/$LATEST_NDK

export GOPATH=$(go env GOPATH)
export PATH=$PATH:$GOPATH/bin

# Initialize sdkman if present, so the script can find Java managed by it
if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

# Verify Java version (gomobile strictly requires OpenJDK 17)
if ! command -v java >/dev/null 2>&1; then
    echo "FATAL: java command not found. Please install OpenJDK 17."
    exit 1
fi

JAVA_VERSION_CHECK=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION_CHECK" != "17" ]; then
    echo "FATAL[0000] java version should be openjdk 17. Current version is: $(java -version 2>&1 | head -1 | cut -d'"' -f2)"
    echo "Please set your default Java to version 17."
    echo "If using sdkman, run: sdk use java <17.x.x-identifier>"
    exit 1
fi

echo "Using SDK: $ANDROID_HOME"
echo "Using NDK: $ANDROID_NDK_HOME"
echo "Using Java: $(java -version 2>&1 | head -1)"

NDK_MAJOR=$(echo $LATEST_NDK | cut -d'.' -f1)
if [ "$NDK_MAJOR" -lt 29 ]; then
    echo "WARNING: NDK version is too old. Need 29+"
    sleep 3
fi

go install github.com/sagernet/gomobile/cmd/gomobile@latest
go install github.com/sagernet/gomobile/cmd/gobind@latest

gomobile clean
gomobile init

if [ ! -d "sing-box" ]; then
    git clone https://github.com/SagerNet/sing-box.git
fi

cd sing-box

go run ./cmd/internal/build_libbox -target android

if [ -f "libbox.aar" ]; then
    echo "Library generated successfully."

    # Navigate back to the Android project root directory
    cd ..

    # Create the libs directory if it does not exist
    mkdir -p app/libs

    # Copy the compiled AAR to the Android project
    cp sing-box/libbox.aar app/libs/

    echo "Success: libbox.aar has been copied to app/libs/"

    # Clean up the cloned repository as it is no longer needed
    echo "Cleaning up the sing-box source directory..."
    rm -rf sing-box
    echo "Cleanup complete."
else
    echo "Build failed."
    exit 1
fi