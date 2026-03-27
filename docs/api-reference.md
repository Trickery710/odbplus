# API Reference

## `ObdService` (core-protocol)

The primary OBD-II facade. Inject via Hilt.

### State Flows

| Flow | Type | Description |
|------|------|-------------|
| `connectionState` | `StateFlow<ConnectionState>` | `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `ERROR` |
| `supportedPids` | `StateFlow<Set<String>?>` | null = disconnected; Set of hex PID codes confirmed by ECU |
| `discoveryState` | `StateFlow<PidDiscoveryState>` | `IDLE`, `DISCOVERING`, `COMPLETE`, `FAILED` |

### Methods

```kotlin
fun onTransportReady(label: String)
```
Call after `TransportRepository.connect()` succeeds. Triggers UOAPL initialization.

```kotlin
suspend fun sendCommand(pid: ObdPid): ObdResponse
```
Send a single OBD-II PID request. Throws `ObdException` on failure or `NoData`.

```kotlin
suspend fun runPidDiscovery()
```
Runs bitmap discovery for all supported PIDs (modes 0x00, 0x20, 0x40, …). Updates `supportedPids` flow on completion.

```kotlin
fun preloadSupportedPids(pidCodes: Set<String>)
```
Load PID support from cache without running discovery. Call when cache is valid.

---

## `TransportRepository` (core-transport)

### Methods

```kotlin
suspend fun connect(label: String, device: BluetoothDevice): Result<Unit>
suspend fun connect(label: String, host: String, port: Int): Result<Unit>
fun disconnect()
```

### State Flows

```kotlin
val connectionState: StateFlow<ConnectionState>
val activeTransport: StateFlow<ObdTransport?>
```

---

## `VinDecodeCoordinator` (app/vin/coordinator)

Injected into `ConnectViewModel`. Call after VIN is read from the ECU.

```kotlin
fun onVinDiscovered(vin: String)  // 5s debounce, in-flight dedup
fun reset()                        // Call on disconnect
```

---

## `LiveDataViewModel` (app/live)

### Key Methods

```kotlin
fun resolveAndDiscoverPids()   // Call on connect; checks cache first
fun rescanSupportedPids()      // Force full bitmap discovery
fun togglePidSelection(pid: ObdPid)
fun selectPreset(preset: PidPreset)
fun togglePolling()            // Start/stop OBD polling loop
fun toggleLogging()            // Start/stop sensor log recording
fun startReplay(session: LogSession)
fun sortedFilteredPids(state: LiveDataUiState): List<PidDisplayState>
```

### State

```kotlin
val uiState: StateFlow<LiveDataUiState>
```

See `LiveDataUiState.kt` for full state definition.

---

## `AdapterSession` (core-protocol/session)

Managed by `ProtocolModule`. Not injected directly into the app layer — use `ObdService` instead.

### State Machine States

| State | Meaning |
|-------|---------|
| `IDLE` | Not connected |
| `CONNECTING` | Transport handshake in progress |
| `FINGERPRINTING` | Adapter detection (ATGM, ATI, ATRV probes) |
| `DRIVER_INIT` | ATE0/ATL0/ATS1/ATH1/ATCAF1 sequence |
| `PROTOCOL_NEGOTIATION` | Direct 0100 → ATSP5 → ATSP3 fallback |
| `SESSION_ACTIVE` | Ready for PID queries |
| `STREAMING` | Active polling in progress |
| `SESSION_ERROR` | Unrecoverable error; triggers reconnect |

---

## `ObdPid` Enum (core-protocol)

Each entry has:
```kotlin
val code: String          // 2-char hex, e.g. "0C" for RPM
val description: String   // Human-readable
val unit: String          // "RPM", "°C", "%", etc.
val expectedBytes: Int    // 1–4
fun parse(bytes: ByteArray): Double
```

Key PIDs used frequently:
| PID | Code | Unit |
|-----|------|------|
| `ENGINE_RPM` | 0C | RPM |
| `VEHICLE_SPEED` | 0D | km/h |
| `ENGINE_COOLANT_TEMP` | 05 | °C |
| `THROTTLE_POSITION` | 11 | % |
| `ENGINE_LOAD` | 04 | % |
| `MAF_FLOW_RATE` | 10 | g/s |
| `CONTROL_MODULE_VOLTAGE` | 42 | V |
| `SHORT_TERM_FUEL_TRIM_BANK1` | 06 | % |
| `LONG_TERM_FUEL_TRIM_BANK1` | 07 | % |

---

## NHTSA VIN Decode

Endpoint: `GET https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVin/{VIN}?format=json`

Handled by `NhtsaVinDecoderService` (Ktor). Results cached in Room for 30 days (verified) / 7 days (partial). Exponential backoff on network errors.
