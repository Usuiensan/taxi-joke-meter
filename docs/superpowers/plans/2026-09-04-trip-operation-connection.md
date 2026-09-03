# Trip Operation Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the existing Meter Surface operations to the Room v9 Trip, TripPlace, TripCharge, RestInterval, and DrivingSample records.

**Architecture:** Keep `MeterStateMachine` as the transition authority and `MeterController` as the side-effect coordinator. Extend `DutyDao`/`DutyRecordRepository` for immediate record writes, keep charge settings in a small SharedPreferences store, and let `LocationMeasurementService` apply the current meter state to samples and REST distance.

**Tech Stack:** Kotlin, Android Compose, Room 2.x, SharedPreferences, Android Geocoder, Kotlin coroutines, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-04-trip-operation-connection-design.md`

## Global Constraints

- Preserve EMPTY + EMPTY_MENU, PAYMENT→OCCUPIED same Trip, TOTAL→OCCUPIED new Trip, VOIDED numbering, FareRule snapshots, actual/chargeable distance separation, ChargeProgress, SafetyEvent, AudioCue, and existing Meter Surface design.
- Do not add PDF/PNG/TXT report renderers, external Places APIs, a second RoadMode enum, GPS interpolation across invalid samples, or a new large persistence subsystem.
- Preserve all existing uncommitted DB, log, image, GPX, and audio files; never reset, clean, stash, or push.
- Every production behavior change starts with a focused failing test; every task ends with a Japanese commit after its focused checks pass.

---

### Task 1: Expand state model and durable charge settings

**Files:**
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/meter/MeterStateMachine.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/meter/MeterRuntimeStore.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/data/fare/TripChargeSettingsStore.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/ui/shell/AppShell.kt`
- Test: `app/src/test/java/jp/usuiensan/taxijokemeter/domain/meter/MeterStateMachineTest.kt`
- Test: `app/src/androidTest/java/jp/usuiensan/taxijokemeter/data/fare/TripChargeSettingsStoreTest.kt`

**Interfaces:**
- `MeterState` gains `REST` and `PICKUP`; `MeterAction` gains direct `PICKUP`, `REST`, and `REST_RELEASE` semantics while retaining existing action names used by the UI.
- `MeterRuntimeSnapshot` gains `pendingReservation`, `passengerCount`, `waypointCount`, `pickupFeeApplied`, `restIntervalId`, and `restDistanceMeters`.
- `TripChargeSettingsStore.load(): TripChargeSettings` and `save(settings: TripChargeSettings)` persist `pickupFeeYen` and `reservationFeeYen` in one SharedPreferences file with default 0.

- [ ] **Step 1: Write failing state and settings tests**

```kotlin
@Test fun emptyCanEnterPickupAndRestCanReleaseToEmpty() {
    val machine = MeterStateMachine()
    assertEquals(MeterState.PICKUP, machine.dispatch(MeterAction.PICKUP).meterState)
    assertEquals(MeterState.EMPTY, MeterStateMachine(MeterRuntimeState(meterState = MeterState.REST))
        .dispatch(MeterAction.REST_RELEASE).meterState)
}
```

- [ ] **Step 2: Run `.\gradlew.bat :app:testDebugUnitTest --tests '*MeterStateMachineTest'` and confirm the new test fails because the states/actions do not exist.**
- [ ] **Step 3: Add only the new enum values, explicit REST/PICKUP transitions, EMPTY_MENU invariants, and the two-key SharedPreferences store.**
- [ ] **Step 4: Run the focused unit and Android settings tests and confirm PASS.**
- [ ] **Step 5: Commit `状態機械と迎車料金設定を追加`.**

### Task 2: Add atomic Trip/Place/Charge/Rest repository operations

**Files:**
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyDao.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyRecordRepository.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/model/Trip.kt`
- Test: `app/src/androidTest/java/jp/usuiensan/taxijokemeter/data/duty/DutyRecordRepositoryTest.kt`

**Interfaces:**
- Add DAO queries to find a TripPlace by `(tripId,type,sequence)`, find max waypoint sequence, list TripCharges by Trip, update place address fields, update passenger count, and update RestInterval distance/end fields.
- Repository methods: `addChargeIfAbsent`, `addWaypoint`, `upsertDropoff`, `savePickupPlace`, `updatePassengerCount`, `startRestInterval`, `updateRestDistance`, and `finishRestInterval`.
- Each method writes via Room and uses the existing `withTransaction` boundary where multiple rows must change together.

- [ ] **Step 1: Add instrumentation tests for one-time PICKUP_FEE, WAYPOINT sequences 1/2/3, DROPOFF upsert, passenger updates, and REST finish reasons.**
- [ ] **Step 2: Run `.\gradlew.bat :app:connectedDebugAndroidTest --tests '*DutyRecordRepositoryTest'` and confirm the new tests fail against missing methods.**
- [ ] **Step 3: Implement the smallest DAO queries and repository methods using existing entities and converters; keep TripPlace coordinate fields nullable only by skipping a place when no valid coordinate exists.**
- [ ] **Step 4: Re-run the focused instrumentation tests and the existing repository tests.**
- [ ] **Step 5: Commit `Trip地点料金と休憩記録の更新処理を追加`.**

### Task 3: Connect MeterController operations and GPS state

**Files:**
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/meter/MeterController.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/location/LocationMeasurementService.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/location/MeasurementRuntimeState.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/measurement/OccupancyState.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/measurement/DrivingSampleEntity.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/measurement/DrivingSampleRepository.kt`
- Test: `app/src/test/java/jp/usuiensan/taxijokemeter/data/meter/MeterControllerPaymentTest.kt`
- Test: `app/src/test/java/jp/usuiensan/taxijokemeter/domain/measurement/RestDistanceAccumulatorTest.kt`

