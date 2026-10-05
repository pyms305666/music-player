$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release-state.ps1')
$releases = '[{"tag_name":"old","draft":false},{"tag_name":"4.1.12","draft":true,"target_commitish":"source"}]' | ConvertFrom-Json
$draft = Get-ZaDraftRelease $releases '4.1.12' 'source'
if ($draft.tag_name -ne '4.1.12') { throw 'Array selection failed' }
$expected = 'ZA' + [char]0x97f3 + [char]0x4e50 + ' 4.1.12'
if ((Get-ZaReleaseTitle '4.1.12') -cne $expected) { throw 'Unicode release title failed' }
$beforeEncoding = [Console]::OutputEncoding.CodePage
$fixture = Join-Path $PSScriptRoot 'release-json-fixture.ps1'
$native = Invoke-ZaReleaseUtf8 { & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $fixture | ConvertFrom-Json }
if ($LASTEXITCODE -or $native.name -cne $expected) { throw 'Native UTF-8 JSON title decoding failed' }
if ([Console]::OutputEncoding.CodePage -ne $beforeEncoding) { throw 'Console encoding was not restored' }
$cases = @(
    @{releases=$releases;version='missing';source='source'},
    @{releases=$releases;version='old';source='source'},
    @{releases=$releases;version='4.1.12';source='wrong'},
    @{releases=@($draft,$draft);version='4.1.12';source='source'}
)
foreach ($case in $cases) {
    $rejected = $false
    try { Get-ZaDraftRelease $case.releases $case.version $case.source | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Invalid release state accepted' }
}
Write-Host 'RELEASE STATE PASSED: Unicode title, array selection and four rejection cases'
