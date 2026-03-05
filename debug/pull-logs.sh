#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT="$SCRIPT_DIR/logs_$TIMESTAMP.txt"

# Check device
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No Android device connected"
    exit 1
fi

echo "Pulling logcat → $OUT"

{
    echo "=== odbplus logcat — $TIMESTAMP ==="
    echo "=== Device: $(adb shell getprop ro.product.model | tr -d '\r') ==="
    echo ""

    adb logcat -d -v threadtime 2>/dev/null \
        | grep -E "(com\.odbplus|UOAPL|AdapterSession|ElmDriver|AdapterFingerprinter|Fingerprint|SESSION|ObdService|PollingManager|LiveData|Protocol|ObdParser)"

} > "$OUT"

LINE_COUNT="$(wc -l < "$OUT")"
echo "Done — $LINE_COUNT lines written to $(basename "$OUT")"
