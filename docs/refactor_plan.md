# Refactor Plan

## Priority Classification

| Priority | Risk | Effort | Value |
|----------|------|--------|-------|
| P0 — Critical | Low | Low | High |
| P1 — High | Low | Medium | High |
| P2 — Medium | Medium | Medium | Medium |
| P3 — Low | Medium | High | Low |

---

## P0 — Remove Dead Code

### Remove ODBPlus_Reconnect_Upgrade/

The `ODBPlus_Reconnect_Upgrade/` directory is not referenced by any Gradle
module and its logic is superseded by `AdapterSession.startReconnectLoop()`.

**Action:** Delete the directory. Verify with `grep -r "ODBPlus_Reconnect_Upgrade" .`
to confirm no imports reference it.

**Risk:** None — excluded from all builds.

---

## P1 — Extract Composable Sub-Components

### AiChatScreen.kt (1,246 LOC → target ~300 LOC per file)

Create `app/src/main/java/com/obdplus/app/ui/chat/`:
```
chat/
  ChatMessageList.kt        — LazyColumn of chat bubbles
  ChatInputBar.kt           — TextField + send + context buttons
  ApiKeyDialog.kt           — Claude API key entry dialog
  GeminiKeyDialog.kt        — Gemini API key entry dialog
  ProviderSelector.kt       — AI provider bottom-sheet
```

`AiChatScreen.kt` becomes the layout scaffold that assembles these components.

### LiveScreen.kt (980 LOC → target ~200 LOC per file)

Create `app/src/main/java/com/obdplus/app/ui/live/`:
```
live/
  NumericModeContent.kt     — Grid of PID value cards
  GaugeModeContent.kt       — Circular gauge widgets
  GraphModeContent.kt       — Line chart with 120-point rolling window
  TilesModeContent.kt       — Compact tile layout
  PidCategoryTabs.kt        — Category filter tab row
```

### DiagnosticHudScreen.kt (1,053 LOC → target ~250 LOC per file)

Create `app/src/main/java/com/obdplus/app/ui/diagnostics/`:
```
diagnostics/
  DtcSection.kt             — DTC tile list
  MonitorStatusSection.kt   — Readiness monitor badges
  TestResultsSection.kt     — Auto/guided test result cards
  ExportDialog.kt           — Data export options
```

### GuidedRpmTestScreen.kt (865 LOC → target ~250 LOC per file)

Create `app/src/main/java/com/obdplus/app/ui/guidedtest/`:
```
guidedtest/
  RpmSweepChart.kt          — RPM visualisation widget
  SensorTargetDisplay.kt    — Target vs. actual sensor values
  TestInstructionCard.kt    — Step-by-step instruction overlay
  TestProgressIndicator.kt  — Pass/fail/running status
```

**Extraction rules:**
- Each sub-composable receives only the parameters it needs (no UiState pass-through).
- Sub-composables must not hold state — all state lives in the parent screen or ViewModel.
- Prefer `@Preview` annotations on each extracted component.

---

## P2 — Split LiveDataViewModel

`LiveDataViewModel` (10 injected dependencies) can be split:

### Option A: Internal extraction (safer, no DI changes)

Extract private helper objects within the ViewModel:

```kotlin
@HiltViewModel
class LiveDataViewModel @Inject constructor(
    private val polling: LivePollingOrchestrator,   // new: wraps ObdService, PollingManager
    private val logging: LiveLoggingOrchestrator,   // new: wraps SensorLoggingService, SessionManager
    private val settings: SettingsRepository,
    private val context: VehicleContextProvider
) : ViewModel()
```

`LivePollingOrchestrator` and `LiveLoggingOrchestrator` become `@Singleton`
classes injected via Hilt, reducing the ViewModel's constructor arity from
10 to 4.

### Option B: Coordinator pattern (more refactoring)

Create `LiveDataCoordinator` as a non-ViewModel class that owns the polling
loop and emits a `Flow<LiveDataState>`. The ViewModel becomes a thin adapter.

