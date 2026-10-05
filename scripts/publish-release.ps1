param(
    [Parameter(Mandatory)][string] $WindowsInstaller,
    [string] $AndroidApk,
    [string] $NotesFile,
    [switch] $VerifyOnly
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'build-common.ps1')
. (Join-Path $PSScriptRoot 'release-state.ps1')
Invoke-ZaReleaseUtf8 {
$version = (Get-ZaVersion $root).versionName
if (-not $NotesFile) { $NotesFile = Join-Path $root "docs/release-$version.md" }
$repo = 'pyms305666/music-player'
$sha = (& git -C $root rev-parse HEAD).Trim()
if (& git -C $root status --porcelain --untracked-files=normal) { throw 'Publish requires a clean source tree; commit, then rebuild both artifacts' }
if (-not $AndroidApk) { $AndroidApk = Join-Path $root "android-app/dist/ZA-Music-Android-$version.apk" }
$records = @()
foreach ($pair in @(@($WindowsInstaller,'windows'),@($AndroidApk,'android-release'))) {
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
if (-not $ci) { throw 'All CI jobs must pass for this exact source commit before publication' }
$stage = Join-Path $root "release/$version"
New-Item -ItemType Directory -Force -Path $stage | Out-Null
$winName = "ZA-Music-Windows-$version.exe"; $apkName = "ZA-Music-Android-$version.apk"
Copy-Item -LiteralPath $WindowsInstaller -Destination (Join-Path $stage $winName) -Force
Copy-Item -LiteralPath $AndroidApk -Destination (Join-Path $stage $apkName) -Force
$records[0].artifact = $winName; $records[1].artifact = $apkName
[ordered]@{version=$version; sourceCommit=$sha; ciUrl=$ci.url; artifacts=$records} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $stage 'build-info.json') -Encoding UTF8
$files = @($winName,$apkName,'build-info.json') | ForEach-Object { Join-Path $stage $_ }
$lines = $files | ForEach-Object { (Get-FileHash -LiteralPath $_).Hash.ToLowerInvariant() + '  ' + (Split-Path -Leaf $_) }
[IO.File]::WriteAllLines((Join-Path $stage 'SHA256SUMS.txt'),[string[]]$lines,[Text.UTF8Encoding]::new($false))
$files += Join-Path $stage 'SHA256SUMS.txt'
if ($VerifyOnly) { Write-Host "Release verification passed: $version source=$sha CI=$($ci.url)"; return }
# Never overwrite an existing public release or its assets.
$existing = gh release list --repo $repo --limit 100 --json tagName | ConvertFrom-Json
if ($LASTEXITCODE -or ($existing | Where-Object tagName -EQ $version)) { throw 'Cannot publish: release already exists or release listing failed' }
$title = Get-ZaReleaseTitle $version
gh release create $version @files --repo $repo --target $sha --title $title --notes-file $NotesFile --draft
if ($LASTEXITCODE) { throw 'Draft release creation/upload failed; inspect the draft before retrying' }
function Assert-RemoteAssets($Release) {
    if (@($Release.assets).Count -ne $files.Count) { throw 'Uploaded asset count mismatch' }
    foreach ($file in $files) {
        $item = Get-Item -LiteralPath $file
        $assetMatches = @($Release.assets | Where-Object name -EQ $item.Name)
        if ($assetMatches.Count -ne 1) { throw "Expected one uploaded asset: $($item.Name)" }
        $remoteAsset = $assetMatches[0]
        if ($remoteAsset.state -ne 'uploaded' -or $remoteAsset.size -ne $item.Length -or $remoteAsset.digest -ne ('sha256:' + (Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant())) { throw "Uploaded asset verification failed: $($item.Name)" }
    }
}
$releases = gh api "repos/$repo/releases" | ConvertFrom-Json
if ($LASTEXITCODE) { throw 'Unable to query draft releases' }
$draft = Get-ZaDraftRelease $releases $version $sha
Assert-RemoteAssets $draft
gh release edit $version --repo $repo --draft=false --latest
if ($LASTEXITCODE) { throw 'Draft publication failed' }
$published = gh api "repos/$repo/releases/latest" | ConvertFrom-Json
if ($LASTEXITCODE -or $published.tag_name -ne $version -or $published.draft -or $published.name -ne $title) { throw 'Release is not published as latest with the expected title' }
Assert-RemoteAssets $published
$tag = gh api "repos/$repo/git/ref/tags/$version" | ConvertFrom-Json
if ($tag.object.type -eq 'tag') { $tag = gh api $tag.object.url | ConvertFrom-Json }
if ($LASTEXITCODE -or $tag.object.sha -ne $sha) { throw 'Published tag does not match artifact source' }
Write-Host "Published and verified: $($published.html_url) source=$sha"
}
