param(
    [Parameter(Mandatory)][string] $WindowsInstaller,
    [string] $AndroidApk,
    [string] $NotesFile,
    [switch] $VerifyOnly
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'build-common.ps1')
$version = (Get-ZaVersion $root).versionName
if (-not $NotesFile) { $NotesFile = Join-Path $root "docs/release-$version.md" }
$repo = 'pyms305666/music-player'
$sha = (& git -C $root rev-parse HEAD).Trim()
if (& git -C $root status --porcelain --untracked-files=normal) { throw 'Publish requires a clean source tree; commit, then rebuild both artifacts' }
if (-not $AndroidApk) { $AndroidApk = Join-Path $root 'android-app/app/build/outputs/apk/debug/app-debug.apk' }
$records = @()
foreach ($pair in @(@($WindowsInstaller,'windows'),@($AndroidApk,'android-debug'))) {
    $artifact = (Resolve-Path -LiteralPath $pair[0]).Path
    $record = Assert-ZaArtifactRecord $artifact $pair[1] $sha $version
    if ($pair[1] -eq 'windows') {
        if ((Get-Item -LiteralPath $artifact).VersionInfo.FileVersion -ne $version) { throw 'Windows file version mismatch' }
    }
    $records += $record
}
if (-not (Test-Path -LiteralPath $NotesFile)) { throw 'Release notes missing' }
$branch = (& git -C $root branch --show-current).Trim()
$remote = gh api "repos/$repo/git/ref/heads/$branch" | ConvertFrom-Json
if ($LASTEXITCODE -or $remote.object.sha -ne $sha) { throw 'Remote branch does not match source commit' }
$runs = gh run list --repo $repo --workflow build.yml --commit $sha --limit 20 --json databaseId,conclusion,status,url | ConvertFrom-Json
if ($LASTEXITCODE) { throw 'Unable to query build workflow' }
$ci = $runs | Where-Object { $_.status -eq 'completed' -and $_.conclusion -eq 'success' } | Select-Object -First 1
if (-not $ci) { throw 'Both CI jobs must pass for this exact source commit before publication' }
$stage = Join-Path $root "release/$version"
New-Item -ItemType Directory -Force -Path $stage | Out-Null
$winName = "ZA-Music-Windows-$version.exe"; $apkName = "ZA-Music-Android-$version-debug.apk"
Copy-Item -LiteralPath $WindowsInstaller -Destination (Join-Path $stage $winName) -Force
Copy-Item -LiteralPath $AndroidApk -Destination (Join-Path $stage $apkName) -Force
$records[0].artifact = $winName; $records[1].artifact = $apkName
[ordered]@{version=$version; sourceCommit=$sha; ciUrl=$ci.url; artifacts=$records} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $stage 'build-info.json') -Encoding UTF8
$files = @($winName,$apkName,'build-info.json') | ForEach-Object { Join-Path $stage $_ }
$lines = $files | ForEach-Object { (Get-FileHash -LiteralPath $_).Hash.ToLowerInvariant() + '  ' + (Split-Path -Leaf $_) }
[IO.File]::WriteAllLines((Join-Path $stage 'SHA256SUMS.txt'),[string[]]$lines,[Text.UTF8Encoding]::new($false))
$files += Join-Path $stage 'SHA256SUMS.txt'
if ($VerifyOnly) { Write-Host "Release verification passed: $version source=$sha CI=$($ci.url)"; return }
# Never overwrite an existing public release or its assets.
$existing = gh release list --repo $repo --limit 100 --json tagName | ConvertFrom-Json
if ($LASTEXITCODE -or ($existing | Where-Object tagName -EQ $version)) { throw 'Cannot publish: release already exists or release listing failed' }
gh release create $version @files --repo $repo --target $sha --title "ZA音乐 $version" --notes-file $NotesFile --draft
if ($LASTEXITCODE) { throw 'Draft release creation/upload failed; inspect the draft before retrying' }
function Assert-RemoteAssets($Release) {
    foreach ($file in $files) {
        $item = Get-Item -LiteralPath $file
        $remoteAsset = $Release.assets | Where-Object name -EQ $item.Name
        if ($remoteAsset.state -ne 'uploaded' -or $remoteAsset.size -ne $item.Length -or $remoteAsset.digest -ne ('sha256:' + (Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant())) { throw "Uploaded asset verification failed: $($item.Name)" }
    }
}
$draft = gh api "repos/$repo/releases" | ConvertFrom-Json | Where-Object tag_name -EQ $version
if ($LASTEXITCODE -or -not $draft.draft) { throw 'Expected draft release missing' }
Assert-RemoteAssets $draft
gh release edit $version --repo $repo --draft=false --latest
if ($LASTEXITCODE) { throw 'Draft publication failed' }
$published = gh api "repos/$repo/releases/latest" | ConvertFrom-Json
if ($LASTEXITCODE -or $published.tag_name -ne $version -or $published.draft) { throw 'Release is not published as latest' }
Assert-RemoteAssets $published
$tag = gh api "repos/$repo/git/ref/tags/$version" | ConvertFrom-Json
if ($tag.object.type -eq 'tag') { $tag = gh api $tag.object.url | ConvertFrom-Json }
if ($LASTEXITCODE -or $tag.object.sha -ne $sha) { throw 'Published tag does not match artifact source' }
Write-Host "Published and verified: $($published.html_url) source=$sha"
