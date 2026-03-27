# ODBPlus Architecture Map

## Module Overview

```
odbplus/
├── core-transport/    ~670 LOC   Raw socket I/O layer
├── core-protocol/   ~5,400 LOC  OBD-II protocol + UOAPL
├── app/            ~27,000 LOC  UI, ViewModels, Repositories, DB
├── feature-*/          (stubs)  Reserved for future feature modules
└── ODBPlus_Reconnect_Upgrade/  WIP prototype — NOT integrated
```

**Total production code: ~33,000 LOC**

---

## Module Dependency Graph

```
:app
├── :core-protocol
│   └── :core-transport
└── :core-transport

:core-transport  ← Android framework + Kotlin coroutines only
```

All packages use `com.obdplus.*` (corrected from `com.odbplus` typo, March 2026).

---

## Layer Architecture

```
┌──────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                              │
│  AiChatScreen · LiveScreen · DiagnosticHudScreen · ...  │
└──────────────────────┬───────────────────────────────────┘
                       │ StateFlow<UiState>
┌──────────────────────┴───────────────────────────────────┐
│  ViewModel Layer (@HiltViewModel)                        │
│  LiveDataViewModel · AiChatViewModel · ConnectViewModel  │
└──────────────────────┬───────────────────────────────────┘
                       │ suspend fun / Flow
┌──────────────────────┴───────────────────────────────────┐
│  Repository / Use-Case Layer                             │
│  VinDecoderRepository · SupportedPidCacheRepository     │
│  LogSessionRepository · AiSettingsRepository            │
└──────┬───────────────────────────────────┬───────────────┘
       │                                   │
┌──────┴────────────┐           ┌──────────┴──────────────┐
│  Protocol Layer   │           │  Database Layer (Room)  │
│  ObdService       │           │  OdbDatabase v3         │
│  ObdParser        │           │  14 entities, 14 DAOs   │
│  ObdPid enum      │           └─────────────────────────┘
└──────┬────────────┘
       │
┌──────┴────────────────────────────────────────────────────┐
│  UOAPL (Universal OBD Adapter Protocol Layer)             │
│  AdapterSession · AdapterFingerprinter · DriverFactory   │
│  ElmDriver · StnDriver · Esp32Driver                     │
│  ProtocolFallback · HealthMonitor · IsoTpAssembler       │
└──────┬────────────────────────────────────────────────────┘
       │
┌──────┴────────────────────────┐
│  Transport Layer              │
│  BaseTransport (mutex I/O)    │
│  BluetoothTransport (RFCOMM)  │
│  TcpTransport (TCP socket)    │
└───────────────────────────────┘
```

---

## Subsystems

### core-transport

**Purpose:** Provides the raw byte I/O channel over Bluetooth RFCOMM or TCP sockets.

**Key design decisions:**
- `commandMutex` serialises write+read pairs to prevent keepalive ping responses
  from colliding with in-flight command handlers.
- `readerLoop` runs on `Dispatchers.IO` (blocking read) but within the external
  coroutine scope for structured concurrency.
- `drainChannel()` clears the inbound channel before a new command to prevent
  stale responses leaking from the previous command.
- `close()` closes streams before `cancelAndJoin()` to unblock the native
  `read()` call (coroutine cancellation cannot interrupt blocking I/O).
- Frame terminator is `>` (ELM327 prompt).

**Public API:** `ObdTransport` interface → `connect / writeLine / readUntilPrompt / sendCommand / drainChannel / close`.

---

### core-protocol / UOAPL

**Purpose:** ELM/OBD command abstraction + Universal OBD Adapter Protocol Layer.

**UOAPL state machine:**
```
DISCONNECTED
  → TRANSPORT_CONNECTED   (transport.connect success)
  → DEVICE_IDENTIFIED     (AdapterFingerprinter completes)
  → PROTOCOL_DETECTED     (0100 returns "41" or "NO DATA")
  → SESSION_ACTIVE        (ready for PID queries)
  ↔ STREAMING             (high-rate CAN mode)
  → ERROR_RECOVERY        (soft-reset + re-init)
  → RECONNECTING          (transport dropped, backoff retry)
```

**Fingerprinting sequence:** ATZ → ATI → ATDP → firmware banner → `KnownDeviceRegistry` lookup → `DeviceProfile`.

