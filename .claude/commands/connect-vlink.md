# Connect to Android-Vlink OBD Adapter

Build and install the latest app, launch it on the connected Android device, navigate to the Connect screen, and connect to the Android-Vlink Bluetooth OBD adapter (`13:E0:2F:8D:53:68`).

## Steps

1. **Check device** — run `adb devices` to confirm a device is connected. If none, stop and tell the user.

2. **Build & install** — run `./gradlew :app:installDebug` from the project root. Wait for "Installed on 1 device."

3. **Launch app** — force-stop first so the BT socket is released cleanly, then start:
   ```
   adb shell am force-stop com.odbplus.app && sleep 2 && adb shell am start -n com.odbplus.app/.MainActivity && sleep 2
   ```

4. **Navigate to ODB tab** — tap the ODB bottom nav item (text bounds `[368,2132][438,2171]`, center `(403, 2151)`):
   ```
   adb shell input tap 403 2151
   ```
   Wait 1 second, take a screenshot, verify the ODB Hub screen is showing.

5. **Navigate to Connect screen** — tap the "Connect" tile (text bounds `[202,1001][364,1052]`, center `(283, 1026)`):
   ```
   adb shell input tap 283 1026
   ```

6. **Open Bluetooth picker** — the exact bounds for the Bluetooth button are `[556,420][1027,546]`, center `(791, 483)`:
   ```
   adb shell input tap 791 483
   ```
   Wait 1.5 seconds and take a screenshot to verify the "Paired Bluetooth Devices" dialog appeared.

7. **Select Android-Vlink** — the Android-Vlink entry (`13:E0:2F:8D:53:68`) is always first in the list. Its bounds are `[183,383][897,541]`, center `(540, 462)`:
   ```
   adb shell input tap 540 462
   ```

8. **Monitor connection** — wait 5 seconds, then take a screenshot and report the connection status shown in the log. Key outcomes:
   - `Status: CONNECTED` with `Session pre-init complete` and no `Disconnecting` = success
   - `!! Unexpected Error: read failed` = adapter dropped the socket (VLink may have been busy from previous attempt — wait 10 seconds and retry from step 6)
   - `Disconnecting...` immediately after `Session pre-init complete` = known bug in `ObdService`/`AdapterSession` — report to user

9. **Report** — show the user a screenshot of the final state and summarize the connection log lines visible on screen.

## Notes

- VLink MAC: `13:E0:2F:8D:53:68` (Android-Vlink)
- If the Wi-Fi dialog opens instead of the BT picker, press back twice (`adb shell input keyevent KEYCODE_BACK`) and retry step 6
- The "Disconnecting after pre-init" pattern is a known active bug — the adapter connects fine but something triggers an immediate disconnect in `ObdService.onTransportReady()` or `AdapterSession`
- BT permissions (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`) are granted; no permission prompt expected
- UI coordinates are for a Samsung Galaxy A16 at 1080×2340 resolution; if the device differs, use `adb shell uiautomator dump` to find exact button bounds
