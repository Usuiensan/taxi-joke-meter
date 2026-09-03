# Task 3 report: 乗務員操作をTripとGPS記録へ接続

## Scope

- `MeterController` に、予約、乗車人員、経由地、迎車料金、PICKUP/REST、Trip開始・完了の副作用を接続した。
- GPS sample の occupancy 状態を `PICKUP`、`REST`、`DEADHEAD` まで保持し、REST の valid segment だけを累積して `RestInterval` へ保存するようにした。
- 1,000m 以上の REST は `AUTO_DISTANCE_1KM` で終了し、runtime を EMPTY + EMPTY_MENU に戻す。
- Room schema、migration、UI、外部 API は変更していない。

## TDD

1. `MeterControllerOperationsTest` と `RestDistanceAccumulatorTest` を先に追加した。
2. RED: focused test 実行は、`startsNewTrip`、`tripStartChargeTypes`、`clampPassengerCount`、`measurementOccupancyState`、`RestDistanceAccumulator`、追加の `OccupancyState` が未実装のため `compileDebugUnitTestKotlin` で失敗した。
3. GREEN: 最小の Controller/GPS/domain 実装後、focused test と既存 meter/location/measurement test を通過した。

## Behavior covered

- EMPTY/PICKUP/REST/TOTAL からだけ新規 Trip を開始し、PAYMENT から OCCUPIED へ戻る場合は同じ Trip を維持する。
- REST 開始は `RestInterval` を作成し、REST 解除は `MANUAL`、REST からの実車開始は `OCCUPIED_STARTED` で終了する。
- PICKUP 開始時の正の `PICKUP_FEE`、pending reservation の正の `RESERVATION` を一度だけ保存し、reservation pending を Trip 開始時に解除する。
- OCCUPIED から PAYMENT で最新 valid GPS を DROPOFF sequence 0 として upsert する。Waypoint は OCCUPIED 中に最新 valid GPS がある場合だけ sequence を進めて保存する。
- 人数は 1..9 に clamp し、active Trip へ直ちに保存する。後付けの迎車料金は OCCUPIED/PAYMENT だけで `addChargeIfAbsent` を使う。
- 同一 RoadMode の選択は何もしない。変更時だけ既存の `SAFETY_ROAD_MODE_CHANGE` と measurement state 更新を行う。
- Fare accumulation は OCCUPIED sample のみ。REST の invalid segment は 0m とする。

## Verification

- RED: `./gradlew.bat :app:testDebugUnitTest --tests '*MeterControllerOperationsTest' --tests '*RestDistanceAccumulatorTest' --no-daemon` — expected compile failure (missing production symbols).
- Focused/existing: `./gradlew.bat :app:testDebugUnitTest --tests 'jp.usuiensan.taxijokemeter.data.meter.*' --tests 'jp.usuiensan.taxijokemeter.data.location.*' --tests 'jp.usuiensan.taxijokemeter.domain.measurement.*' --tests 'jp.usuiensan.taxijokemeter.domain.meter.*' --no-daemon` — PASS.
- Full unit/build: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon` — PASS.
- `git diff --check` — PASS.

## Boundaries and concerns

- Connected instrumentation/device/GPX verification was not run in Task 3; it remains the Task 6 acceptance boundary.
- The build reports pre-existing toolchain/SDK warnings for KSP and the Android 37 platform location; neither was changed in this task.
