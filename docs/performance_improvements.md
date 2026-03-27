# Performance Improvements

## Already Optimised (Do Not Change)

### Polling Loop

`PollingManager` correctly uses `delay()` (non-blocking suspension) between
poll cycles and between individual PID commands. Batch rotation ensures PIDs
beyond `MAX_BATCH_SIZE = 15` are sampled fairly across cycles rather than
always dropping the tail.

### Transport I/O

`BaseTransport` runs the reader loop on `Dispatchers.IO` and uses a mutex
(`commandMutex`) to serialise command pairs. Write + read are atomic: no
thread can interleave a keepalive ping between a command's write and its
response read.

### Protocol Negotiation

The fast path (`0100` directly after `ATZ`) avoids the full ATSP0 scan.
On repeat connections with a warm ELM hint, negotiation completes in ~300 ms
vs. ~5 s for a cold full scan. A 30-second hard ceiling prevents the UI from
freezing on un-connectable vehicles.

### PID Discovery Cache

`SupportedPidCacheRepository` caches discovery results with confidence scoring.
After the first connection, subsequent live-data sessions skip bitmap discovery
entirely. The confidence threshold degrades on failures to trigger a re-scan.

---

## Active Performance Issues

### 1. `StateFlow.update` on Every Response

`PollingManager.applyResponse()` calls `_pidValues.update { ... }` per PID per
cycle. With 15 PIDs per cycle at 500 ms intervals, this is 30 updates/second.
Each `update` creates a new Map copy.

**Impact:** Minor GC pressure. On older devices or when chart rendering is active,
this can cause frame drops.

**Fix:** Batch all PID updates from a single cycle into one `update` call:

```kotlin
// In pollOnce(), accumulate all responses then flush once:
val cycleUpdates = mutableMapOf<ObdPid, PidDisplayState>()
for (pid in batch) {
    val response = obdService.query(pid)
    cycleUpdates[pid] = buildState(pid, pids, response)
}
_pidValues.update { current -> current + cycleUpdates }  // single update
```

### 2. Map Copy on Every `_pidValues.update`

The current implementation:
```kotlin
_pidValues.update { current ->
    current.toMutableMap().also { it[pid] = newState }
}
```
copies the entire map on every write. With 30–50 active PIDs this is
30–50 map copies per second.

**Fix:** Use `toMutableMap()` once per cycle (see item 1 above) and emit a
single copy per polling cycle rather than per PID.

### 3. Chart Buffer Allocation

The rolling 120-point chart buffer appends values and trims to size:
```kotlin
buffer.add(newPoint)
if (buffer.size > CHART_MAX_POINTS) buffer.removeAt(0)
```
`removeAt(0)` on an `ArrayList` shifts all elements left (O(n)).

**Fix:** Use `ArrayDeque` which has O(1) `removeFirst()`:
```kotlin
val buffer: ArrayDeque<ChartPoint> = ArrayDeque(CHART_MAX_POINTS)
buffer.addLast(newPoint)
if (buffer.size > CHART_MAX_POINTS) buffer.removeFirst()
```

### 4. `hexStringToBytes` Allocation in Hot Path

`ObdParser.hexStringToBytes()` allocates a list for split tokens, flatMaps
into another list, then maps to a ByteArray. This runs on every PID response.

For a 4-byte response like `"41 0C 1A F8"`, the chain is:
`split → filter → flatMap → mapNotNull → toByteArray` = 4 intermediate collections.

**Fix for hot-path PIDs:** Pre-parse common responses by byte offset in
`ObdPid.parse()` directly from the raw hex string without going through
`hexStringToBytes` for the simple single-line case. The current code is
correct and the overhead is small, but this could matter at high polling rates.

### 5. Compose Recomposition Scope

Large composable functions (LiveScreen, AiChatScreen) recompose their entire
subtree when any part of `uiState` changes. Extracting sub-composables (see
`refactor_plan.md`) naturally limits recomposition scope to the affected
sub-tree.

**Immediate fix:** Annotate extracted sub-composables with `@Stable` where
applicable, and pass only the specific slice of state each component needs
rather than the full `UiState`.

---

## Measurement Recommendations

Before and after any polling-related change, measure with:
```
adb shell dumpsys gfxinfo com.obdplus.app framestats
```

For memory: use Android Studio Profiler → Memory → Record allocations
during a 30-second live data session and look for repeated `Map` or
`ArrayList` alloc spikes at the polling interval.
