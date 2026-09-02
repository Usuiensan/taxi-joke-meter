# SDD ledger — plan: docs/superpowers/plans/2026-09-03-road-measurement-foundation.md


## Task 1 review fix report (2026-09-03)

- 無効サンプルを除去して連結せず、元のサンプル列で両端が有効な区間だけ低速時間へ加算するよう修正。
- 通知窓、平均速度閾値、クールダウン値を `HighwayModeReminderEvaluator` のコンストラクタで設定可能にした。既存通知ありは one-time 契約どおり常に `false`。
- 無効 GPS gap 回帰テスト、設定値の境界テスト、全モード変更 payload テストを追加。
- focused unit tests: PASS（11 tests）。`git diff --check`: PASS。
## Preflight scan

| Scope | Relationship | Finding | Ruling |
|---|---|---|---|
| Task 1 ↔ Task 2 | `RoadMode` is consumed by `DrivingSample`; both use existing `SafetyRoadMode` values | No shared file; public enum is already stable | Keep Task 1 independent; Task 2 consumes existing `RoadMode` |
| Task 1 ↔ Task 3 | No direct interface dependency | No conflict | Execute independently |
| Task 2 ↔ Task 3 | Task 3 persists `DrivingSample` produced by Task 2 | Entity mapping must preserve all raw fields | Task 3 follows Task 2 public model |
| Task 1 | Tests target controller/evaluator public behavior | Self-consistent | Proceed |
| Task 2 | Tests target accumulator and safety limit behavior | Self-consistent | Proceed |
| Task 3 | Instrumentation test targets repository round-trip | Self-consistent; device availability may limit execution | Build and unit evidence remain mandatory |
