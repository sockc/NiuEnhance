# NiuDash Windows local-only data collector. Requires official Android Platform-Tools.
# Examples:
#   powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode status
#   powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode apk
#   powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode mark -Label "wallpaper-start"
#   powershell -ExecutionPolicy Bypass -File .\tools\niudash\collect.ps1 -Mode bugreport
[CmdletBinding()]
param(
  [ValidateSet('status','apk','mark','bugreport')]
  [string]$Mode = 'status',
  [string]$Label = '',
  [string]$Destination = 'niudash-private'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$adb = Get-Command adb -ErrorAction Stop
$devices = @(& $adb.Source devices | Select-String '^\S+\s+device$')
if ($devices.Count -ne 1) {
  throw "Please connect exactly ONE authorized Android device (adb devices): found $($devices.Count)"
}
$dir = Join-Path (Get-Location) $Destination
New-Item -ItemType Directory -Force -Path $dir | Out-Null

switch ($Mode) {
  'status' {
    Write-Host 'Device connected. Check Niu official app package:'
    & $adb.Source shell pm path com.niu.cloud
    Write-Host "Local-only working directory: $dir"
  }
  'apk' {
    $appDir = Join-Path $dir 'phone-app-apks'
    New-Item -ItemType Directory -Force -Path $appDir | Out-Null
    $paths = @(& $adb.Source shell pm path com.niu.cloud |
      ForEach-Object { $_.Trim() } |
      Where-Object { $_ -match '^package:/' } |
      ForEach-Object { $_ -replace '^package:', '' })
    if ($paths.Count -eq 0) { throw 'com.niu.cloud APK path not found' }
    $index = 0
    foreach ($remote in $paths) {
      $index++
      $filename = '{0:D2}-{1}' -f $index, [System.IO.Path]::GetFileName($remote)
      $target = Join-Path $appDir $filename
      Write-Host "Copying installed APP package file $index of $($paths.Count) (personal research only)..."
      & $adb.Source pull $remote $target
      if ($LASTEXITCODE -ne 0) { throw "adb pull failed for APK part $index" }
    }
    Write-Host "APP package copies stored locally: $appDir"
    Write-Host 'These are PHONE APP files, not confirmed NIU instrument firmware resources.'
    Write-Host 'DO NOT upload or commit the copied APKs.'
  }
  'mark' {
    if ([string]::IsNullOrWhiteSpace($Label)) { throw 'Use -Label "idle-start" etc.' }
    $safe = $Label -replace '[^a-zA-Z0-9_-]','_'
    $phoneUtc = (& $adb.Source shell date -u '+%Y-%m-%dT%H:%M:%SZ' |
      Select-Object -First 1).Trim()
    $line = $safe + [char]9 + $phoneUtc
    Add-Content -Path (Join-Path $dir 'markers.tsv') -Value $line -Encoding UTF8
    Write-Host "Marker: $safe at phone UTC $phoneUtc"
  }
  'bugreport' {
    Write-Warning 'A bugreport can contain accounts, location, Bluetooth traffic, and other sensitive data.'
    $answer = Read-Host 'Save a private bugreport locally? Type YES to continue'
    if ($answer -cne 'YES') { Write-Host 'Cancelled.'; return }
    $target = Join-Path $dir ('fx-bugreport-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
    & $adb.Source bugreport $target
    if ($LASTEXITCODE -ne 0) { throw 'adb bugreport failed' }
    Write-Host "Private bugreport saved: $target"
    Write-Host 'Do not upload the raw file publicly. Inspect it locally.'
  }
}
