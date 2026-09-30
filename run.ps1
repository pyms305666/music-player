param([Parameter(ValueFromRemainingArguments = $true)][string[]] $GradleArgs)
. (Join-Path $PSScriptRoot 'scripts/build-common.ps1')
if (-not $GradleArgs) { $GradleArgs = @('run') }
Invoke-ZaGradle $PSScriptRoot '.' $GradleArgs
