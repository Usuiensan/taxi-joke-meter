# Task 1 実施報告

## 対象

`C:\private\taxi-joke-meter\taxi-joke-meter\.worktrees\trip-operation-connection`

要件ブリーフに従い、状態機械の拡張、ランタイム表示キャッシュ、迎車/予約料金の独立 SharedPreferences 保存、既存設定画面への2項目追加を実施した。

## TDD 証跡

1. `MeterStateMachineTest.emptyCanEnterPickupAndRestCanReleaseToEmpty` と `TripChargeSettingsStoreTest.savedSettingsAreLoadedAfterStoreIsRecreated` を先に追加した。
2. RED:
   `./gradlew.bat :app:testDebugUnitTest --tests '*MeterStateMachineTest'`
   は失敗した。`PICKUP`、`REST`、`REST_RELEASE` が本番コードに未定義という、追加機能に対応したコンパイルエラーだった。
3. 状態/アクション、キャッシュ、ストア、設定画面を最小実装した。
4. GREEN:
   `./gradlew.bat :app:testDebugUnitTest --tests '*MeterStateMachineTest'`
   は `BUILD SUCCESSFUL`。

## 変更内容

- `MeterState.REST` / `PICKUP` と `MeterAction.PICKUP` / `REST` / `REST_RELEASE` を追加。
- `EMPTY -> PICKUP`、`EMPTY -> REST`、`REST -> EMPTY` の遷移を追加し、EMPTY_MENU と既存遷移を保持。
- `MeterRuntimeSnapshot` に6つの表示キャッシュ項目を追加し、`resetMeter()` で初期化。
- `TripChargeSettingsStore` を新設。既定値は迎車料金・予約料金ともに0円。
- 既存の運賃設定画面に「迎車料金 (円)」「予約料金 (円)」を追加し、既存 FareRule の検証・画面遷移を維持。
- 新 enum に伴う既存 `when` の網羅性を保つため、`MeterController` と `MeterScreen` に最小の分岐を追加。

## 検証結果

- `./gradlew.bat :app:testDebugUnitTest --tests '*MeterStateMachineTest'`: PASS (`BUILD SUCCESSFUL`)
- `./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=jp.usuiensan.taxijokemeter.data.fare.TripChargeSettingsStoreTest'`: PASS (`BUILD SUCCESSFUL`)
  - Pixel 10a - 17: 1 test finished
  - Pixel_8 (AVD) - 17: 1 test finished
- `git diff --check`: PASS
- Android テスト実行時の debug コンパイル: PASS

## 境界・懸念

- 実機 UI 操作、Task 2 以降の Controller/Room 接続、迎車料金の実運用適用は未実施（後続 Task の範囲）。
- Android テストは2端末で実行したが、テスト自身は SharedPreferences の保存・再生成ロードを確認するものに限定している。
- 既存の未追跡 `.superpowers/sdd/trip-operation-connection/` 内のブリーフ等は変更せず、今回のレポートだけを追加した。
