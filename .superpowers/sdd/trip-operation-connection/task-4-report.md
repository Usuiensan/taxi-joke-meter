# Task 4 実施報告

## 対象

`C:\private\taxi-joke-meter\taxi-joke-meter\.worktrees\trip-operation-connection`

Task 4 brief に従い、固定論理 Meter Surface の外枠、既存色、字体、料金表示、ChargeProgress、SafetyEvent、AudioCue、右側キーの7位置を維持したまま、乗務員操作メニューと料金設定画面の接続を実装した。

## 変更内容

- EMPTY の SoftKey を固定2行10枠へ変更。
  - 1行目: `休憩 / 迎車 / 予約 / 回送 / 空き`
  - 2行目: `レポート / 再発行 / 運賃制 / デバッグ / 空き`
- EMPTY の仮表示 `空車メニュー`、ページ遷移、ページ表示、`その他`、`戻る` は追加せず、既存の段階制メニューも使わない。
- EMPTY の `休憩`、`迎車`、`予約`、`回送`、`運賃制`、`デバッグ` を既存の MeterController または画面遷移へ接続した。
- OCCUPIED の SoftKey を `人員 / 経由地 / 迎車 / 早朝 / 高割` とし、経由地と迎車料金を既存 Controller API へ接続した。
- OCCUPIED に大きな乗車人員 `− / 現在値 / +` 操作を追加し、`MeterController.changePassengerCount` を呼び出すようにした。範囲端ではボタンを無効化し、Room は UI から直接操作していない。
- OCCUPIED に経由地件数と迎車料金適用状態を表示した。
- REST は EMPTY メニューを表示せず、下部右端の大きな `解除` を `REST_RELEASE` へ接続した。DEADHEAD の `解除` は既存の `RELEASE` を維持した。
- 右側の `空車 / 実車 / 支払 / 合計 / 一般 / 専用道 / 高速` を固定順・固定位置のまま配置し、状態キーだけを状態別に有効化した。無効キーはグレー表示かつクリックなし、道路モードキーは全状態で利用可能とした。
- `AppShell` は Meter/Debug から同じ既存料金設定画面へ遷移する callback を共有した。既存 FareRule 6項目と `迎車料金` / `予約料金` は保持した。
- domain、data、GPS コード、新規依存関係は変更していない。

## TDD 証跡

1. `MeterPresentationTest` に EMPTY 2行、OCCUPIED 主要キー、REST/DEADHEAD 解除、状態ラベル、右キー有効行列の期待値を先に追加した。
2. RED 実測:

   `./gradlew.bat :app:testDebugUnitTest --tests '*MeterPresentationTest' --no-daemon`

   `BUILD FAILED`。`emptyMenuRows`、`softKeyAction`、`rightKeyEnabled` が未実装という期待した compile failure だった。
3. `MeterScreen.kt` と `AppShell.kt` に最小変更を実装した。
4. GREEN 実測:

   `./gradlew.bat :app:testDebugUnitTest --tests '*MeterPresentationTest' --no-daemon`

   `BUILD SUCCESSFUL in 40s`。

## 検証

- `./gradlew.bat :app:testDebugUnitTest --tests '*MeterPresentationTest' --no-daemon`: PASS。
- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`: PASS、`BUILD SUCCESSFUL in 20s`。
- `git diff --check`: PASS。
- Gradle が既存の KSP/Kotlin 互換性警告と Android 37 SDK パス警告を出力したが、本タスクでは変更していない。
- 実機 UI 操作、スクリーンショット比較、connected Android test は本タスクでは実施していない。したがって固定面の視覚一致と実機タップ動作は未検証である。
- 既存の未追跡 `.superpowers/sdd/trip-operation-connection/` ファイルは保持した。