**Interfaces:**
- Controller operations remain the UI entry points: `dispatch`, `setRoadMode`, plus `toggleReservation`, `changePassengerCount`, `addWaypoint`, and `togglePickupFee`.
- `LocationMeasurementService` receives meter state through the existing state-update intent and records PICKUP/REST/DEADHEAD sample states without starting fare accumulation outside OCCUPIED.
- Add a pure `RestDistanceAccumulator.add(previous: DrivingSample?, current: DrivingSample): Long` that returns 0 for invalid segments and otherwise uses the existing segment distance policy.

- [ ] **Step 1: Write failing tests for EMPTY→PICKUP, REST→OCCUPIED with one Trip, pending reservation transfer, PICKUP_FEE idempotence, passenger clamp 1..9, and invalid REST GPS segments.**
- [ ] **Step 2: Run the focused unit tests and confirm failure for the missing behavior.**
- [ ] **Step 3: Implement Controller orchestration: create/finish REST rows immediately, start one Trip, save pickup/waypoint/dropoff snapshots, add configured charges once, and update the measurement service state.**
- [ ] **Step 4: Implement REST valid-segment accumulation in the serialized sample path and persist distance/end transitions at 1000m; do not alter fare accumulation for non-OCCUPIED states.**
- [ ] **Step 5: Run focused unit tests plus existing meter/measurement tests and commit `乗務員操作をTripとGPS記録へ接続`.**

### Task 4: Replace the Meter Surface soft-key behavior

**Files:**
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/ui/meter/MeterScreen.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/ui/shell/AppShell.kt`
- Modify: `app/src/test/java/jp/usuiensan/taxijokemeter/ui/meter/MeterPresentationTest.kt`

**Interfaces:**
- `softKeySlots` returns the direct two-row EMPTY menu and state-specific OCCUPIED/REST/PICKUP/PAYMENT/TOTAL/DEADHEAD slots.
- `stateLabel` and `stateLabelColor` include REST and PICKUP.
- The seven right-side buttons remain in fixed order and derive `enabled` from the current state; disabled buttons remain visible and do not invoke the controller.

- [ ] **Step 1: Add presentation tests for the two EMPTY rows, OCCUPIED major actions, REST release, and right-key enabled matrix.**
- [ ] **Step 2: Run `.\gradlew.bat :app:testDebugUnitTest --tests '*MeterPresentationTest'` and confirm failure.**
- [ ] **Step 3: Implement direct Compose handlers with existing colors/layout primitives; add large passenger +/- dialog/panel and the settings fields without changing the fixed logical surface.**
- [ ] **Step 4: Run presentation tests and compile the debug app.**
- [ ] **Step 5: Commit `Meter操作メニューと料金設定画面を接続`.**

### Task 5: Add asynchronous reverse geocoding

**Files:**
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/location/ReverseGeocoder.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/data/location/AndroidReverseGeocoder.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/meter/MeterController.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyDao.kt`
- Test: `app/src/test/java/jp/usuiensan/taxijokemeter/domain/location/ReverseGeocoderTest.kt`

**Interfaces:**
- `ReverseGeocoder` returns nullable address fields for a coordinate and never controls Trip creation.
- Controller saves the coordinate row first, returns from the UI action, then launches geocoding on its existing IO scope and updates only available address fields. Exceptions leave the original row intact.

- [ ] **Step 1: Write a failing pure mapping test for canonical/report/landmark fields and a failure-result test.**
- [ ] **Step 2: Run the focused unit test and confirm the new mapping behavior fails.**
- [ ] **Step 3: Implement the Android Geocoder adapter and repository address update; avoid external Places APIs and avoid treating ambiguous feature text as a landmark.**
- [ ] **Step 4: Run the focused test and existing unit suite.**
- [ ] **Step 5: Commit `地点保存後の非同期逆ジオコードを追加`.**

### Task 6: Full verification and device boundary report

**Files:**
- No production file changes unless a test exposes a task-scoped defect.
- Verify: all changed files and existing test/build configuration.

- [ ] **Step 1: Run `.\gradlew.bat :app:testDebugUnitTest`.**
- [ ] **Step 2: Run `.\gradlew.bat :app:assembleDebug`.**
- [ ] **Step 3: Run `.\gradlew.bat :app:assembleDebugAndroidTest`.**
- [ ] **Step 4: Run `.\gradlew.bat :app:connectedDebugAndroidTest` with the explicit authorized device serial if available; record unauthorized/no-device separately.**
- [ ] **Step 5: Run `git diff --check` and inspect `git status --short`; do not stage unrelated user files.**
- [ ] **Step 6: If a Pixel/GPX run is available, verify the specified flows and DB rows; report device execution separately from build/test results.**
- [ ] **Step 7: Record all task commit SHAs and unresolved external/device limitations; do not push.**
