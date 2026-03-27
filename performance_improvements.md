# Performance Improvements

Generated: 2026-03-27

---

## Current Performance Profile

The app is generally well-optimized. The areas below have measurable improvement potential.

---

## P1 — `sortedFilteredPids()` Called on Every Recomposition

**File**: `ui/LiveScreen.kt:273`
```kotlin
val sorted = remember(state.availablePids, state.sortOrder, state.activeCategory, state.activeDtcFilter) {
    vm.sortedFilteredPids(state)
}
```
**Assessment**: `remember` keys are correct. However `sortedFilteredPids` is on the ViewModel (forcing a ViewModel reference into `remember`). This is already memoized — **no recomposition overhead**. Low priority.

**Improvement**: Expose a derived `StateFlow<List<PidDisplayState>>` from the ViewModel that pushes updates reactively, so the UI just `collectAsState()` without needing `remember` + a method call.

```kotlin
// In LiveDataViewModel:
val sortedFilteredPids: StateFlow<List<PidDisplayState>> = combine(
    _uiState.map { it.availablePids },
    _uiState.map { it.sortOrder },
    _uiState.map { it.activeCategory },
    _uiState.map { it.activeDtcFilter }
) { pids, sort, cat, dtc -> /* sort/filter logic */ }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

---

## P2 — `ObdPid.entries` Iterated on Every `supportedPids` Update

**File**: `live/LiveDataViewModel.kt:207`
```kotlin
val allPids = withContext(Dispatchers.Default) {
    ObdPid.entries.map { pid ->
        PidDisplayState(pid, PidRegistry.get(pid), isSelected = pid in current.selectedPids)
    }
}
```
**Assessment**: Already dispatched to `Dispatchers.Default`. `ObdPid.entries` is 200+ items — each update creates 200+ `PidDisplayState` objects. The `pid in current.selectedPids` check is O(n) per item.

**Improvement**: Convert `selectedPids: List<ObdPid>` to `selectedPids: Set<ObdPid>` internally for O(1) lookup. `List` is used for ordered display, but the lookup during map is pure membership testing.

```kotlin
// Internal set for O(1) lookup:
val selectedSet = current.selectedPids.toHashSet()
ObdPid.entries.map { pid ->
    PidDisplayState(pid, PidRegistry.get(pid), isSelected = pid in selectedSet)
}
```
`toHashSet()` is a one-time O(n) cost, then each of 200 lookups is O(1) instead of O(n). Net: 200× faster membership testing.

---

## P3 — Chart Data Allocation Per Poll Cycle

**File**: `live/LiveDataViewModel.kt:130`
```kotlin
val chartUpdates = _uiState.value.chartData.toMutableMap()
for ((pid, v) in pidValues) {
    val existing = chartUpdates[pid] ?: emptyList()
    chartUpdates[pid] = (existing + ChartPoint(now, value)).takeLast(CHART_MAX_POINTS)
}
```
**Assessment**: `existing + ChartPoint(...)` creates a new list on every poll cycle for every polled PID. At 500ms intervals with 10 PIDs selected: ~20 list allocations/second.

**Improvement**: Use `ArrayDeque` (bounded size) instead of immutable `List` for chart data in the mutable map, converting to `List` only for state emission.

```kotlin
// Replace List<ChartPoint> with ArrayDeque<ChartPoint> in the working buffer,
// snapshot to List when updating _uiState.
```

---

## P4 — `buildCurrentValueMap` on Every Poll Cycle

**File**: `live/LiveDataViewModel.kt:495`
```kotlin
private fun buildCurrentValueMap(freshValues: Map<ObdPid, Double?>): Map<ObdPid, Double?> {
    val result = _uiState.value.pidValues
        .mapValues { (_, state) -> state.value }
        .toMutableMap()
    result.putAll(freshValues)
    return result
}
```
**Assessment**: This creates a full copy of `pidValues` (200+ entries) on every poll cycle solely to pass to `DerivedMetricCalculator`. If only 4–6 PIDs are selected, this is wasteful.

**Improvement**: Pass `freshValues` directly to `DerivedMetricCalculator` alongside the last known values map, avoiding the full map copy:
```kotlin
val derived = DerivedMetricCalculator.calculate(freshValues + lastKnownValues)
```
where `lastKnownValues` is maintained as a separate `MutableMap<ObdPid, Double?>` field updated in place.

---

## P5 — `ArcGauge` Allocates Path Objects on Every Draw

**File**: `ui/LiveScreen.kt:541`
```kotlin
Box(modifier = modifier.drawWithCache {
    onDrawBehind { /* draws arcs using drawArc */ }
})
```
**Assessment**: `drawWithCache` is used correctly — the `onDrawBehind` lambda caches the draw commands and only re-executes when the cache key changes. No allocation issue here. **No action needed.**

---

## P6 — `LazyColumn` Key Stability in `NumericListPane`

**File**: `ui/LiveScreen.kt:289`
```kotlin
items(sorted, key = { it.pid.code }) { pidState ->
    PidNumericCard(pidState, vm)
}
```
**Assessment**: Keys are stable `String` values from `ObdPid.code`. Correct. **No action needed.**

---

## Summary Table

| Issue | Impact | Effort | Priority |
|-------|--------|--------|----------|
| `selectedPids` O(n) lookup → HashSet | Low-Medium | Low | Implement |
| Chart data list allocation → ArrayDeque | Low | Medium | Consider |
| `buildCurrentValueMap` full copy | Low | Low | Implement |
| `sortedFilteredPids` → derived StateFlow | Medium | Medium | Consider |
| `drawWithCache` (no issue) | — | — | None |
| `LazyColumn` key stability (no issue) | — | — | None |
