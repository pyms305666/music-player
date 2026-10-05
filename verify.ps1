param([switch] $SkipClean)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
. (Join-Path $root 'scripts/build-common.ps1')
$tasks = if ($SkipClean) { @('test', ':shared:test', 'installDist') } else { @('clean', ':shared:clean', 'test', ':shared:test', 'installDist') }
Invoke-ZaGradle $root '.' $tasks
& (Join-Path $root 'scripts/test-build-tools.ps1')
& (Join-Path $root 'scripts/test-release-state.ps1')
Write-Host 'Verification passed: desktop and shared tests; installDist.'
