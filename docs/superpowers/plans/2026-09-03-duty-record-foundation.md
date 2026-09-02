# 乗務記録基盤 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 乗務セッション・営業・イベント・記録場所をRoom/SQLiteへ保存し、ドメインモデルとして再読込できる基盤を追加する。

**Architecture:** 純粋なKotlinドメインモデルをRoom Entityから分離する。Room adapterは3 Entity、TypeConverter、DAO、Repositoryに限定し、`RecordedPlace`は親テーブルへ埋め込む。`ReverseGeocoder`はインターフェースのみとする。

**Tech Stack:** Android, Kotlin, Room, JUnit4, AndroidX instrumentation test.

**Spec:** `docs/superpowers/specs/2026-09-03-duty-record-domain-design.md`

## Global Constraints

- 日時は `java.time.Instant`、距離はメートルの `Long`、金額は円の `Long`、人数は `Int` とする。
- 住所未解決でも緯度・経度等を保持できる。
- `FareCalculator` は変更せず、GPS/DBに依存させない。
- 国土地理院API、GPS取得、トンネル補完、PDF出力、UIは実装しない。

### Task 1: ドメインモデルと公開境界

**Files:**
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/model/DutySession.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/model/Trip.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/model/DutyEvent.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/model/RecordedPlace.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/location/ReverseGeocoder.kt`
- Create: `app/src/test/java/jp/usuiensan/taxijokemeter/domain/model/DutyRecordModelTest.kt`

**Interfaces:** `DutySession`, `Trip`, `DutyEvent`, `RecordedPlace`, `DutyEventType`, `PaymentMethod`, and `ReverseGeocoder` are public Kotlin types. Use data classes and immutable lists; nullable `RecordedPlace` address fields support unresolved addresses.

- [ ] Write tests for all event types, trip fields, and unresolved place round-trip equality.
- [ ] Run `./gradlew.bat testDebugUnitTest` and confirm the new test fails because types do not exist.
- [ ] Add the minimum model types and constructor validation for nonnegative count/distance/amounts.
- [ ] Run the same unit test and confirm it passes.

### Task 2: Room persistence adapter

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyEntities.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyConverters.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyDao.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyDatabase.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyRecordRepository.kt`
- Create: `app/src/androidTest/java/jp/usuiensan/taxijokemeter/data/duty/DutyRecordRepositoryTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:** `DutyRecordRepository.save(DutySession)` and `findById(String): DutySession?`. The DAO stores a session row plus child trip/event rows with a foreign key and reconstructs children ordered by timestamp.

- [ ] Add Room and AndroidX test dependencies and enable the test runner configuration required for an in-memory database.
- [ ] Write an instrumentation test that saves one session with two trips, all eight event types, and a place with null address fields, then reads it back and compares the domain values.
- [ ] Run `./gradlew.bat connectedDebugAndroidTest` and confirm the test fails before the adapter exists.
- [ ] Implement entities, converters, DAO, database, and repository mappings with no domain dependency on Room annotations.
- [ ] Run the instrumentation test and confirm it passes.

### Task 3: Full verification and delivery

**Files:** Existing implementation files only; no UI changes.

- [ ] Run `./gradlew.bat testDebugUnitTest connectedDebugAndroidTest assembleDebug`.
- [ ] Inspect `git diff --check`, status, and dependency graph for unrelated changes; verify `FareCalculator.kt` is unchanged.
- [ ] Commit implementation in Japanese with `乗務記録ドメインとRoom永続化を実装`.
- [ ] Push the current `main` branch to `origin` and report the commit hash and command results.

