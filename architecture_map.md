# ODBPlus Architecture Map

## Project Overview
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material3)
- **DI**: Hilt
- **DB**: Room v3
- **Network**: Ktor
- **Build**: Gradle (KSP)
- **Target SDK**: 36 / Min SDK: 24

---

## Module Dependency Graph

```
app
 ├── core-protocol
 │    └── core-transport
 └── core-transport

feature-live           (placeholder)
feature-logger         (placeholder)
feature-diagnostics    (placeholder)
feature-ecu-profile    (placeholder)
data-schema            (placeholder)
```

---

## Module Descriptions

### `core-transport`
Low-level TCP/Bluetooth socket layer.

| File | Role |
|------|------|
| `ObdTransport` | Interface for all transport backends |
| `BaseTransport` | Shared read/write logic, timeout management |
| `TcpTransport` | TCP socket implementation |
| `BluetoothTransport` | Android BT socket implementation |
| `TransportRepository` | Manages active transport, exposes `ConnectionState` flow |

**Entry point**: `TransportRepository.connect(label, device)`

---

### `core-protocol`
ELM327/OBD-II command layer and UOAPL (Universal OBD-II Adapter Protocol Layer).

| Subpackage | Role |
|------------|------|
| `ObdPid` (1,501 lines) | Enum of 200+ OBD-II PIDs with parsers |
| `ObdService` | High-level API: `sendCommand()`, `runPidDiscovery()`, `supportedPids` flow |
| `ObdParser` | Hex → typed value parsing; handles ISO/KWP headers |
| `adapter/` | Device fingerprinting: `AdapterFingerprinter`, `DeviceProfile`, `KnownDeviceRegistry` |
| `driver/` | `ElmDriver`, `StnDriver`, `Esp32Driver` — AT command sequences per adapter family |
| `session/` | `AdapterSession` (state machine), `HealthMonitor`, `ProtocolFallback` |
| `isotp/` | Software ISO-TP multi-frame reassembly |
| `signalset/` | Signal definitions |
| `diagnostic/` | `DiagnosticTroubleCode`, DTC parsing |
| `di/ProtocolModule` | Hilt bindings |

**Data flow**: `TransportRepository.connect()` → `ObdService.onTransportReady()` → `AdapterSession.onTransportConnected()` → fingerprint → driver init → protocol negotiation → `SESSION_ACTIVE`

---

### `app`
Main application module. All UI, business logic, and persistence.

#### Data Layer (`data/db/`)
- Room database v3 (13 entities, 14 DAOs)
- Migrations: v1→2, v2→3 (VIN decode tables)
- Key entities: `VehicleEntity`, `SensorLogEntity`, `DtcLogEntity`, `FreezeFrameEntity`
- VIN entities: `VehicleIdentityEntity`, `VehicleVinValidationEntity`, `VehicleVinRawDecodeEntity`, `VehicleVinCachePolicyEntity`

#### Feature Packages

| Package | Description | Key Files |
|---------|-------------|-----------|
| `live/` | Real-time OBD-II data | `LiveDataViewModel`, `LiveDataUiState`, `PidPreset`, `PidMetadata`, `PollingManager`, `LogSessionManager`, `ReplayManager`, `DerivedMetric`, `DtcSensorMap`, `SupportedPidCacheRepository` |
| `ai/` | Claude API chat & diagnostics | `AiChatViewModel`, `ClaudeApiService`, `DiagnosticPromptBuilder`, `VehicleContextProvider` |
| `vin/` | VIN decode pipeline | `VinValidator`, `VinVerificationEngine`, `VinCachePolicy`, `NhtsaVinDecoderService`, `VinDecoderRepository`, `VinDecodeCoordinator` |
| `expertdiag/` | Knowledge-based automated tests | `DiagnosticEngine`, `AutoTestRegistry`, `GuidedTestManager`, 7 auto tests, 5 guided tests |
| `diagnostic/` | Legacy diagnostic tests | 5 test files (older system, not removed yet) |
| `connect/` | Connection management | `ConnectViewModel` |
| `session/` | Vehicle session lifecycle | `VehicleSessionManager`, `SensorLoggingService` |
| `settings/` | App preferences | `SettingsRepository` (DataStore) |
| `vehicle/` | Vehicle profile management | |
| `parts/` | Parts catalog | |
| `tools/` | Tools catalog | |
| `guidedtest/` | Guided test orchestration | `GuidedTestViewModel` |
| `nav/` | Compose navigation graph | `AppNav` |
| `ui/` | All screens (20+) | `HomeScreen`, `ConnectScreen`, `LiveScreen`, `AiChatScreen`, `DiagnosticHudScreen`, … |

---

## Data Flow

```
User Action
    │
    ▼
Compose Screen (ui/)
    │ collects StateFlow
    ▼
ViewModel (HiltViewModel)
    │ calls
    ├──► ObdService (core-protocol)
    │         │
    │         ▼
    │    AdapterSession ──► TransportRepository ──► BT/TCP Socket
    │
    ├──► Room DAOs (data/db/)
    │
    └──► External APIs (Ktor)
              └── NHTSA VIN decode
              └── Claude AI
```

---

## Navigation Routes

```
home → odb_hub → live_data
                 ai_chat
                 diagnostic_hud
                 expert_diag
                 logs
                 settings
                 vehicle_detail
                 parts_and_tools
                 terminal
                 session_detail
                 guided_rpm_test
```
