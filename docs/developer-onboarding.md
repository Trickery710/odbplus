# Developer Onboarding

## Prerequisites
- Android Studio Ladybug or later
- JDK 17+
- Android SDK 36
- Bluetooth adapter: ELM327 v2.3 "Android-Vlink" (MAC `13:E0:2F:8D:53:68`)
- Test device: Samsung Galaxy A16 (1080×2340)

## First Build
```bash
./gradlew :app:compileDebugKotlin    # fast compile check
./gradlew :app:assembleDebug         # full APK build
```

## Module Structure
```
odbplus/
├── app/                  Main application
├── core-transport/       TCP/Bluetooth socket layer
├── core-protocol/        ELM327/OBD-II + UOAPL
├── feature-live/         (placeholder — roadmap)
├── feature-logger/       (placeholder — roadmap)
├── feature-diagnostics/  (placeholder — roadmap)
├── feature-ecu-profile/  (placeholder — roadmap)
└── data-schema/          (placeholder — roadmap)
```

## Key Concepts

### Transport → Protocol → UI Data Flow
1. User taps Connect → `TransportRepository.connect(label, device)`
2. Connection established → `ObdService.onTransportReady(label)` called
3. `AdapterSession` fingerprints the adapter, selects driver, negotiates protocol
4. `SESSION_ACTIVE` emitted → LiveData screen calls `resolveAndDiscoverPids()`
5. PID cache checked → either `preloadSupportedPids()` or `runPidDiscovery()`
6. User starts polling → `PollingManager` loops through selected PIDs at `pollIntervalMs`

### UOAPL State Machine
`AdapterSession` state transitions:
```
IDLE → CONNECTING → FINGERPRINTING → DRIVER_INIT → PROTOCOL_NEGOTIATION → SESSION_ACTIVE → STREAMING
                                                                                     ↓
                                                                               SESSION_ERROR
```

### Protocol Negotiation (KWP2000 vehicles)
**Critical**: Do NOT send `ATSP0` first — this resets the ELM protocol hint and causes "UNABLE TO CONNECT".
The fast path: send `0100` directly after `ATZ`. The ELM retains its last-used protocol (KWP FAST) across resets.
See `ProtocolFallback` for the ordered retry sequence.

### VIN Decode Pipeline
```
VIN discovered (from `0902` response)
    → VinValidator.validate()
    → VinDecodeCoordinator.onVinDiscovered()      [5s debounce, in-flight dedup]
    → VinDecoderRepository.decode()
         → local check (VehicleIdentityDao)
         → NhtsaVinDecoderService (Ktor → vpic.nhtsa.dot.gov)
    → VinInfoCard shows result
```

### Database
Room v3. Migrations in `AppDatabase.kt`. Never add a column without a migration.

## Key Files to Know

| File | Why It Matters |
|------|---------------|
| `core-protocol/ObdPid.kt` | All 200+ OBD-II PIDs — add new PIDs here |
| `core-protocol/ObdParser.kt` | Response parsing — KWP header handling is here |
| `core-protocol/session/AdapterSession.kt` | Protocol state machine |
| `core-protocol/driver/ElmDriver.kt` | ELM327 AT command sequence |
| `app/live/LiveDataViewModel.kt` | OBD polling orchestration |
| `app/live/LiveDataUiState.kt` | All data models for the live screen |
| `app/live/PidMetadata.kt` | `PidDefinition`, `PidRegistry`, `SortOrder`, `LiveDisplayMode` |
| `app/nav/AppNav.kt` | All navigation routes |
| `app/ui/theme/` | Color tokens — always use theme tokens, not raw hex |

## Running Tests
```bash
./gradlew :app:testDebugUnitTest
```
43 tests pass (VIN subsystem). Protocol and polling layers currently lack unit tests.

## Adding a New OBD PID
1. Add entry to `ObdPid` enum in `core-protocol/ObdPid.kt`
2. Add `PidDefinition` entry in `live/PidMetadata.kt` (`PidRegistry` map)
3. Optionally add to a `PidPreset` in `live/PidPreset.kt`
4. If derived metric uses it, update `DerivedMetricCalculator` in `live/DerivedMetric.kt`

## Adding a New Screen
1. Create `ui/NewFeatureScreen.kt`
2. Add route constant in `nav/AppNav.kt`
3. Add `composable("route") { NewFeatureScreen(...) }` in the `NavHost`
4. Link from an existing screen (e.g., add tile to `OdbHubScreen`)

## Commit Convention
Conventional commits preferred: `feat:`, `fix:`, `refactor:`, `chore:`, `test:`.
