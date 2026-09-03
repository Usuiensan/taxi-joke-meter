# Task 2 実施報告

## 対象

`C:\private\taxi-joke-meter\taxi-joke-meter\.worktrees\trip-operation-connection`

Task 2 brief に従い、既存 Room v9 の `DutyDao` と `DutyRecordRepository` に TripPlace / TripCharge / RestInterval の検索・更新処理を追加し、repository instrumentation test を追加した。`Trip.kt`、UI、GPS service、fare logic、migration は変更していない。

## TDD 証跡

1. `DutyRecordRepositoryTest` に PICKUP_FEE idempotence、WAYPOINT sequence 1/2/3、DROPOFF sequence 0 upsert、passenger/rest 更新、座標なし地点操作のテストを先に追加した。
2. RED: `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepositoryTest'` は `addChargeIfAbsent`、`addWaypoint`、`upsertDropoff`、`updatePassengerCount`、`startRestInterval`、`updateRestDistance`、`finishRestInterval` および DAO 検索の unresolved reference で失敗した。
3. DAO の検索・更新 query と repository の最小実装を追加した。charge の存在確認、waypoint 最大 sequence 算出、dropoff upsert は `withTransaction` 内で実行した。
4. REST 距離更新が終了時刻・理由を消さないよう、距離更新 query と終了 query を分離した。

## 変更内容

- `DutyDao`: TripPlace 検索、WAYPOINT 最大 sequence、TripCharge 一覧、RestInterval 検索、住所・乗客数・REST 距離・REST 終了の query を追加。
- `DutyRecordRepository`: `addChargeIfAbsent`、`addWaypoint`、`upsertDropoff`、`savePickupPlace`、`updatePassengerCount`、`startRestInterval`、`updateRestDistance`、`finishRestInterval` を追加。
- 座標がない地点操作は保存せず成功する。地点 entity は既存 `RecordedPlace` から変換し、DROPOFF は sequence 0 の既存 ID を再利用する。

## 検証結果

- RED focused instrumentation: PASS（期待した unresolved reference による失敗を確認）。
- `.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepositoryTest'`: PASS、Pixel 10a - 17 と Pixel_8(AVD) - 17 の各10テスト。
- `git diff --check`: PASS。
- Gradle 実行時に既存の KSP/Kotlin 互換性警告（`ksp-2.2.10-2.0.2` と `kotlin-2.3.21`）が表示されたが、テストは成功した。

## 境界・懸念

- `addChargeIfAbsent` の charge type は既存スキーマどおり String とし、列挙型は追加していない。
- 住所補完の実処理、MeterController/GPS 接続、実機 UI 受入は Task 3 以降の範囲で未実施。
- 既存の未追跡 `.superpowers/sdd/trip-operation-connection/` ファイルは変更していない。

## Round 1 fix: zero-valued charge

レビュー指摘により、`addChargeIfAbsent` が `amountYen == 0` の PICKUP_FEE / RESERVATION 設定でも行を作成する仕様不一致を確認した。`zeroAmountChargeIsNotInserted` を先に追加し、現行実装で両端末のテストが失敗することを確認した。

RED の実測出力:

```text
DutyRecordRepositoryTest > zeroAmountChargeIsNotInserted[Pixel 10a - 17] FAILED
DutyRecordRepositoryTest > zeroAmountChargeIsNotInserted[Pixel_8(AVD) - 17] FAILED
java.lang.AssertionError
at jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepositoryTest.zeroAmountChargeIsNotInserted(DutyRecordRepositoryTest.kt:125)
at jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepositoryTest$zeroAmountChargeIsNotInserted$1.invokeSuspend(DutyRecordRepositoryTest.kt:130)
Tests on Pixel 10a - 17 failed: There was 1 failure(s).
Tests on Pixel_8(AVD) - 17 failed: There was 1 failure(s).
> Task :app:connectedDebugAndroidTest FAILED
BUILD FAILED in 14s
```

修正は `addChargeIfAbsent` の `withTransaction` ブロック冒頭に `if (amountYen == 0L) return@withTransaction` を追加しただけである。

GREEN / covering test の実測出力:

```text
Command: .\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepositoryTest'
Starting 11 tests on Pixel 10a - 17
Finished 11 tests on Pixel 10a - 17
Starting 11 tests on Pixel_8(AVD) - 17
Finished 11 tests on Pixel_8(AVD) - 17
> Task :app:connectedDebugAndroidTest
BUILD SUCCESSFUL in 18s

Command: git diff --check
Result: PASS
```

この round 1 fix は `DutyRecordRepository.kt`、`DutyRecordRepositoryTest.kt`、本 report のみを変更し、未追跡ファイルは保持した。
