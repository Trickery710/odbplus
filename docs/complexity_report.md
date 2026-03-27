# Complexity Report

## File Size Analysis

### Large Composables (>500 LOC) — Primary Refactor Candidates

| File | LOC | Issue |
|------|-----|-------|
| `ui/AiChatScreen.kt` | ~1,246 | Header, pager, 3 dialogs, message list in one file |
| `ui/DiagnosticHudScreen.kt` | ~1,053 | DTC tiles, test results, gauges, export UI |
| `ui/LiveScreen.kt` | ~980 | 4 display modes, category tabs, chart, DTC filter |
| `ui/GuidedRpmTestScreen.kt` | ~865 | Test orchestration UI, RPM visualisation |
| `ui/DiagnosticsScreen.kt` | ~643 | DTC list, monitor status, action buttons |
| `ui/VehicleDetailScreen.kt` | ~621 | VIN info, session history, DTC summary |
| `ui/ConnectScreen.kt` | ~601 | Bluetooth scan, device list, permission dialogs |

**Impact:** Hard to navigate, slow IDE performance, difficult to test or reuse sub-components.

### Large ViewModels (>200 LOC)

| File | LOC | Injected Deps |
|------|-----|--------------|
| `live/LiveDataViewModel.kt` | ~554 | 10 |
| `ai/AiChatViewModel.kt` | ~373 | 7 |
| `guidedtest/GuidedTestViewModel.kt` | ~295 | 5 |
| `ai/VehicleContextViewModel.kt` | ~176 | 5 |
| `connect/ConnectViewModel.kt` | ~188 | 6 |

### Large Protocol Files

| File | LOC | Note |
|------|-----|------|
| `protocol/ObdPid.kt` | ~1,501 | 100+ enum entries, each with parser lambda |
| `protocol/session/AdapterSession.kt` | ~530 | Acceptable — well-structured state machine |
| `protocol/ObdService.kt` | ~469 | Acceptable — clear section separation |
| `protocol/ObdParser.kt` | ~406 | Acceptable — well-documented parsing logic |

---

## Identified Code Smells

### 1. Monolithic Composable Screens

**AiChatScreen.kt** contains at minimum:
- `ChatHeader` (toolbar, provider chip, connection status)
- `MessageList` (chat bubbles with markdown rendering)
- `ChatInputBar` (text field, send button, attach context button)
- `ApiKeyDialog` (API key entry for Claude)
- `GeminiKeyDialog` (API key entry for Gemini)
- `ProviderSelector` (bottom sheet or dialog)

All co-located in one 1,246-line file. Each component can and should be an
independent `@Composable` function in its own file under `ui/chat/`.

**LiveScreen.kt** embeds 4 display-mode rendering subtrees (Numeric, Gauge,
Graph, Tiles) plus category tab logic, DTC badge, and polling controls in a
single file. Each mode composable is independently testable and reusable.

### 2. High Constructor Fan-In in ViewModels

`LiveDataViewModel` with 10 injected dependencies is a clear sign it owns
too much responsibility. It coordinates polling, logging, PID discovery,
profile building, and settings observation simultaneously.

**Recommended split:**
- `LivePollingCoordinator` — owns `ObdService`, `PollingManager`, `SupportedPidCacheRepository`, `ResolveSupportedPidsUseCase`
- `LiveLoggingCoordinator` — owns `SensorLoggingService`, `LogSessionRepository`, `VehicleSessionManager`
- `LiveDataViewModel` — injects both coordinators + `SettingsRepository` + `VehicleContextProvider`

### 3. No Centralized Error Handling

Every ViewModel repeats:
```kotlin
try {
    val result = repo.doWork()
    _uiState.update { it.copy(result = result, error = null) }
} catch (e: Exception) {
    _uiState.update { it.copy(error = e.message) }
}
```

Issues:
- No distinction between user-facing errors vs internal bugs
- No retry logic — user must restart the entire action
- `e.message` can be null (NPE in some Kotlin versions)
- No centralized logging of errors

