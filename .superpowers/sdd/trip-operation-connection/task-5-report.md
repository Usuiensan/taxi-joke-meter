# Task 5 実装レポート

## 結論

座標地点を先に `TripPlace` として保存し、既存 `MeterController` の IO scope から非同期に Android `Geocoder` を実行して、取得できた住所項目だけを更新する処理を追加した。Geocoder の例外・空結果は `null` として扱い、元の座標行と Trip 操作を保持する。

## TDD

### RED

先に `app/src/test/java/jp/usuiensan/taxijokemeter/domain/location/ReverseGeocoderTest.kt` を追加し、住所4項目の純粋マッピングと失敗時の `null` を定義した。

実行:

```text
.\gradlew.bat :app:testDebugUnitTest --tests '*ReverseGeocoderTest' --no-daemon
```

結果: FAIL。`mapReverseGeocode` と `ReverseGeocodeAddress` の unresolved reference で、実装不足による失敗を確認した。

### GREEN

同じ focused test を再実行し、`BUILD SUCCESSFUL` を確認した。

## 変更内容

- `domain/location/ReverseGeocoder.kt`: nullable な `ReverseGeocodeResult`、入力値、純粋マッピングを追加。
- `data/location/AndroidReverseGeocoder.kt`: Android `Geocoder` の最小アダプターを追加。例外・未対応・空結果は `null`。
- `data/duty/DutyDao.kt`: 住所4項目を `COALESCE` 更新にして、未取得項目を既存値のまま保持。
- `data/meter/MeterController.kt`: pickup、waypoint、dropoff の座標行保存後に同じ IO scope で逆ジオコードを起動。
- `app/src/test/.../ReverseGeocoderTest.kt`: 純粋マッピングと失敗結果の focused test。

UI/layout、Room schema/migration、fare/GPS logic、dependencies、無関係なファイルは変更していない。

## 検証

- `:app:testDebugUnitTest --tests '*ReverseGeocoderTest' --no-daemon`: PASS（BUILD SUCCESSFUL）
- `:app:testDebugUnitTest --no-daemon`: PASS（BUILD SUCCESSFUL）
- `:app:assembleDebug --no-daemon`: PASS（BUILD SUCCESSFUL）
- `git diff --check`: PASS

## 懸念・未検証境界

- Android `Geocoder.getFromLocation` の deprecated 警告が出る。今回の scope では既存 platform API を使用し、外部依存は追加していない。
- 既存の KSP/Kotlin 互換性警告と SDK platform 配置警告が出るが、今回の unit/build は成功した。
- 実機での Geocoder provider 応答、DB 更新完了タイミング、接続 instrumentation は未実施。
