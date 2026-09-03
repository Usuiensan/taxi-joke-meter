[CmdletBinding()]
param(
    [string]$Path = (Join-Path $PSScriptRoot '..\imperial-palace-taxi-meter-test.gpx'),
    [string]$Serial = 'emulator-5554',
    [switch]$Loop,
    [ValidateRange(0.001, 1000)]
    [double]$SpeedMultiplier = 1
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "GPX file not found: $Path"
}

$adb = (Get-Command adb -ErrorAction Stop).Source
& $adb -s $Serial get-state *> $null
if ($LASTEXITCODE -ne 0) {
    throw "ADB device is not ready: $Serial"
}

[xml]$gpx = Get-Content -LiteralPath $Path -Raw
$namespace = New-Object System.Xml.XmlNamespaceManager($gpx.NameTable)
$namespace.AddNamespace('gpx', $gpx.DocumentElement.NamespaceURI)
$points = @($gpx.SelectNodes('//gpx:trkpt', $namespace))
if ($points.Count -eq 0) {
    throw "No track points found: $Path"
}

$track = foreach ($point in $points) {
    $speedElement = @($point.extensions.ChildNodes | Where-Object LocalName -eq 'speed_mps') | Select-Object -First 1
    $speedMps = 0.0
    if ($null -ne $speedElement -and $speedElement.InnerText) {
        $speedMps = [double]::Parse($speedElement.InnerText, [Globalization.CultureInfo]::InvariantCulture)
    }

    [pscustomobject]@{
        Latitude = [double]::Parse($point.lat, [Globalization.CultureInfo]::InvariantCulture)
        Longitude = [double]::Parse($point.lon, [Globalization.CultureInfo]::InvariantCulture)
        Time = if ($point.time) { [DateTimeOffset]::Parse($point.time, [Globalization.CultureInfo]::InvariantCulture) } else { $null }
        VelocityKnots = $speedMps * 1.943844
    }
}

try {
    do {
        for ($index = 0; $index -lt $track.Count; $index++) {
            $current = $track[$index]
            $args = @(
                '-s', $Serial, 'emu', 'geo', 'fix',
                ('{0:R}' -f $current.Longitude),
                ('{0:R}' -f $current.Latitude),
                '10', '8',
                ('{0:R}' -f $current.VelocityKnots)
            )

            & $adb @args
            if ($LASTEXITCODE -ne 0) {
                throw "geo fix failed at track point $($index + 1)"
            }

            if ($index -lt $track.Count - 1 -and $null -ne $current.Time -and $null -ne $track[$index + 1].Time) {
                $milliseconds = [math]::Max(0, ($track[$index + 1].Time - $current.Time).TotalMilliseconds / $SpeedMultiplier)
                if ($milliseconds -gt 0) {
                    Start-Sleep -Milliseconds ([int][math]::Round($milliseconds))
                }
            }
        }
    } while ($Loop)
}
finally {
    Write-Host "GPX replay stopped. Points sent: $($track.Count)"
}
