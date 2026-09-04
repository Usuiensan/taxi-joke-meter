param(
    [string]$Path = 'C:\Users\miwam\Downloads\virtual_drive_test_bundle'
)

$bundlePath = (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath (Join-Path $bundlePath 'virtual_drive_1hz.csv'))) {
    throw "virtual_drive_1hz.csv がありません: $bundlePath"
}

$env:VIRTUAL_DRIVE_BUNDLE = $bundlePath
& "$PSScriptRoot\..\gradlew.bat" testDebugUnitTest --tests 'jp.usuiensan.taxijokemeter.domain.fare.VirtualDriveFareTest' --no-daemon
exit $LASTEXITCODE
