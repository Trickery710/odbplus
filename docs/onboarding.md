# Developer Onboarding Guide

## Overview

ODBPlus is an Android OBD-II diagnostic app (Kotlin, Jetpack Compose) for
communicating with ELM327-compatible Bluetooth adapters. It provides live
sensor data, DTC reading/clearing, expert diagnostics, vehicle history,
and AI-powered chat with Claude/Gemini context injection.

**Minimum Android:** API 26 (Android 8.0)
**Target SDK:** 36 (Android 15)
**Build tool:** Gradle with Kotlin DSL
**DI:** Hilt
**DB:** Room v3 (14 entities)
**HTTP:** Ktor (VIN decode via NHTSA, AI APIs via Anthropic/Gemini)
**Navigation:** Jetpack Navigation Compose

---

## Module Map

```
core-transport/   — Raw TCP/BT socket I/O (no OBD knowledge)
core-protocol/    — OBD-II protocol layer + UOAPL adapter abstraction
app/              — All UI, ViewModels, Repositories, Room DB
feature-*/        — Reserved stubs for future module extraction
```

All packages use `com.obdplus.*`.

---

## Building

```bash
# Debug build
./gradlew :app:assembleDebug

# Verify core protocol compiles
./gradlew :core-protocol:compileDebugKotlin

# Run unit tests
./gradlew test
```

---

## Key Entry Points

| File | Role |
|------|------|
| `app/OdbPlusApp.kt` | Application class with Hilt `@HiltAndroidApp` |
| `app/MainActivity.kt` | Single activity host for Compose nav |
| `app/nav/AppNav.kt` | Full nav graph (5 bottom tabs + nested routes) |
| `core-protocol/ObdService.kt` | Primary OBD API for ViewModels |
| `core-protocol/session/AdapterSession.kt` | UOAPL state machine |
| `core-transport/TransportRepository.kt` | Connect/disconnect transport |

---

## Connection Flow (Critical Path)

```
1. User picks Bluetooth device in ConnectScreen
2. ConnectViewModel calls TransportRepository.connect(mac, 1, isBluetooth=true)
3. On success: ObdService.onTransportReady("Bluetooth")
4. AdapterSession.onTransportConnected() takes over:
   a. AdapterFingerprinter.fingerprint() — sends ATZ, ATI, ATDP
   b. DriverFactory.create(profile) — picks ElmDriver / StnDriver / Esp32Driver
   c. driver.initialize() — ATE0 → ATL0 → ATS1 → ATH1 → ATCAF1
   d. negotiateProtocol() — fast path 0100, then ATSP0, then fallback loop
   e. Transition to SESSION_ACTIVE
5. ConnectViewModel reads VIN via Mode 09, stores in Room, triggers VIN decode
```

**Never** send ATSP0 before the fast-path `0100`. See memory notes for the
full list of commands that must not be sent (ATSH 7DF, ATCAF0, etc.).

---

## Adding a New PID

1. Add an entry to `ObdPid.kt` (in `core-protocol`):
   ```kotlin
   MY_NEW_SENSOR(
       code = "XX",             // Mode 01 PID hex code
       description = "...",
       unit = "unit",
       expectedBytes = N,
       parser = { ba -> /* parse ba[0]..ba[N-1] → Double */ }
   )
   ```
2. Add an entry to `PidRegistry` in `app/live/PidMetadata.kt` for display metadata.
3. If the PID should appear in a specific category tab, add it to the relevant
   `PidCategory` set in `PidMetadata.kt`.

---

## Adding a New Screen

1. Create `app/ui/YourScreen.kt` as a `@Composable fun YourScreen(...)`.
2. Create `app/viewmodel/YourViewModel.kt` as a `@HiltViewModel`.
3. Add the route to `AppNav.kt` in the appropriate nav graph section.
4. If it's a new bottom tab, add it to `BottomNavItem.kt`.

---

## Database Changes

Room is at version 3. Any schema change requires:

1. Increment version in `OdbDatabase.kt`.
2. Write a `MIGRATION_N_M` object with the SQL DDL.
3. Add it to `databaseBuilder().addMigrations(...)`.
4. Write an instrumented test (see `stability_improvements.md`).

---

## Test Device

- **Hardware:** Samsung Galaxy A16 (1080×2340)
- **Adapter:** ELM327 v2.3 Bluetooth, "Android-Vlink", MAC `13:E0:2F:8D:53:68`
- **Vehicle protocol:** KWP2000 Fast Init (ISO 14230-4 KWP FAST)
- **Connect skill:** `.claude/commands/connect-vlink.md`

---

## Common Pitfalls

| Pitfall | Consequence | Fix |
|---------|-------------|-----|
| Sending ATSP0 before 0100 | "UNABLE TO CONNECT" on KWP vehicles | Let fast path try 0100 first |
| Sending ATSH 7DF | BUS INIT: ERROR on ISO/KWP | Never call probeCustomHeaders() |
| Sending ATCAF0 | All CAN PIDs return NO DATA | ATCAF1 is the required default |
| Missing ATS1 after fingerprint | KWP responses unspaced → parse overflow | ATS1 is step 3 of driver init |
| Using `Thread.sleep()` in coroutines | Blocks the IO thread pool | Always use `delay()` |
| Catching CancellationException | Breaks coroutine cancellation | Always re-throw it |
