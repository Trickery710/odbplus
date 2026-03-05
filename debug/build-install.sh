#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/.."

echo "=== odbplus debug build & install ==="

# Check device
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No Android device connected"
    exit 1
fi

./gradlew :app:installDebug \
    -Pandroid.enableJetifier=true \
    --stacktrace \
    --info

echo ""
echo "=== Launching app ==="
adb shell am force-stop com.odbplus.app
sleep 1
adb shell am start \
    -n com.odbplus.app/.MainActivity \
    -e "debug" "true"

echo "Done."
