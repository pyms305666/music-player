$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'build-common.ps1')
$temporary = Join-Path $root ('build/build-tools-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
$artifact = Join-Path $temporary 'fixture.apk'
Set-Content -LiteralPath $artifact -Value 'test payload' -Encoding ASCII
$fixtureVersion = (Get-ZaVersion $root).versionName
$good = @{
    sourceCommit = 'fixture-commit'; sourceDirty = $false; version = $fixtureVersion; platform = 'android-debug'
    sha256 = (Get-FileHash $artifact).Hash.ToLowerInvariant()
    applicationId = 'app.musicplayer.android'
    signerSha256 = '1cd53ceaeef7ce7772d274b450982deeb34424d896612a66a95d9ee00dd2e3ff'
}
function Write-TestRecord($record) { $record | ConvertTo-Json | Set-Content -LiteralPath ($artifact + '.build.json') -Encoding UTF8 }
Write-TestRecord $good
$null = Assert-ZaArtifactRecord $artifact 'android-debug' 'fixture-commit' $fixtureVersion
$cases = @{
    sourceCommit = 'stale-commit'; sourceDirty = $true; version = '0.0.0'; platform = 'windows'
    sha256 = 'bad-hash'; applicationId = 'wrong.package'; signerSha256 = 'different-certificate'
}
foreach ($key in $cases.Keys) {
    $bad = $good.Clone(); $bad[$key] = $cases[$key]; Write-TestRecord $bad
    $rejected = $false
    try { $null = Assert-ZaArtifactRecord $artifact 'android-debug' 'fixture-commit' $fixtureVersion } catch { $rejected = $true }
    if (-not $rejected) { throw "Release gate accepted invalid $key" }
}
$bad = $good.Clone(); $bad.Remove('sourceDirty'); Write-TestRecord $bad
$rejected = $false
try { $null = Assert-ZaArtifactRecord $artifact 'android-debug' 'fixture-commit' $fixtureVersion } catch { $rejected = $true }
if (-not $rejected) { throw 'Missing provenance must fail closed' }
Write-Host 'BUILD TOOLS PASSED: valid provenance accepted; 8 invalid source/hash/identity cases rejected'
$release = $good.Clone(); $release.platform = 'android-release'; $release.qa = $true
Write-TestRecord $release
$rejected = $false
try { $null = Assert-ZaArtifactRecord $artifact 'android-release' 'fixture-commit' $fixtureVersion } catch { $rejected = $true }
if (-not $rejected) { throw 'QA release record accepted' }
$release.qa = $false; Write-TestRecord $release
$rejected = $false
try { $null = Assert-ZaArtifactRecord $artifact 'android-release' 'fixture-commit' $fixtureVersion } catch { $rejected = $true }
if (-not $rejected) { throw 'Invalid APK accepted solely from a release record' }
Write-Host 'FORMAL SIGNING GATE PASSED: QA and invalid binary rejected'
# The rejected Python verifier intentionally exits nonzero; do not leak it to callers.
$global:LASTEXITCODE = 0
