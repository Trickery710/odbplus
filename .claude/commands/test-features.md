# Test App Features

Automated test cycle focused on Live Data reliability. Terminal is used only to verify the adapter connection works. Logs are only captured when an error is detected.

**This skill is designed for a fix-and-retry loop.** When an error is found, the agent returns structured results to the main Claude Code agent, which reads the logs, fixes the code, and re-invokes this skill. This repeats until the test passes.

---

## Steps

1. **Check device** — run `adb devices`. If no device listed, stop with `STATUS: DEVICE_MISSING`.

2. **Clear logcat**:
   ```
   adb logcat -c
   ```

3. **Build & install**:
   ```
   cd /home/casey/Desktop/projects/odbplus && ./gradlew :app:installDebug
   ```
   If build fails, stop with `STATUS: BUILD_FAILED` and include the gradle error output.

4. **Launch app**:
   ```
   adb shell am force-stop com.odbplus.app && sleep 2 && adb shell am start -n com.odbplus.app/.MainActivity && sleep 2
   ```

5. **Navigate to ODB tab** `(403, 2151)`:
   ```
   adb shell input tap 403 2151 && sleep 1
   ```

6. **Open Connect screen** `(283, 1026)`:
   ```
   adb shell input tap 283 1026 && sleep 1
   ```

7. **Open Bluetooth picker** `(791, 483)`:
   ```
   adb shell input tap 791 483 && sleep 1.5
   ```
   If Wi-Fi dialog appeared instead, press back twice and retry this step once.

8. **Select Android-Vlink** `(540, 462)`:
   ```
   adb shell input tap 540 462 && sleep 6
   ```
   Take screenshot. If "!! Unexpected Error: read failed" → wait 10s and retry step 7 once.
   If still failing → stop with `STATUS: CONNECT_FAILED`.

9. **Return to ODB Hub**:
   ```
   adb shell input keyevent KEYCODE_BACK && sleep 1
   ```

9a. **Dump connection-phase logs** — capture UOAPL session trace BEFORE it gets cleared:
    ```
    adb logcat -d -v threadtime 2>/dev/null | grep -E "(AdapterSession|ElmDriver|AdapterFingerprinter|Fingerprint|SESSION|UOAPL|Protocol|ObdService|PollingManager)" | tail -400
    ```
    Look for `UOAPL: SESSION_ACTIVE` — if missing, the session never activated. Note the last log line seen.

---

## Terminal — Connection Health Check

10. **Open Terminal** `(797, 1540)`:
    ```
    adb shell input tap 797 1540 && sleep 1
    ```

11. **Send ATI** `(269, 1540)`:
    ```
    adb shell input tap 269 1540 && sleep 2
    ```
    Take screenshot. ATI must return a version string like `ELM327 v2.3`.
    If ATI returns nothing or gibberish → dump logs and stop with `STATUS: TERMINAL_FAILED`:
    ```
    adb logcat -d -v threadtime 2>/dev/null | grep -E "(com\.odbplus|UOAPL|AdapterSession|ElmDriver|ObdService|PollingManager|LiveData|Adapter|Protocol|SESSION)" | tail -300
    ```

12. **Send 010C** `(640, 1540)`:
    ```
    adb shell input tap 640 1540 && sleep 2
    ```
    Take screenshot. Read the response carefully.
    **Pass** = any response that does NOT contain "BUS INIT", "ERROR", or "UNABLE TO CONNECT" (e.g. `41 0C xx xx`, `NO DATA`).
    **Fail** = response contains "BUS INIT", "ERROR", or "UNABLE TO CONNECT" → dump connection-phase logs and stop with `STATUS: TERMINAL_FAILED`:
    ```
    adb logcat -d -v threadtime 2>/dev/null | grep -E "(AdapterSession|ElmDriver|AdapterFingerprinter|Fingerprint|SESSION|UOAPL|Protocol|ObdService)" | tail -300
    ```

13. **Return to ODB Hub**:
    ```
    adb shell input keyevent KEYCODE_BACK && sleep 1
    ```

14. **Clear logcat** — fresh buffer for Live Data test:
    ```
    adb logcat -c
    ```

---

## Test: Live Data (main focus)