### 4. ObdPid Enum Size

`ObdPid.kt` at 1,501 LOC is an enormous enum. Each entry encodes a parser
lambda inline, leading to patterns like:

```kotlin
ENGINE_RPM(code = "0C", ..., expectedBytes = 2,
    parser = { ba -> ((ba[0].toInt() and 0xFF) * 256 + (ba[1].toInt() and 0xFF)) / 4.0 }),
VEHICLE_SPEED(code = "0D", ..., expectedBytes = 1,
    parser = { ba -> (ba[0].toInt() and 0xFF).toDouble() }),
```

The enum is correct and functional. For maintainability, common parser
formulas (2-byte unsigned int, 1-byte signed, etc.) could be extracted as
named lambdas at the top of the file to reduce repetition.

### 5. ODBPlus_Reconnect_Upgrade — Dead Code

The `ODBPlus_Reconnect_Upgrade/` directory contains a prototype reconnect
implementation (206 LOC) that is **not referenced by any build target**.
Its reconnect logic has been superseded by `AdapterSession.startReconnectLoop()`.

This directory adds confusion for new developers and should be removed or
archived. It is excluded from Gradle so has zero runtime impact.

### 6. Hardcoded Magic Numbers

Scattered constants that would benefit from being in a central `Constants.kt`:

| Location | Value | Meaning |
|----------|-------|---------|
| `PollingManager` | `MAX_BATCH_SIZE = 15` | Max PIDs per poll cycle |
| `PollingManager` | `INTER_PID_DELAY_MS = 25L` | Pause between PID commands |
| `VinCachePolicy` | `BACKOFF_BASE_MS = 60_000` | Retry base (1 minute) |
| `AdapterSession` | `NEGOTIATION_TIMEOUT_MS = 30_000L` | Max negotiation time |
| `AdapterSession` | `MAX_CONSECUTIVE_FAILURES = 5` | Failures before recovery |
| `AdapterSession` | `MAX_RECONNECT_ATTEMPTS = 10` | Max reconnect retries |

All are already declared as `private const val` in their respective files,
which is correct. A shared constants file is only warranted if these values
need to be tuned together or referenced from tests.

---

## What Was Initially Flagged But Is Already Fixed

The following anti-patterns were suspected but are **correctly implemented**
in the actual code:

| Suspected Issue | Actual State |
|----------------|--------------|
| `Thread.sleep()` in polling loops | `delay()` used throughout ✓ |
| Orphaned keepalive/reconnect jobs | `cancelAndJoin()` in `disconnect()` ✓ |
| Missing negotiation timeout | `withTimeoutOrNull(30_000)` in place ✓ |
| `close()` deadlock on blocked `read()` | Streams closed before `cancelAndJoin()` ✓ |
| Missing bounds check in `ObdParser` | `parseValue` checks `dataBytes.size < pid.expectedBytes` ✓ |
| Non-cancellable blocking I/O | `Dispatchers.IO` + `SocketTimeoutException` handling ✓ |

---

## Test Coverage

**5 unit test files, all in the VIN subsystem:**
- `VinValidatorTest` (166 LOC)
- `VinVerificationEngineTest` (151 LOC)
- `VinCachePolicyTest` (181 LOC)
- `PayloadBuilderTest` (166 LOC)
- `RpmRangeValidationTest` (183 LOC)

**No tests for:**
- `ObdParser` — highest-risk parsing logic, many edge cases
- `AdapterSession` — complex state machine
- `ProtocolFallback` — ordering and retry logic
- `IsoTpAssembler` — multi-frame reassembly
- All ViewModels — state transition correctness
- All Repositories — data access correctness

Adding tests for `ObdParser` and `IsoTpAssembler` would provide the highest
return on investment, as these handle the most complex transformations with
the most edge cases (ISO headers, unspaced hex, negative responses, etc.).
