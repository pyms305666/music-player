$ErrorActionPreference = 'Stop'
function Get-ZaVersion([string] $Root) {
    $values = @{}
    Get-Content -LiteralPath (Join-Path $Root 'version.properties') | ForEach-Object {
        if ($_ -match '^(versionName|versionCode)=(.+)$') { $values[$Matches[1]] = $Matches[2].Trim() }
    }
    if ($values.versionName -notmatch '^\d+\.\d+\.\d+$' -or $values.versionCode -notmatch '^\d+$') { throw 'Invalid version.properties' }
    return $values
}
function Invoke-ZaGradle([string] $Root, [string] $Project, [string[]] $Tasks) {
    $projectPath = Join-Path $Root $Project
    $properties = Get-Content -LiteralPath (Join-Path $projectPath 'gradle/wrapper/gradle-wrapper.properties') -Raw
    if ($properties -notmatch 'gradle-([\d.]+)-bin.zip') { throw 'Invalid Gradle wrapper configuration' }
    $gradleVersion = $Matches[1]
    $localGradle = Join-Path $Root ".tools/gradle-$gradleVersion/bin/gradle.bat"
    $mappedDrive = $null
    $oldJava = $env:JAVA_HOME; $oldPath = $env:PATH
    $oldAndroid = $env:ANDROID_HOME; $oldSdk = $env:ANDROID_SDK_ROOT
    try {
        $workRoot = $Root
        if ($Root -match '[^\x00-\x7F]') {
            $used = @(Get-PSDrive -PSProvider FileSystem | ForEach-Object { $_.Name.ToUpperInvariant() })
            $letter = @('M','N','P','Q','R','S','T','U','V','W','X','Y','Z') | Where-Object { $used -notcontains $_ } | Select-Object -First 1
            if (-not $letter) { throw 'No free drive letter for Unicode path mapping' }
            $mappedDrive = $letter + ':'
            & subst $mappedDrive $Root
            if ($LASTEXITCODE -ne 0) { throw 'Temporary project mapping failed' }
            $workRoot = $mappedDrive + '\'
        }
        if ($Project -eq 'android-app') {
            $localJdk = Join-Path $Root '.tools/jdk-17-android'
            if (Test-Path (Join-Path $localJdk 'bin/java.exe')) { $env:JAVA_HOME = $localJdk }
            $sdk = if (Test-Path (Join-Path $Root '.tools/android-sdk')) { Join-Path $workRoot '.tools/android-sdk' } else { $env:ANDROID_HOME }
            if ($sdk) {
                $env:ANDROID_HOME = $sdk; $env:ANDROID_SDK_ROOT = $sdk
                $escaped = $sdk.Replace('\','\\').Replace(':','\:')
                Set-Content -LiteralPath (Join-Path $projectPath 'local.properties') -Encoding ASCII -Value "sdk.dir=$escaped"
            }
        } elseif (Test-Path 'C:/jdk-25.0.2/bin/java.exe') { $env:JAVA_HOME = 'C:/jdk-25.0.2' }
        if ($env:JAVA_HOME) { $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH }
        $workProject = Join-Path $workRoot $Project
        $gradle = if (Test-Path $localGradle) { $localGradle } else { Join-Path $workProject 'gradlew.bat' }
        $cache = Join-Path $Root ".tools/gradle-cache-$($Project.Replace('.','desktop'))"
        Push-Location $workProject
        try {
            & $gradle @Tasks '--no-daemon' '--console=plain' '--project-cache-dir' $cache
            if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
        } finally { Pop-Location }
    } finally {
        if ($mappedDrive) { & subst $mappedDrive /d }
        $env:JAVA_HOME = $oldJava; $env:PATH = $oldPath
        $env:ANDROID_HOME = $oldAndroid; $env:ANDROID_SDK_ROOT = $oldSdk
    }
}
function Get-ZaSourceSnapshot([string] $Root) {
    return @{ commit = (& git -C $Root rev-parse HEAD).Trim(); dirty = [bool](& git -C $Root status --porcelain --untracked-files=normal) }
}
function Write-ZaBuildRecord([string] $Root, [string] $Artifact, [string] $Platform, [hashtable] $Snapshot) {
    $version = Get-ZaVersion $Root
    $commit = (& git -C $Root rev-parse HEAD).Trim()
    $dirty = [bool](& git -C $Root status --porcelain --untracked-files=normal)
    if (-not $Snapshot -or $Snapshot.commit -ne $commit -or (-not $Snapshot.dirty -and $dirty)) { throw 'Source changed during build; rebuild from a stable source snapshot' }
    $dirty = $dirty -or $Snapshot.dirty
    $record = [ordered]@{
        version = $version.versionName; versionCode = [int]$version.versionCode
        sourceCommit = $commit; sourceDirty = $dirty; platform = $Platform
        artifact = (Split-Path -Leaf $Artifact); sha256 = (Get-FileHash -LiteralPath $Artifact -Algorithm SHA256).Hash.ToLowerInvariant()
        builtAtUtc = [DateTime]::UtcNow.ToString('o')
    }
    if ($Platform -eq 'android-debug') {
        $metadataPath = Join-Path $Root 'android-app/app/build/outputs/apk/debug/output-metadata.json'
        $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
        if ($metadata.applicationId -ne 'app.musicplayer.android' -or $metadata.elements[0].versionName -ne $version.versionName -or $metadata.elements[0].versionCode -ne [int]$version.versionCode) { throw 'APK metadata does not match the canonical version and package ID' }
        $record.applicationId = $metadata.applicationId
        $oldJava = $env:JAVA_HOME
        try {
            $localJdk = Join-Path $Root '.tools/jdk-17-android'
            if (Test-Path $localJdk) { $env:JAVA_HOME = $localJdk }
            $sdk = if (Test-Path (Join-Path $Root '.tools/android-sdk')) { Join-Path $Root '.tools/android-sdk' } else { $env:ANDROID_HOME }
            $signer = & (Join-Path $sdk 'build-tools/35.0.0/apksigner.bat') verify --print-certs $Artifact
            if ($LASTEXITCODE) { throw 'APK signature verification failed' }
            $line = $signer | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest: ([0-9a-f]+)$' } | Select-Object -First 1
            if (-not $line -or $line -notmatch '^Signer #1 certificate SHA-256 digest: ([0-9a-f]+)$') { throw 'Unable to read APK signer' }
            $record.signerSha256 = $Matches[1]
        } finally { $env:JAVA_HOME = $oldJava }
    }
    $wrapper = if ($Platform -eq 'windows') { 'gradle/wrapper/gradle-wrapper.properties' } else { 'android-app/gradle/wrapper/gradle-wrapper.properties' }
    $wrapperText = Get-Content -LiteralPath (Join-Path $Root $wrapper) -Raw
    if ($wrapperText -notmatch 'gradle-([\d.]+)-bin.zip') { throw 'Invalid wrapper version' }
    $record.gradle = $Matches[1]
    $javaPath = if ($Platform -eq 'windows' -and (Test-Path 'C:/jdk-25.0.2/bin/java.exe')) { 'C:/jdk-25.0.2/bin/java.exe' } elseif ($Platform -ne 'windows' -and (Test-Path (Join-Path $Root '.tools/jdk-17-android/bin/java.exe'))) { Join-Path $Root '.tools/jdk-17-android/bin/java.exe' } elseif ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    $javaVersion = & $javaPath -version 2>&1
    $record.jdk = ($javaVersion | Select-Object -First 1).ToString()
    $record | ConvertTo-Json | Set-Content -LiteralPath ($Artifact + '.build.json') -Encoding UTF8
}


function Assert-ZaArtifactRecord([string] $Artifact, [string] $Platform, [string] $Commit, [string] $Version) {
    $record = Get-Content -LiteralPath ($Artifact + '.build.json') -Raw | ConvertFrom-Json
    if ($record.sourceDirty -ne $false -or $record.sourceCommit -ne $Commit -or $record.version -ne $Version -or $record.platform -ne $Platform) { throw "Artifact was built from different/dirty source: $Artifact" }
    if ($record.sha256 -ne (Get-FileHash -LiteralPath $Artifact).Hash.ToLowerInvariant()) { throw "Artifact hash mismatch: $Artifact" }
    if ($Platform -eq 'android-debug' -and ($record.applicationId -ne 'app.musicplayer.android' -or $record.signerSha256 -ne '1cd53ceaeef7ce7772d274b450982deeb34424d896612a66a95d9ee00dd2e3ff')) { throw 'Android channel signer/package changed; upgrade would be incompatible' }
    if ($Platform -eq 'android-release') {
        if ($record.qa -ne $false) { throw 'QA APK cannot be published' }
        $root = Split-Path -Parent $PSScriptRoot
        $factsText = & python (Join-Path $root 'scripts/android-signing.py') verify --apk $Artifact
        if ($LASTEXITCODE) { throw 'Android binary signing/manifest verification failed' }
        $facts = $factsText | ConvertFrom-Json
        if ($record.versionCode -ne $facts.versionCode -or $record.signing.applicationId -ne $facts.applicationId -or $record.signing.lineageSha256 -ne $facts.lineageSha256) { throw 'Android build record differs from binary facts' }
        foreach ($api in @('28','31','32','33','35','36')) {
            if ($record.signing.apiSigners.$api -ne $facts.apiSigners.$api) { throw "Android signer record differs for API $api" }
        }
    }
    return $record
}
