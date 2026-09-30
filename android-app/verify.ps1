param([switch] $SkipClean)
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'scripts/build-common.ps1')
$sourceSnapshot = Get-ZaSourceSnapshot $root
$tasks = @(':shared:test', 'assembleDebug', 'assembleDebugAndroidTest', 'lintDebug')
if (-not $SkipClean) { $tasks = @('clean', ':shared:clean') + $tasks }
Invoke-ZaGradle $root 'android-app' $tasks
$apk = Join-Path $PSScriptRoot 'app/build/outputs/apk/debug/app-debug.apk'
Write-ZaBuildRecord $root $apk 'android-debug' $sourceSnapshot
Write-Host 'Verification passed: shared Java 17 tests, native APKs and Android lint.'
