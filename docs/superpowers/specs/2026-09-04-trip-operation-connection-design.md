# Trip記録基盤への乗務員操作接続 設計

## 目的

既存のRoom v9 / Trip記録基盤へ、Meter Surfaceの乗務員操作を接続する。
対象はPICKUP、REST、EMPTY二段メニュー、人員、予約pending、迎車料金、経由地、乗降地点、非同期reverse geocode、REST距離自動解除、全状態のRoadMode操作である。帳票生成は対象外とする。

## 方針

- 状態遷移の正本は既存の`MeterStateMachine`に置き、`PICKUP`と`REST`を`MeterState`として追加する。
- 操作の副作用は既存の`MeterController`がまとめて実行し、UIからRoomを直接操作しない。
- 重要な操作結果は`DutyRecordRepository`経由で即時保存する。runtimeは表示用キャッシュとする。
- 迎車料金・予約料金は`FareRule`へ混ぜず、既存設定画面から別のSharedPreferences storeへ保存する。初期値は0円とし、0円設定ではchargeを作成しない。
- Roomスキーマは既存v9を利用し、今回の設定追加だけではmigrationを増やさない。
- 座標が取れる操作は座標を先にDBへ保存し、reverse geocodeは操作完了後に非同期で住所列を更新する。座標がないTrip開始はTripだけ作成し、地点行を作成しない。
- GPSの`occupancyState`は既存の料金計測のEMPTY/OCCUPIEDを壊さず、PICKUP/REST/DEADHEADを記録可能な状態値として拡張する。REST距離はvalid GPS segmentのみを累積する。

## データフロー

### Trip開始

`EMPTY`または`PICKUP`または`REST`から実車を押すと、必要ならRESTを終了し、Tripを1件だけ作成する。Trip開始後に最新有効GPSがあれば`PICKUP`の`TripPlace`を保存する。pending予約が有効で料金が0円より大きければ`RESERVATION` chargeを保存し、pendingを解除する。PICKUPからの開始では設定料金が0円より大きければ`PICKUP_FEE`を1件保存する。どちらのchargeも保存後に非同期住所補完を開始する。

### 迎車料金後付け

`OCCUPIED`または`PAYMENT`でのみ、現在Tripに`PICKUP_FEE`がない場合に1件追加する。同一Tripの既存chargeをtypeで確認し、二重登録しない。状態は`PICKUP`へ戻さない。

### 経由地と降車地

`OCCUPIED`中の経由地操作は、現在Tripの既存WAYPOINT最大sequence+1で即時保存する。`PAYMENT`では`DROPOFF` sequence 0をupsertし、同一Tripの再PAYMENTでは同じ行を更新する。`TOTAL`でTripを完了する。座標なしの場合は地点操作を保存せず、Trip/状態操作は成功させる。

### REST

EMPTYの休憩操作で`RestInterval`を作成し、RESTへ遷移する。解除は`MANUAL`、実車開始は`OCCUPIED_STARTED`で終了する。REST中のvalid GPS segment距離をDBへ継続保存し、累計1000m以上で`AUTO_DISTANCE_1KM`終了・EMPTYへ遷移する。invalid GPSは距離に加算しない。

## UI

- 右固定キーは空車・実車・支払・合計・一般・専用道・高速の7位置を全状態で維持し、無効時はグレーアウトしてタップを無効化する。
- EMPTYは第一段を休憩・迎車・予約・回送・空き、第二段をレポート・再発行・運賃制・デバッグ・空きとする。既存の段階制メニューは追加しない。
- OCCUPIEDの主要ソフトキーは人員・経由地・迎車を直接表示し、既存の未実装/既存機能は削除せず、空き枠を使って配置する。
- RESTはEMPTYメニューを表示せず、大きな解除操作を表示する。
- 人員は1〜9の大きな± UIとし、変更ごとにACTIVE TripをDB更新する。

## テストと検証

状態機械、設定store、Repositoryのcharge/place/rest更新、REST距離判定、UI表示・disableルールを単位テスト中心に追加する。実装後に`testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`、可能なら`connectedDebugAndroidTest`、`git diff --check`を実行する。実機/GPX確認はビルド成功とは別の証拠として報告する。

## 対象外

PDF/PNG/TXT帳票、完成した再発行、GPS unavailable overlay、安全運転スコア、IMU、欠損区間帳票、日跨ぎ表示は追加しない。