15. **Open Live Data** `(797, 1050)`:
    ```
    adb shell input tap 797 1050 && sleep 1
    ```
    Take screenshot. All 4 cards must be present: Engine RPM (0C), Vehicle Speed (0D), Throttle Position (11), Calculated Engine Load (04).
    If any card is missing or header already shows "0 / 4 PIDs" → dump logs and stop with `STATUS: CARDS_MISSING_BEFORE_START`:
    ```
    adb logcat -d -v threadtime 2>/dev/null | grep -E "(com\.odbplus|UOAPL|AdapterSession|ElmDriver|ObdService|PollingManager|LiveData|Adapter|Protocol|SESSION)" | tail -300
    ```

16. **Start streaming** `(316, 441)`:
    ```
    adb shell input tap 316 441 && sleep 3
    ```
    Take screenshot. Read it carefully. This is the most critical check.

    **Failure = any of:**
    - Header shows "0 / 4 PIDs"
    - Screen is blank / cards gone
    - Any card shows "No response", "BUS INIT ERROR", "--", or blank value

    **Pass = all 4 cards show a numeric value** (e.g. `750 rpm`, `0 km/h`, `15 %`)

    On failure → dump logs and stop with `STATUS: PIDS_NOT_DISPLAYING`:
    ```
    adb logcat -d -v threadtime 2>/dev/null | grep -E "(com\.odbplus|UOAPL|AdapterSession|ElmDriver|ObdService|PollingManager|LiveData|Adapter|Protocol|SESSION)" | tail -300
    ```

17. **Watch live data for ~2 minutes** — only if all 4 cards are showing numeric values.
    Take a screenshot every 30 seconds. Read each one immediately.
    If any card loses its numeric value or disappears → dump logs and stop with `STATUS: PIDS_DROPPED`:
    ```
    sleep 30 && adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png /tmp/screen_live_30s.png
    sleep 30 && adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png /tmp/screen_live_60s.png
    sleep 30 && adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png /tmp/screen_live_90s.png
    sleep 30 && adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png /tmp/screen_live_120s.png
    ```
    On failure at any interval:
    ```
    adb logcat -d -v threadtime 2>/dev/null | grep -E "(com\.odbplus|UOAPL|AdapterSession|ElmDriver|ObdService|PollingManager|LiveData|Adapter|Protocol|SESSION)" | tail -300
    ```

18. **Stop streaming** `(316, 441)`:
    ```
    adb shell input tap 316 441 && sleep 1
    ```
    Take screenshot. Cards must remain visible.

---

## Structured Result (always end your report with this block)

```
STATUS: PASS | PIDS_NOT_DISPLAYING | PIDS_DROPPED | CARDS_MISSING_BEFORE_START | TERMINAL_FAILED | CONNECT_FAILED | BUILD_FAILED | DEVICE_MISSING
FAILED_AT_STEP: <step number and description>
SCREEN_OBSERVATION: <exactly what was visible on screen at point of failure>
LOGCAT:
<full logcat dump — include every line, do not truncate>
```

If STATUS is PASS, omit FAILED_AT_STEP, SCREEN_OBSERVATION, and LOGCAT.

---

## Coordinate Reference (Samsung Galaxy A16 · 1080×2340)

| Element | Bounds | Tap |
|---|---|---|
| ODB nav tab | `[368,2132][438,2171]` | `(403, 2151)` |
| Connect tile (hub) | `[202,1001][364,1052]` | `(283, 1026)` |
| Live Data tile (hub) | `[706,1025][888,1076]` | `(797, 1050)` |
| Terminal tile (hub) | `[711,1515][883,1566]` | `(797, 1540)` |
| Bluetooth button | `[556,420][1027,546]` | `(791, 483)` |
| Android-Vlink (1st in list) | `[183,383][897,541]` | `(540, 462)` |
| Live Data → Start/Stop | `[140,410][488,472]` | `(316, 441)` |
| Terminal → ATI | `[237,1521][301,1560]` | `(269, 1540)` |
| Terminal → 010C | `[597,1521][683,1560]` | `(640, 1540)` |

## Notes
- All coordinates verified on Samsung Galaxy A16 at 1080×2340. VLink MAC: `13:E0:2F:8D:53:68`
- Logcat: `-v threadtime *:V --pid` — verbose level, thread timestamps. Only dump on error.
- PID cards MUST show numeric values during streaming. "No response", blank, or "--" = PIDS_NOT_DISPLAYING.
- The "0 / 4 PIDs + blank screen" bug is the primary known failure mode.
