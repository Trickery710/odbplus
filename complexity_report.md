# Complexity Report

Generated: 2026-03-27

---

## Large Files (>300 lines)

| File | Lines | Issues |
|------|-------|--------|
| `core-protocol/ObdPid.kt` | 1,501 | Single 200+ entry enum — acceptable; splitting by category risks breaking enum ordinal ordering |
| `ui/AiChatScreen.kt` | 1,246 | All composables in one file; no component subdirectory |
| `ui/DiagnosticHudScreen.kt` | 1,053 | All composables in one file; well-sectioned with comments |
| `ui/LiveScreen.kt` | 980 | All composables in one file; well-sectioned, pane boundaries clear |
| `ui/GuidedRpmTestScreen.kt` | 865 | Large but self-contained test UI |
| `ui/DiagnosticsScreen.kt` | 643 | |
| `ui/VehicleDetailScreen.kt` | 621 | |
| `ui/ConnectScreen.kt` | 601 | |
| `live/LiveDataViewModel.kt` | ~420 (post-refactor) | **Reduced** — data classes extracted |
| `ui/SettingsScreen.kt` | 490 | |
| `ui/OdbHubScreen.kt` | 486 | |
| `core-protocol/AdapterSession.kt` | 530 | Complex state machine — complexity is warranted |
| `core-protocol/ObdService.kt` | 487 | |
| `core-protocol/ObdParser.kt` | 406 | |

---

## Identified Code Smells

### 1. Oversized UI Files (HIGH)
**Files**: `AiChatScreen.kt` (1,246), `DiagnosticHudScreen.kt` (1,053), `LiveScreen.kt` (980)
**Issue**: All composable components are private functions in one file. IDE navigation is slow; PR reviews touch one giant file.
**Fix**: Extract into `ui/aichat/components/`, `ui/diagnostic/components/`, `ui/live/components/` packages with `internal` visibility.

### 2. Duplicate Diagnostic Systems (MEDIUM)
**Files**: `diagnostic/` (legacy, 5 tests) vs `expertdiag/` (newer, 12 tests)
**Issue**: Two parallel diagnostic engines. `DiagnosticHudScreen` uses the legacy `diagnostic/` package. `ExpertDiagnosticScreen` uses `expertdiag/`. No clear migration timeline.
**Fix**: Document migration path; add deprecation notice to `diagnostic/`; plan consolidation into `expertdiag/`.

### 3. Empty Feature Modules (LOW)
**Modules**: `feature-live`, `feature-logger`, `feature-diagnostics`, `feature-ecu-profile`, `data-schema`
**Issue**: Each contains only `Placeholder.kt`. Add build overhead and module overhead with no benefit.
**Fix**: Either implement or remove. If these are roadmap items, keep but document intent in each `README.md`.

### 4. Wildcard Imports in Large UI Files (LOW)
**Files**: `LiveScreen.kt`, `AiChatScreen.kt`
```kotlin
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
```
**Issue**: Wildcard imports make IDE unused-import detection unreliable and slow down incremental compilation hints.
**Fix**: Replace with explicit imports (IDE can do this automatically: Optimize Imports).

### 5. Identical Icon in Log Toggle Button (LOW)
**File**: `LiveScreen.kt`, line 881
```kotlin
if (state.isLogging) Icons.Default.FiberManualRecord else Icons.Default.FiberManualRecord
```
**Issue**: Both branches use the same icon. The intent was likely `Icons.Default.RadioButtonUnchecked` (or similar) for the stopped state.
**Fix**: Verify intended icon for the non-logging state and update accordingly.

### 6. Mixed State Model in `LiveDataViewModel` (RESOLVED ✓)
**Was**: Data classes, `PidPreset` enum, and `LiveDataViewModel` class all in one 554-line file.
**Fixed**: `PidDisplayState`, `LoggedDataPoint`, `LogSession`, `ChartPoint`, `LiveDataUiState` → `LiveDataUiState.kt`. `PidPreset` → `PidPreset.kt`.

### 7. Hardcoded Color Values (LOW)
**Files**: `LiveScreen.kt`, `DiagnosticHudScreen.kt`
```kotlin
Color(0xFF4CAF50)  // green
Color(0xFFFF9800)  // orange
Color(0xFFF44336)  // red
```
**Issue**: These duplicate semantic colors that already exist in the theme (`GreenSuccess`, `AmberSecondary`, `RedError`). `DiagnosticHudScreen.kt` correctly uses theme tokens; `LiveScreen.kt` uses raw hex for some composables.
**Fix**: Replace raw hex in `LiveScreen.kt` with the theme color tokens from `ui/theme/`.

---

## Naming Consistency

All naming conventions are consistent across the codebase:

| Convention | Pattern | Status |
|------------|---------|--------|
| ViewModels | `*ViewModel` | ✓ 16 files |
| Repositories | `*Repository` | ✓ 14 files |
| Screens | `*Screen` | ✓ 20 files |
| Services | `*Service` | ✓ consistent |
| Entities | `*Entity` | ✓ 13 entities |
| DAOs | `*Dao` | ✓ 14 DAOs |
| Package name | `com.obdplus.*` | ✓ all files |

---

## Coupling Analysis

| Area | Assessment |
|------|-----------|
| `core-transport` ↔ `core-protocol` | Clean interface boundary via `ObdTransport` |
| `core-protocol` ↔ `app` | Clean via `ObdService` facade; app never reaches into `AdapterSession` directly |
| VIN subsystem | Well-isolated in `vin/` with its own DI module |
| Expert diag subsystem | Well-isolated in `expertdiag/` with its own UI components package |
| Live data subsystem | Slightly overloaded ViewModel; `sortedFilteredPids()` is a pure function that could move to a use case |

---

## Test Coverage

| Area | Tests | Status |
|------|-------|--------|
| `VinValidatorTest` | 20+ cases | ✓ |
| `VinVerificationEngineTest` | 15+ cases | ✓ |
| `VinCachePolicyTest` | 8+ cases | ✓ |
| `GuidedTestPayloadBuilder*` | Builder tests | ✓ |
| Protocol layer | None | ✗ Missing |
| Transport layer | None | ✗ Missing |
| LiveData polling | None | ✗ Missing |
| UI / integration | None | ✗ Missing |
