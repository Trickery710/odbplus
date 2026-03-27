# Stability Improvements

## Already Hardened (Do Not Change)

### Transport Layer
- `commandMutex` prevents response interleaving between concurrent callers.
- `close()` closes streams before `cancelAndJoin()` to unblock blocking `read()`.
- `SocketTimeoutException` caught in reader loop and treated as "no data this tick"
  (loop continues, allows cooperative cancellation).
- EOF from remote (`read() == -1`) correctly marks `isConnected = false`.

### Protocol Session
- `withTimeoutOrNull(30_000)` hard ceiling on entire protocol negotiation.
- `keepaliveJob?.cancelAndJoin()` + `reconnectJob?.cancelAndJoin()` in `disconnect()`
  ensures no jobs write to a null transport.
- `CancellationException` is always re-thrown (never swallowed).
- `MAX_CONSECUTIVE_FAILURES = 5` triggers error recovery before the bus times out.
- `MAX_RECONNECT_ATTEMPTS = 10` prevents infinite reconnect loop after adapter
  is powered off.
- Exponential backoff in reconnect: 2s, 4s, 8s, 16s, 30s (capped).

### ObdParser
- `parseValue()` checks `dataBytes.size < pid.expectedBytes` before parsing.
- `hexStringToBytes()` silently skips non-hex tokens (handles mixed ISO header
  text like "BUS INIT: OK" alongside valid hex data).
- `isOnlyNegativeResponse()` guards against `7F xx yy` frames masquerading as
  valid responses.
- `extractMatchingPidResponse()` truncates to exactly `expectedBytes` to drop
  trailing negative frames from secondary ECUs.
- BUS INIT error detection happens on the raw response before `cleanResponse()`
  strips the prefix.

### Reconnect Loop
- `ReconnectManager` prototype (in `ODBPlus_Reconnect_Upgrade/`) has been
  superseded by `AdapterSession.startReconnectLoop()` which adds state checks,
  `cancelAndJoin`, and capped exponential backoff.

---

## Remaining Stability Risks

### 1. ViewModel Error Swallowing

All ViewModels catch `Exception` and set `error = e.message`. `e.message` can
be `null` for some exception types (e.g., `NullPointerException`), resulting
in the UI showing no error text.

**Fix:**
```kotlin
_uiState.update { it.copy(error = e.message ?: "An unexpected error occurred") }
```

**Also:** Errors caught in `collect {}` blocks are not propagated to the UI
in some ViewModels. The pattern:
```kotlin
repo.data.collect { value ->
    try { _uiState.update { ... } }
    catch (e: Exception) { /* swallowed */ }
}
```
should surface the error:
```kotlin
repo.data.catch { e ->
    _uiState.update { it.copy(error = e.message ?: "Data error") }
}.collect { value -> _uiState.update { ... } }
```

### 2. No Timeout on PID Discovery

`ObdService.runPidDiscovery()` queries support bitmaps (0x00, 0x20, 0x40,
0x60, 0x80, 0xA0) sequentially. If the ECU stops responding mid-discovery,
this can hang until individual command timeouts accumulate (6 PIDs × 3s = 18s
minimum, plus retries).

**Fix:** Wrap in `withTimeoutOrNull`:
```kotlin
val result = withTimeoutOrNull(60_000L) { runPidDiscovery() }
if (result == null) {
    Timber.w("PID discovery timed out — using cached or default PID set")
    // Fall back to cached PIDs
}
```

### 3. VinDecodeCoordinator Silent Failures

`VinDecodeCoordinator` runs as a background sidecar. If NHTSA returns a
network error or the Room write fails, the error is logged but not surfaced
to the UI. The vehicle VIN is saved without decoded identity data.

This is acceptable behaviour (VIN decode is best-effort) but should be
indicated in the UI. `ConnectViewModel` could observe `coordinator.state`
and show a snackbar for decode failures.

### 4. Bluetooth Socket Reconnection

On Android 12+ (API 31+), Bluetooth permissions are runtime-granted.
If the user revokes `BLUETOOTH_CONNECT` mid-session, the RFCOMM socket
throws `SecurityException` rather than `IOException`. The current
`BluetoothTransport` only handles `IOException`.

**Fix:**
```kotlin
override suspend fun createConnection(host: String, port: Int): ConnectionStreams {
    try {
        // ... existing code ...
    } catch (e: SecurityException) {
        throw TransportException.PermissionDenied("Bluetooth permission revoked: ${e.message}")
    }
}
```

`TransportException.PermissionDenied` would need to be added to the
exception hierarchy.

### 5. Room Migration Safety

MIGRATION_2_3 adds four new tables. If the migration SQL has a typo or
uses a column name that doesn't match the entity, Room will crash at app
startup (not at migration time). The fallback is `fallbackToDestructiveMigration()`
which is NOT set — so a migration failure will crash rather than clearing data.

**Recommendation:** Add an instrumented migration test:
```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OdbDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun migrate2to3() {
        helper.createDatabase(TEST_DB, 2)
        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
    }
}
```

---

## Hardening Checklist

| Area | Status | Action |
|------|--------|--------|
| Transport mutex | ✅ Done | — |
| Protocol timeout | ✅ Done | — |
| Job lifecycle | ✅ Done | — |
| Parser bounds check | ✅ Done | — |
| BT socket closure | ✅ Done | — |
| SecurityException for BT | ⚠️ Missing | Add catch in BluetoothTransport |
| ViewModel null message | ⚠️ Minor | Add fallback string |
| PID discovery timeout | ⚠️ Missing | Wrap in withTimeoutOrNull |
| VIN decode failure UX | ⚠️ Silent | Surface in ConnectViewModel |
| Room migration test | ⚠️ Missing | Add instrumented test |
