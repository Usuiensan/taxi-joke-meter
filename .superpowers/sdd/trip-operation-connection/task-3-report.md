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

## Fix round 1 status (2026-09-04, interrupted)

### Added regressions

- `MeterControllerOperationOrderingTest`: repeated REST, immediate `PAYMENT`/`TOTAL`/`VOIDED`, passenger/waypoint/pickup-fee ordering, and unchanged RoadMode.
- `RestDistanceAccumulatorTest`: sub-metre REST segments retain fractional metres.
- `MeasurementRuntimeStateTest`: sub-metre REST distance is retained across serialized samples.

### Test output

- RED: `./gradlew.bat :app:testDebugUnitTest --tests '*RestDistanceAccumulatorTest' --no-daemon` — `subMeterSegmentsAccumulateWithoutTruncation` failed as expected while `Long` truncation was present (3 tests, 1 failed).
- RED: `./gradlew.bat :app:testDebugUnitTest --tests '*MeasurementRuntimeStateTest' --tests '*RestDistanceAccumulatorTest' --no-daemon` — test compilation failed as expected because `restDistanceMeters` did not yet exist.
- RED: `./gradlew.bat :app:connectedDebugAndroidTest --project-prop 'android.testInstrumentationRunnerArguments.class=jp.usuiensan.taxijokemeter.data.meter.MeterControllerOperationOrderingTest' --no-daemon` — before serialization, 3 failures: repeated REST timed out; immediate trip ordering had no completed Trip; immediate VOIDED timed out.
- Partial GREEN before the final serialization edit: the repeated-REST regression passed; the connected suite still failed the two immediate-ordering regressions, demonstrating that `limitedParallelism(1)` alone did not serialize suspended Room operations.
- Unit GREEN before the final serialization edit: `./gradlew.bat :app:testDebugUnitTest --tests '*MeasurementRuntimeStateTest' --tests '*RestDistanceAccumulatorTest' --tests '*MeterControllerOperationsTest' --no-daemon` — PASS.
- Final connected command after adding the `Mutex` operation gate: `./gradlew.bat :app:connectedDebugAndroidTest --project-prop 'android.testInstrumentationRunnerArguments.class=jp.usuiensan.taxijokemeter.data.meter.MeterControllerOperationOrderingTest' --no-daemon` — interrupted by the user before a test result. Only its resolved Gradle processes (PIDs 476 and 47432) were stopped.
- Post-interruption compile: `./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (`BUILD SUCCESSFUL in 11s`, exit 0).
- Post-interruption `git diff --check` — PASS (exit 0; only LF/CRLF warnings).

### Conclusion

- The working tree compiles and remains coherent, but Fix round 1 is intentionally uncommitted.
- Review finding 1 has a passing regression before the final serialization edit. Findings 2 and 4 have compiled implementations but no completed post-change instrumentation result. Finding 3 has passing focused unit coverage before that edit. Therefore the review findings are **not fully verified as fixed**.

## Fix round 2 verification (2026-09-04)

- Focused ordering instrumentation: `.\gradlew.bat :app:connectedDebugAndroidTest --project-prop 'android.testInstrumentationRunnerArguments.class=jp.usuiensan.taxijokemeter.data.meter.MeterControllerOperationOrderingTest' --no-daemon` — PASS (`BUILD SUCCESSFUL in 24s`, exit 0). All 4 tests finished on both `Pixel_8(AVD) - 17` (`emulator-5554`) and `Pixel 10a - 17` (`5B181JEA300393`).
- Focused unit: `.\gradlew.bat :app:testDebugUnitTest --tests '*MeasurementRuntimeStateTest' --tests '*RestDistanceAccumulatorTest' --tests '*MeterControllerOperationsTest' --no-daemon` — PASS (`BUILD SUCCESSFUL in 23s`, exit 0).
- Full unit/build: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon` — PASS (`BUILD SUCCESSFUL in 13s`, exit 0).
- The existing KSP version and Android 37 platform-location warnings were emitted unchanged; no UI, migration, external API, SDK, or unrelated file was modified.

### Conclusion

- Repeated REST idempotence, ordered immediate Controller persistence through PAYMENT/TOTAL/VOIDED, fractional REST distance, and single-owner main-looper runtime-state updates are verified by the focused regressions and completed builds.
