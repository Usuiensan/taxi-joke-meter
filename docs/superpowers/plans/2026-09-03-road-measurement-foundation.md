# 道路モード・GPS走行計測基盤 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 3道路モードの操作履歴と、GPS正常時の走行サンプル・料金計測基盤・戻し忘れ判定を追加する。

**Architecture:** 道路モード遷移、戻し忘れ判定、GPSサンプルからの距離/低速時間振り分けを純粋Kotlinとして実装する。Android Location APIとUIは直接接続せず、DrivingSampleを境界モデルにして、既存のFareCalculatorと乗務履歴イベントへ渡せる形にする。DrivingSampleは将来の分析用にRoomへ保存可能にする。

**Tech Stack:** Android, Kotlin, Room, JUnit4, AndroidX instrumentation test.

**Spec:** `docs/superpowers/specs/2026-09-03-duty-record-domain-design.md` と本ユーザー要件。

## Global Constraints

- `GENERAL` の安全評価基準は60km/h、`HIGHWAY1` は80km/h、`HIGHWAY2` は100km/hとする。
- 短押しは `GENERAL→HIGHWAY1→HIGHWAY2→HIGHWAY1`、長押しは `HIGHWAY1/HIGHWAY2→GENERAL` とする。
- 法定速度、道路DB、標識認識、GPS補完、マップマッチング、逆ジオコーディング、採点、UI通知は実装しない。
- 不良GPSサンプルは距離・低速時間へ加算しない。
- `FareCalculator` の公開契約と既存の `SAFETY_ROAD_MODE_CHANGE` 履歴構造を維持する。
- 通知は初回のみとし、最終通知時刻を受け取れる判定構造を残す。

### Task 1: 道路モード操作と戻し忘れ判定

**Files:**
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/road/RoadModeController.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/road/HighwayModeReminderEvaluator.kt`
- Test: `app/src/test/java/jp/usuiensan/taxijokemeter/domain/road/RoadModeControllerTest.kt`
- Test: `app/src/test/java/jp/usuiensan/taxijokemeter/domain/road/HighwayModeReminderEvaluatorTest.kt`

**Interfaces:** `RoadModeController.transition(current: RoadMode, action: RoadModeAction): RoadModeTransition` returns the next mode and optional `SafetyRoadModeChange`. `HighwayModeReminderEvaluator.evaluate(currentMode, samples, lastNotifiedAt, now): Boolean` evaluates only valid samples and returns false for GENERAL or after the first notification.

- [ ] **Step 1: Write failing tests** for all short/long press transitions, no-op GENERAL long press, GENERAL no reminder, sustained low-speed HIGHWAY reminder, and one-time notification.
- [ ] **Step 2: Run** `./gradlew.bat testDebugUnitTest --tests '*RoadModeControllerTest' --tests '*HighwayModeReminderEvaluatorTest'`; confirm failure because the public types do not exist.
- [ ] **Step 3: Implement** the two pure Kotlin classes using the existing `RoadMode` and `SafetyRoadModeChange`.
- [ ] **Step 4: Run** the focused tests and confirm pass.
- [ ] **Step 5: Commit** with `道路モード遷移と戻し忘れ判定を実装`.

### Task 2: GPS走行サンプルと計測積算

**Files:**
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/measurement/DrivingSample.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/measurement/TripMeasurementAccumulator.kt`
- Test: `app/src/test/java/jp/usuiensan/taxijokemeter/domain/measurement/TripMeasurementAccumulatorTest.kt`

**Interfaces:** `DrivingSample` contains timestamp, latitude, longitude, accuracyMeters, speedMps, optional bearingDegrees, roadMode, occupancyState, and measurementQuality. `TripMeasurementAccumulator.add(previous, current)` returns the accumulated `chargeableDistanceMeters` and `lowSpeedSeconds`, using elapsed time and coordinate distance only for valid samples; the road mode exposes `safetyLimitKmh` as 60/80/100.

- [ ] **Step 1: Write failing tests** for fast distance accumulation, low-speed time accumulation without distance double count, invalid samples excluded, and all three safety limits.
- [ ] **Step 2: Run** the focused unit test and confirm failure.
- [ ] **Step 3: Implement** the minimum value types and accumulator; use haversine distance in the domain layer and do not add GPS/Android dependencies.
- [ ] **Step 4: Run** the focused test and confirm pass.
- [ ] **Step 5: Commit** with `GPS走行サンプルと料金計測積算を実装`.

### Task 3: DrivingSampleのRoom保存と全体検証

**Files:**
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyDatabase.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyDao.kt`
- Create or modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/measurement/DrivingSampleEntity.kt`
- Create or modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/measurement/DrivingSampleDao.kt`
- Create or modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/measurement/DrivingSampleRepository.kt`
- Test: `app/src/androidTest/java/jp/usuiensan/taxijokemeter/data/measurement/DrivingSampleRepositoryTest.kt`

**Interfaces:** Repository persistence accepts and returns `DrivingSample`; Room stores the raw sample fields, quality, road mode, occupancy state, and timestamp without changing domain types. Save cadence remains a caller concern; no fixed UI/location cadence is introduced.

- [ ] **Step 1: Write failing in-memory Room test** for saving and loading valid and unavailable samples.
- [ ] **Step 2: Run** `./gradlew.bat connectedDebugAndroidTest --tests '*DrivingSampleRepositoryTest'`; if no device exists, record that limitation and continue with compilation/unit verification.
- [ ] **Step 3: Implement** the entity, DAO, database version update, converters if required, and repository mapping.
- [ ] **Step 4: Run** the instrumentation test when a device is available.
- [ ] **Step 5: Run** `./gradlew.bat testDebugUnitTest`, `./gradlew.bat assembleDebug`, and `./gradlew.bat assembleDebugAndroidTest`.
- [ ] **Step 6: Inspect** `git diff --check`, confirm `FareCalculator.kt` is unchanged, then commit with `GPS走行サンプルのRoom保存を実装`.
- [ ] **Step 7: Push** the verified current branch to `origin/main` only if the checkout is `main` and the push is fast-forward safe.