**Recommendation:** Option A for the next sprint. Option B for a larger
architectural cycle.

---

## P2 — Centralize Error Handling

Replace scattered try/catch with a sealed result type:

```kotlin
sealed class ObdResult<out T> {
    data class Success<T>(val value: T) : ObdResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : ObdResult<Nothing>()
    object Timeout : ObdResult<Nothing>()
}
```

Add an extension on `viewModelScope`:
```kotlin
fun ViewModel.launchWithError(
    onError: (String) -> Unit,
    timeoutMs: Long = 30_000L,
    block: suspend () -> Unit
) = viewModelScope.launch {
    try {
        withTimeoutOrNull(timeoutMs) { block() }
            ?: onError("Operation timed out")
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        onError(e.message ?: "Unknown error")
    }
}
```

---

## P2 — Add ObdParser Unit Tests

The parser handles the most complex data transformations and has many edge
cases documented in comments. A test suite should cover:

- Bare response: `"41 0C 1A F8"` → RPM value
- ISO/KWP header-wrapped: `"84 F1 10 41 0C 1A F8"` → same RPM value
- Unspaced: `"84F110410C1AF8"` → same RPM value
- BUS INIT alongside valid data: `"BUS INIT: OK\r41 0C 1A F8"` → RPM
- Negative response only: `"7F 01 12"` → `NoData`
- Mixed negative + positive: `"41 0C 1A F8 7F 01 12"` → RPM (7F stripped)
- Multi-ECU DTC: `"43 01 71 00 43 00 00"` → deduplicated DTC list
- VIN multi-frame: Mode 09 PID 02 response assembly
- `"NO DATA"` → `ObdResponse.NoData`
- `"UNABLE TO CONNECT"` → `ObdResponse.Error`
- `"BUS INIT: ERROR"` → `ObdResponse.Error`

---

## P3 — ObdPid Parser Formula Extraction

The 1,501-line `ObdPid.kt` enum can be made more maintainable by extracting
common parser formulas as named functions at the top of the file:

```kotlin
private object PidParsers {
    val oneByteUnsigned: (ByteArray) -> Double = { ba ->
        (ba[0].toInt() and 0xFF).toDouble()
    }
    val twoByteUnsigned: (ByteArray) -> Double = { ba ->
        ((ba[0].toInt() and 0xFF) shl 8 or (ba[1].toInt() and 0xFF)).toDouble()
    }
    val oneByteSignedOffset40: (ByteArray) -> Double = { ba ->
        (ba[0].toInt() and 0xFF) - 40.0
    }
    // ... etc.
}
```

Each enum entry then references a named formula rather than inlining the lambda,
making the intent clear and the formula reusable across PIDs.

**Risk:** Moderate — enum initialiser order matters in Kotlin. Test thoroughly.

---

## P3 — Feature Module Integration

The `feature-*` modules (`feature-diagnostics`, `feature-live`, `feature-ecu-profile`,
`feature-logger`) are currently stubs with only `Placeholder.kt` files.

If the app grows, the relevant code from `app/` can migrate to these modules:
- `feature-live` ← `app/live/`, `app/ui/LiveScreen.kt`
- `feature-diagnostics` ← `app/diagnostic/`, `app/expertdiag/`, diagnostic screens
- `feature-logger` ← `app/session/`, `app/ui/LogsScreen.kt`

This enables parallel team development and reduces `app/` module recompilation
time for unrelated changes.

**Prerequisites:** DI modules must be moved alongside features. `:app` becomes
a thin shell module with only navigation and application bootstrap.

---

## Implementation Order

1. Remove `ODBPlus_Reconnect_Upgrade/` ← immediate, zero risk
2. Extract composable sub-components ← 1–2 sprints, high value
3. Split `LiveDataViewModel` via Option A ← 1 sprint
4. Add `ObdParser` unit tests ← ongoing, foundational
5. Centralize error handling ← 1 sprint, prerequisites test coverage
6. `ObdPid` formula extraction ← 1 sprint, low urgency
7. Feature module migration ← only if team grows
