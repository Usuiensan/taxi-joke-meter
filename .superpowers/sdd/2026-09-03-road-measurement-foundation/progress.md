# SDD ledger — plan: docs/superpowers/plans/2026-09-03-road-measurement-foundation.md


## Task 1 review fix report (2026-09-03)

- 無効サンプルを除去して連結せず、元のサンプル列で両端が有効な区間だけ低速時間へ加算するよう修正。
- 通知窓、平均速度閾値、クールダウン値を `HighwayModeReminderEvaluator` のコンストラクタで設定可能にした。既存通知ありは one-time 契約どおり常に `false`。
- 無効 GPS gap 回帰テスト、設定値の境界テスト、全モード変更 payload テストを追加。
- focused unit tests: PASS（11 tests）。`git diff --check`: PASS。

## Task 1 scoped re-review fix report (2026-09-03)

- one-time 通知契約で使用されていなかった `notificationCooldownSeconds` の引数・検証・テスト指定を削除。
- `reminderWindowSeconds` と `averageSpeedThresholdKmh` の設定可能性は維持。
- focused unit tests: PASS。`git diff --check`: PASS。

## Task 1 evaluator final review fix report (2026-09-03)

- `HighwayModeSample` を削除し、共通 `DrivingSample` の `measurementQuality == VALID` を有効条件に変更。
- 判定対象を `[now - reminderWindowSeconds, now]` 内の実サンプル区間に限定し、窓外サンプルの延長計上を削除。
- 窓外サンプル、UNAVAILABLE サンプル、公開 `DrivingSample` 入力の focused tests を追加・更新。
- focused unit tests: PASS（13 tests）。`git diff --check`: PASS。
## Preflight scan

| Scope | Relationship | Finding | Ruling |
|---|---|---|---|
| Task 1 ↔ Task 2 | `RoadMode` is consumed by `DrivingSample`; both use existing `SafetyRoadMode` values | No shared file; public enum is already stable | Keep Task 1 independent; Task 2 consumes existing `RoadMode` |
| Task 1 ↔ Task 3 | No direct interface dependency | No conflict | Execute independently |
| Task 2 ↔ Task 3 | Task 3 persists `DrivingSample` produced by Task 2 | Entity mapping must preserve all raw fields | Task 3 follows Task 2 public model |
| Task 1 | Tests target controller/evaluator public behavior | Self-consistent | Proceed |
| Task 2 | Tests target accumulator and safety limit behavior | Self-consistent | Proceed |
| Task 3 | Instrumentation test targets repository round-trip | Self-consistent; device availability may limit execution | Build and unit evidence remain mandatory |

Task 1: complete (commits f01ba23..5767d04, review clean)
Task 2: complete (commit 3e37a95, review clean; P3 unused import and extra mixed multi-add test deferred)
Task 3: complete (commits 4f5bda0..397de52, review clean; connected instrumentation unavailable)

## Task 3 review fix report (2026-09-03)

- `DrivingSample` の timestamp を epoch second + nano の複合主キーで保存し、同一ミリ秒内の distinct samples と完全な `Instant` 精度を保持。
- `DutyDatabase.build` に 1→2 / 2→3 migration を登録し、version 2 DB からの migration と保存・復元テストを追加。
- `testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`: PASS。
- `connectedDebugAndroidTest`: 未実行（connected device なし）。
