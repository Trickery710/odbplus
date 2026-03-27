# Stability Improvements

Generated: 2026-03-27

---

## Existing Stability Infrastructure

The codebase already has strong stability measures in place:

- `ProtocolFallback` with ordered retry (KWP2000 fast → ISO 9141-2)
- `HealthMonitor` keepalive and ECU recovery
- `IsoTpAssembler` for multi-frame reassembly
- `VinCachePolicy` with exponential backoff (base=60s, exponent cap=20)
- `VinDecodeCoordinator` with 5s debounce and in-flight dedup
- KSP-generated Room access with compile-time query validation
- Hilt DI with compile-time graph validation
- `try/catch` in `resolveAndDiscoverPids()` with fallback to full discovery

---

## S1 — `resolveAndDiscoverPids`: Discovery Result Race Condition

**File**: `live/LiveDataViewModel.kt:293`
```kotlin
obdService.supportedPids.value?.let { codes ->
    if (codes.isNotEmpty()) {
        pidCacheRepository.saveDiscovery(resolvedProfileId, codes)
    }
}
```
**Issue**: Discovery is triggered by `obdService.runPidDiscovery()` (coroutine), then immediately reads `supportedPids.value`. The flow may not have updated yet — `value` reflects the *previous* state.

**Fix**: Collect the next non-null emission after discovery completes rather than reading `.value`:
```kotlin
obdService.runPidDiscovery()
val codes = obdService.supportedPids.filterNotNull().first()
if (codes.isNotEmpty() && resolvedProfileId >= 0) {
    pidCacheRepository.saveDiscovery(resolvedProfileId, codes)
}
```

---

## S2 — `AdapterFingerprinter`: Missing Timeout on Individual AT Commands

**File**: `core-protocol/adapter/AdapterFingerprinter.kt`
**Issue**: If an individual AT probe command (e.g., `ATCAF`) hangs rather than returning "OK" or "?", the fingerprinter blocks indefinitely at the transport `readLine()` level until the outer session timeout fires.

**Improvement**: Each probe command should have its own `withTimeout(2_000)` guard, failing fast to the next probe rather than consuming the whole session timeout budget.

```kotlin
val response = withTimeout(2_000L) {
    transport.sendCommand("ATCAF0")
}
```

---

## S3 — `ObdParser.hexStringToBytes`: No Bounds Check on Malformed Tokens

**File**: `core-protocol/ObdParser.kt`
**Issue**: `hexStringToBytes()` chunks an unspaced string into 2-char pairs using indexing. If the string has an odd length due to a corrupted response, the last character is dropped silently.

**Improvement**: Add an assertion/log for odd-length tokens:
```kotlin
if (hex.length % 2 != 0) {
    Timber.w("hexStringToBytes: odd-length hex string '%s' — last nibble dropped", hex)
}
```

---

## S4 — `VinDecoderRepository`: Network Timeout Not Propagated to UI

**File**: `vin/repository/VinDecoderRepository.kt`
**Issue**: `NhtsaVinDecoderService` uses Ktor with a default (30s?) socket timeout. If NHTSA is slow, `VinDecodeCoordinator` awaits the full timeout before resolving. The UI shows no progress indication during this wait.

**Improvement**: Set explicit Ktor timeouts:
```kotlin
HttpTimeout {
    connectTimeoutMillis = 5_000
    requestTimeoutMillis = 10_000
    socketTimeoutMillis = 10_000
}
```
Already defined in `VinModule` via `@VinHttpClient`. Verify the `connectTimeoutMillis` is set, not just `requestTimeoutMillis`.

---

## S5 — `PollingManager`: Unhandled Exception in Poll Coroutine

**File**: `live/PollingManager.kt`
**Issue**: If `obdService.sendCommand()` throws an unexpected exception (not `ObdException`), it propagates up through the polling coroutine and cancels the entire polling job silently.

**Improvement**: Wrap the poll cycle body in `try/catch(Exception)` and emit the error to an error state flow rather than crashing the coroutine:
```kotlin
try {
    val result = obdService.sendCommand(pid)
    // ...
} catch (e: CancellationException) {
    throw e  // always rethrow cancellation
} catch (e: Exception) {
    Timber.w(e, "Poll error for %s", pid)
    errorFlow.emit(pid to e.message)
}
```

---

## S6 — `GoogleAuthManager`: Incomplete Implementation

**File**: `app/GoogleAuthManager.kt` (or similar)
**Issue**: Contains a `TODO` comment indicating incomplete auth flow.
**Risk**: If the auth path is reachable, users may encounter an uncaught exception or silent no-op.
**Fix**: Either complete the implementation or throw `UnsupportedOperationException("Not yet implemented")` with a clear message, and guard all call sites.

---

## S7 — `LogSession.id` Collision Risk

**File**: `live/LiveDataUiState.kt`
```kotlin
val id: String = System.currentTimeMillis().toString()
```
**Issue**: If two `LogSession` objects are created within the same millisecond (e.g., during rapid start/stop), they get the same ID.

**Improvement**: Use `UUID.randomUUID().toString()` or combine timestamp + random suffix:
```kotlin
val id: String = "${System.currentTimeMillis()}-${(1000..9999).random()}"
```

---

## S8 — Room Migration Safety

**File**: `data/db/AppDatabase.kt`
**Status**: Migrations `MIGRATION_1_2` and `MIGRATION_2_3` are implemented.
**Risk**: If a future migration is missing and `fallbackToDestructiveMigration()` is enabled, all user data (sensor logs, vehicle profiles, VIN cache) is silently wiped.

**Improvement**: Add `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()` with an explicit `onDestructiveMigration` listener that logs an error to Timber:
```kotlin
.addCallback(object : RoomDatabase.Callback() {
    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        Timber.e("DESTRUCTIVE MIGRATION — all user data wiped")
    }
})
```

---

## Summary Table

| Issue | Severity | Effort | Priority |
|-------|----------|--------|----------|
| Discovery result race condition (S1) | Medium | Low | Fix now |
| Fingerprinter AT command timeout (S2) | Low | Low | Fix next |
| `hexStringToBytes` odd-length log (S3) | Low | Trivial | Fix next |
| NHTSA network timeout check (S4) | Low | Trivial | Verify |
| PollingManager unhandled exception (S5) | Medium | Low | Fix now |
| GoogleAuthManager TODO (S6) | Medium | Medium | Track |
| LogSession ID collision (S7) | Low | Trivial | Fix next |
| Room destructive migration callback (S8) | Low | Trivial | Fix next |
