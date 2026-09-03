# 日報・料金明細の記録基盤 第1実装 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 自動計測からTrip完了・取消までを、後から日報と料金明細を再生成できるRoom記録へ接続する。

**Architecture:** 既存のDutyMeasurementControllerをsessionIdの所有者とし、MeterControllerは同じIDを受け取って使う。既存のDutyRecordRepository/DutyDaoを拡張し、Trip採番・状態更新・snapshotをtransaction内で行う。既存のFareMeasurementSession/TripMeasurementAccumulatorを再利用し、actual distanceだけを最小追加する。

**Tech Stack:** Android, Kotlin, Room, JUnit4, AndroidX instrumentation test.

**Spec:** `docs/superpowers/specs/2026-09-03-duty-record-domain-design.md` および今回承認済み要件。

## Global Constraints

- `FareCalculator`の式、EMPTY + EMPTY_MENU、PAYMENT→OCCUPIED同一Trip、TOTAL→OCCUPIED新規Tripを変更しない。
- Room v8→v9は既存migrationを維持し、`fallbackToDestructiveMigration`を追加しない。
- 既存Trip列、未追跡DB・ログ・画像・WAV・GPX、Meter Surface UIは変更・削除しない。
- 新規Tripは`serviceDate`をAsia/TokyoのpickupAtから確定し、同日MAX+1をtransaction内で採番する。
- 実装前に各テストをREDにし、実装後に対象テストと全体検証を実行する。
- pushは行わない。

### Task 1: sessionId所有の統一

**Files:** `DutyMeasurementController.kt`, `MeterController.kt`, 必要時のみ`MeterRuntimeStore.kt`、新規session controller test。

- [ ] 自動開始でMeterControllerがsessionIdを取得し、finish/eventがnull skipしない失敗テストを書く。
- [ ] 同じcontrollerのensure複数回でsession作成が1件になる失敗テストを書く。
- [ ] Activity再生成相当でcontrollerを再作成してもService/runtime側の既存sessionIdを復元・再利用し、別sessionを作らない失敗テストを書く。
- [ ] REDを確認後、`ensureMeasurementRunning(fareRule): String?`で既存IDを返し、未開始時は1つのIDを確定してDB保存とACTION_STARTへ渡す。
- [ ] MeterControllerのsessionId二重所有を除去し、対象テストと既存MeterControllerテストをGREENにする。
- [ ] `計測セッションIDの所有を統一` でコミットする。

### Task 2: Room v9 schema foundation

**Files:** `DutyEntities.kt`, `DutyDao.kt`, `DutyDatabase.kt`、既存migration test。

- [ ] v8 DBの旧Trip/session/sample/safety行がv9後も保持され、新列・新テーブル・FK/indexが存在する失敗テストを書く。
- [ ] REDを確認後、`MIGRATION_8_9`で旧列を残し、actual/chargeableはoperating、meteredはfare、totalはfare+additionalへ移す。serviceDate/tripNumber/snapshotはNULL許可にする。
- [ ] `TripPlaceEntity`、`TripChargeEntity`、`RestIntervalEntity`と必要なDAOを追加し、`(serviceDate, tripNumber)` uniqueをlegacy NULLと衝突しない形で定義する。
- [ ] v9新規DBと8→9 migrationをGREENにする。
- [ ] `日報基盤用のRoom v9スキーマを追加` でコミットする。

### Task 3: Trip採番・snapshot・距離分離

**Files:** `Trip.kt`, `TripStatus.kt`, `TripMeasurementAccumulator.kt`, `FareMeasurementSession.kt`, `DutyRecordRepository.kt`, `DutyDao.kt`、関連unit/instrumentation tests。

- [ ] 低速・高速segmentでactual/chargeable/lowSpeedSecondsを検証する失敗テストを書く。
- [ ] 同日1,2,3、翌日1、cross-midnight、passengerCount=1、snapshot固定を検証する失敗テストを書く。
- [ ] REDを確認後、accumulator結果へactualDistanceMetersを追加し、既存の課金式とPAYMENTのdistance-only挙動を維持する。
- [ ] Repositoryのtransaction採番、ACTIVE INSERT、更新・完了・取消、Trip mappingを最小実装する。
- [ ] 対象テストをGREENにし、`Trip採番と運賃設定スナップショットを保存`でコミットする。

### Task 4: Meter flow persistence connection

**Files:** `MeterController.kt`, actual値保持が必要な場合のみ`MeterRuntimeStore.kt`、既存フローtest。

- [ ] 新規Trip開始、PAYMENT復帰、TOTAL完了、空車取消の状態と永続結果を検証する失敗テストを書く。
- [ ] REDを確認後、EMPTY/TOTAL→OCCUPIEDは最新FareRuleを一度だけsnapshotしてACTIVE Tripを即時保存し、PAYMENT→OCCUPIEDは同じTripを継続する。
- [ ] TOTALはCOMPLETED、OCCUPIED→EMPTYはVOIDEDとして、actual/chargeable/lowSpeed/fare/status/revenueIncludedを更新する。
- [ ] 既存のEMPTY invariant、音声、安全、ChargeProgress、RoadModeを維持してGREENにする。
- [ ] `実車完了とキャンセルを永続Tripへ接続` でコミットする。

### Task 5: Final verification

- [ ] `./gradlew.bat testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`を実行する。
- [ ] `./gradlew.bat connectedDebugAndroidTest`を実行し、端末認証不可ならコード失敗と分離する。
- [ ] `git diff --check`と変更範囲を確認し、FareCalculator/UI/未追跡データに差分がないことを確認する。
- [ ] 実機/GPX確認が可能なら行い、不可なら未確認項目を明記する。
