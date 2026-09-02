# 単調時計対応測位・Foreground Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android Location の壁時計表示と単調時計計測を分離し、速度欠損を安全に扱いながら、Room保存とForeground Serviceによる連続測位を追加する。

**Architecture:** `LocationSampleMapper` は `Location` をdomainの `DrivingSample` に変換する境界に限定する。`TripMeasurementAccumulator` は単調時計を優先し、速度が欠損した区間だけVALID位置間の平均速度を使い、算出不能区間を除外する。Android側は `LocationMeasurementService` が `LocationManager` を購読し、repositoryとaccumulatorへ流し、controllerがDutySession開始/終了とサービスを接続する。

**Tech Stack:** Android, Kotlin, Room 2.7.2, LocationManager, JUnit4, AndroidX instrumentation test.

**Spec:** `docs/superpowers/specs/2026-09-03-duty-record-domain-design.md` とユーザー要件。

## Global Constraints

- `DrivingSample.timestamp` は壁時計の `Instant`、`elapsedRealtimeNanos` は同一起動中の順序・経過時間用のnullable `Long` とする。
- `Location.hasSpeed() == false` は速度不明であり停止ではない。reported `0.0` は停止として扱う。
- 積算は単調時計を優先し、速度は REPORTED → VALID位置間からのDERIVED → UNAVAILABLEの順で扱う。
- 不明速度かつ信頼できる単調時間のない区間は積算しない。GPS位置飛び補正、道路自動判定、トンネル補完、UI/PDFは実装しない。
- Android APIはdata/domainへ漏らさず、Google Play Servicesと`ACCESS_BACKGROUND_LOCATION`は追加しない。
- Meter Surfaceのデザインは変更せず、計測状態はServiceログで確認可能にする。

### Task 1: Domain時刻・速度と積算

**Files:**
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/measurement/DrivingSample.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/domain/measurement/TripMeasurementAccumulator.kt`
- Modify: `app/src/test/java/jp/usuiensan/taxijokemeter/domain/measurement/TripMeasurementAccumulatorTest.kt`

**Interfaces:** `DrivingSample.speedMps: Double?`, `elapsedRealtimeNanos: Long?`, `speedSource: SpeedSource`; `TripMeasurementAccumulator.add(previous, current): MeasurementAccumulation`.

- [ ] 速度0、速度欠損、壁時計逆行、単調時計500ms、fallback不能を示す失敗テストを書く。
- [ ] `testDebugUnitTest --tests '*TripMeasurementAccumulatorTest'` で新テストが仕様未実装理由で失敗することを確認する。
- [ ] nullable速度・source・単調時間を追加し、積算の時間選択と速度fallbackを最小実装する。
- [ ] focused unit testを実行して通過を確認する。
- [ ] `ドメイン計測を単調時計と速度欠損に対応` としてコミットする。

### Task 2: Android mapperとRoom v6 migration

**Files:**
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/location/LocationSampleMapper.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/measurement/DrivingSampleEntity.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/measurement/DrivingSampleRepository.kt`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/data/duty/DutyDatabase.kt`
- Modify: `app/src/androidTest/java/jp/usuiensan/taxijokemeter/data/location/LocationSampleMapperTest.kt`
- Modify: `app/src/androidTest/java/jp/usuiensan/taxijokemeter/data/measurement/DrivingSampleRepositoryTest.kt`

**Interfaces:** mapperは `Location` の `time`, `elapsedRealtimeNanos`, `hasSpeed()` をdomainへ写し、既存の品質判定とstate変換を保つ。Roomは速度nullable、単調時間、sourceを保存・復元する。

- [ ] mapperの `hasSpeed=true + 0` と `hasSpeed=false`、単調時間の失敗テストを書く。
- [ ] Room v5からv6のmigrationで既存行を破壊せず、旧速度は推測せずUNAVAILABLE/nullにするテストを追加する。
- [ ] focused Android testsを実行して失敗を確認する。
- [ ] mapper/entity/repository/migrationを実装し、DB versionを6へ更新する。
- [ ] focused Android testsを実行して通過を確認する。
- [ ] `Location mapperとRoom v6 migrationを実装` としてコミットする。

### Task 3: LocationManager連続取得・Foreground Service・Duty接続

**Files:**
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/data/location/LocationMeasurementService.kt`
- Create: `app/src/main/java/jp/usuiensan/taxijokemeter/data/location/DutyMeasurementController.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/jp/usuiensan/taxijokemeter/MainActivity.kt` only if permission/start wiring is required without altering Meter Surface.

**Interfaces:** `DutyMeasurementController.start(sessionId, roadMode, occupancyState)` starts a user-initiated foreground service; `.stop()` stops it. Service registers `LocationManager.GPS_PROVIDER`, maps each callback, saves it, updates the accumulator, and logs GPS state/速度/accuracy/RoadMode/累積課金距離/累積低速時間/保存件数.

- [ ] manifestの権限/typeとcontrollerのintent契約を検証するテストまたは静的確認を先に追加する。
- [ ] `LocationManager` listener、permission guard、`startForeground` notification channel、callback lifecycleを実装する。
- [ ] start/stopの順序をDutySession controllerから利用できる形にする。
- [ ] `assembleDebug` を実行してService/Manifestを検証する。
- [ ] `Foreground Serviceと連続測位層を実装` としてコミットする。

### Task 4: 全体検証とpush

- [ ] `testDebugUnitTest` を実行する。
- [ ] `assembleDebug` と `assembleDebugAndroidTest` を実行する。
- [ ] 接続端末を確認し、authorizedなら `connectedDebugAndroidTest`、unauthorized/未接続ならその状態を記録する。
- [ ] `git diff --check`、status、diffを確認し、要求外のMeter Surface変更がないことを確認する。
- [ ] 各コミットと検証結果を確認し、fast-forward可能な場合に限り`origin/main`へpushする。