**Driver initialisation order (critical for KWP2000):**
`ATE0 → ATL0 → ATS1 → ATH1 → ATCAF1`
(ATS1 must restore spaces removed by fingerprinter's ATS0 or KWP responses arrive unspaced.)

**Protocol negotiation fast path:** Direct `0100` after ATZ (uses ELM's stored protocol hint, ~300 ms).
**Fallback order:** CAN 11-bit/500k → CAN 29-bit/500k → CAN 11-bit/250k → CAN 29-bit/250k
→ KWP2000 fast (ATSP5) → KWP2000 5-baud (ATSP4) → ISO 9141-2 (ATSP3) → J1850 PWM → J1850 VPW.
KWP2000 fast is ordered before ISO 9141-2 because KWP init is ~200 ms vs ISO 5-baud ~2.7 s.

**Negotiation hard ceiling:** `withTimeoutOrNull(30_000)` wraps the entire fallback loop.

---

### app/

**Navigation (AppNav.kt):**
```
Bottom tabs (5):
  ai_chat               → AiChatScreen
  odb_hub (nested)      → OdbHubScreen, ConnectScreen, LiveScreen,
                          DiagnosticHudScreen, TerminalScreen, LogsScreen,
                          ExpertDiagnosticScreen, GuidedRpmTestScreen
  parts                 → PartsAndToolsScreen
  vehicle (nested)      → VehicleHistoryScreen, VehicleDetailScreen,
                          SessionDetailScreen
  settings              → SettingsScreen
```

**ViewModel pattern:**
- All ViewModels are `@HiltViewModel` with `MutableStateFlow<UiState>`.
- `init {}` launches parallel `Flow.collect` subscribers for reactive state.
- Actions use `viewModelScope.launch` + try/catch with `_uiState.update`.

**Database (Room v3, 14 entities):**
- v1: Vehicle, VehicleSession, SensorLog, DtcLog, FreezeFrame, EcuModule, TestResult
- v2: VehicleProfile, SupportedPid (PID cache with confidence scoring)
- v3: VehicleIdentity, VinValidation, VinRawDecode, VinCachePolicy (VIN decode subsystem)

**DI (Hilt):**
- `TransportModule` / `CoroutineModule` — in `core-transport`
- `ProtocolModule` — in `core-protocol`
- `DatabaseModule` — in `app`
- `VinModule` (@VinHttpClient qualifier, isolated Ktor client)

---

## Critical Data Flows

### Connect Flow
```
User selects BT device
→ TransportRepository.connect(mac, port, BT)
→ ObdService.onTransportReady("Bluetooth")
→ AdapterSession.onTransportConnected()
  → Fingerprint → identify device
  → Create driver, initialize (ATE0…ATCAF1)
  → Negotiate protocol (fast path → fallback)
  → Transition → SESSION_ACTIVE
→ ConnectViewModel.triggerVinDecoder()
→ VinDecodeCoordinator.onVinDiscovered(vin) [5s debounce]
→ NHTSA decode → cache (30d TTL)
→ VehicleIdentityEntity persisted
```

### Live Data Flow
```
User opens LiveScreen
→ LiveDataViewModel.resolveAndDiscoverPids()
  → SupportedPidCacheRepository.resolve()
  → If NeedsDiscovery: ObdService.runPidDiscovery() (bitmap queries)
→ PollingManager.start(supportedPids, 500ms interval)
  → Each cycle: obdService.query(pid) for each PID in batch (max 15)
  → 25ms inter-PID delay (ECU breathing room)
→ _pidValues updated → UI re-renders in selected display mode
→ Optional: SensorLoggingService.record() → Room SensorLog
```

### Diagnostic Flow
```
User taps "Run Diagnostics"
→ ObdService.readDtcs() → List<DiagnosticTroubleCode>
→ ObdService.readMonitorStatus() → bitmask
→ DiagnosticKnowledgeBase.lookup(dtcCode) → KnowledgeBaseEntry
→ AutomaticTestRunner.run(selectedTests) [7 auto tests]
→ Optional: GuidedTestManager.start(test) [5 guided tests]
→ DiagnosticResultAnalyzer.analyze() → root cause probabilities
→ VehicleContextProvider updated → available to AI chat
```

### AI Chat Flow
```
User sends message
→ VehicleContextProvider.current() → inject DTCs, sensor data, VIN
→ ClaudeApiService.chat() or Gemini API (provider from AiSettingsRepository)
→ Streaming or full response → ChatRepository.addMessage()
→ UI shows typing indicator → response rendered
```
