# Refactor Plan

Generated: 2026-03-27
Status: Phase 1 complete. Phases 2–5 are proposed.

---

## Completed Refactoring (Phase 1)

### [DONE] Extract `LiveDataUiState.kt`
**Rationale**: `LiveDataViewModel.kt` violated Single Responsibility — it contained the ViewModel, 5 data classes, and a preset enum.

| File | Before | After |
|------|--------|-------|
| `live/LiveDataViewModel.kt` | 554 lines — ViewModel + data classes + enum | ~420 lines — ViewModel only |
| `live/LiveDataUiState.kt` | (new) | Data classes: `PidDisplayState`, `LoggedDataPoint`, `LogSession`, `ChartPoint`, `LiveDataUiState` |
| `live/PidPreset.kt` | (new) | `PidPreset` enum with 7 presets |

Build verified: `BUILD SUCCESSFUL`

---

## Proposed Refactoring (Phase 2 — UI Component Extraction)

### Split `AiChatScreen.kt` (1,246 → ~200 lines + components)

Create package `ui/aichat/components/`:

| New File | Content |
|----------|---------|
| `ChatMessageBubble.kt` | Message bubble composables (user/assistant) |
| `ChatInputBar.kt` | Text input field + send button |
| `QuickPromptChips.kt` | Preset prompt suggestion chips |
| `AiChatTopBar.kt` | Top app bar with model/settings |
| `AiChatScreen.kt` | Entry point only (~50 lines) |

### Split `DiagnosticHudScreen.kt` (1,053 → ~150 lines + components)

Create package `ui/diagnostic/components/`:

| New File | Content |
|----------|---------|
| `TestListContent.kt` | Grid of available diagnostic tests |
| `TestDetailContent.kt` | Test description + step list |
| `RunningContent.kt` | Live HUD during test execution |
| `ResultsContent.kt` | Pass/Warning/Fail results display |
| `DiagnosticHudScreen.kt` | Entry point + Scaffold (~60 lines) |

### Split `LiveScreen.kt` (980 → ~100 lines + components)

Create package `ui/live/components/`:

| New File | Content |
|----------|---------|
| `LiveTopBar.kt` | Connection status + source badge |
| `LiveControlBar.kt` | Display mode chips + sort dropdown |
| `CategoryTabRow.kt` | Category filter tabs + DTC banner |
| `NumericListPane.kt` | PID card list + derived metrics section |
| `GaugeGridPane.kt` | Arc gauge grid |
| `GraphPane.kt` | Line graph pane |
| `TilesPane.kt` | Combined sensor tiles |
| `LiveBottomBar.kt` | Poll controls + preset chips + logging |
| `LiveScreen.kt` | Entry point + mode router (~50 lines) |

**Visibility**: Change `private fun` → `internal fun` for pane entry composables; keep inner helpers `private` within each file.

---

## Proposed Refactoring (Phase 3 — Diagnostic System Consolidation)

**Goal**: Eliminate the `diagnostic/` legacy package (5 files) by migrating `DiagnosticHudScreen` to use `expertdiag/` infrastructure.

**Risk**: Medium — requires aligning `DiagnosticTest` / `DiagnosticUiState` models with `expertdiag` equivalents.

**Steps**:
1. Audit overlap between `diagnostic/model/` and `expertdiag/model/`
2. Map legacy `DiagnosticTest` → `expertdiag` `AutomaticTest` equivalents
3. Migrate `DiagnosticViewModel` → `ExpertDiagViewModel`
4. Update route `diagnostic_hud` to render `ExpertDiagnosticScreen`
5. Delete `diagnostic/` package

---

## Proposed Refactoring (Phase 4 — LiveScreen Color Tokens)

**File**: `ui/LiveScreen.kt`
**Issue**: Raw hex color literals; theme already defines semantic tokens.

Replace:
```kotlin
Color(0xFF4CAF50) → GreenSuccess
Color(0xFFFF9800) → AmberSecondary
Color(0xFFF44336) → RedError
Color(0xFFE65100) → (define as OrangeWarning in theme)
Color(0xFFB71C1C) → (define as RedCritical in theme)
Color(0xFFF57C00) → (define as OrangeAccent in theme)
Color(0xFFC62828) → (define as RedDark in theme)
Color(0xFF2196F3) → (define as BlueInfo in theme)
Color(0xFF9C27B0) → (define as PurpleAccent in theme)
Color(0xFF9E9E9E) → TextTertiary
Color(0xFFFFC107) → (define as GoldStar in theme)
Color(0xFFFFF3E0) → AmberContainer
Color(0xFFFFEBEE) → RedContainer
```

---

## Proposed Refactoring (Phase 5 — Empty Module Cleanup)

**Modules**: `feature-live`, `feature-logger`, `feature-diagnostics`, `feature-ecu-profile`, `data-schema`

**Option A (Remove)**: Delete if no roadmap timeline. Reduces build overhead.
**Option B (Document)**: Add `README.md` to each explaining planned scope and target milestone.

Recommended: Option B — preserves architectural intent for future contributors.

---

## Proposed Improvement — `sortedFilteredPids` Use Case

**Current**: `sortedFilteredPids()` is a public method on `LiveDataViewModel`.
**Issue**: ViewModels should not contain pure data transformation logic callable from composables.
**Fix**: Extract to `live/SortAndFilterPidsUseCase.kt` with a single `invoke(state): List<PidDisplayState>` function.

---

## Risk Assessment

| Change | Risk | Reversibility |
|--------|------|--------------|
| Data class extraction (done) | None — same package | Instant |
| UI file splits | Low — visibility change only | Easy |
| Diagnostic consolidation | Medium — model mapping required | Moderate |
| Color token replacement | None | Instant |
| Empty module removal | None | Git history |
| sortedFilteredPids extraction | Low | Easy |
