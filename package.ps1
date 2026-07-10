param(
    [string] $Version = "3.2.0"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$appName = -join @([char]31616, [char]32422, [char]38899, [char]20048, [char]25773, [char]25918, [char]22120)

& (Join-Path $projectRoot "verify.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "Project verification failed. Packaging stopped."
}

$jpackage = (Get-Command jpackage.exe -ErrorAction SilentlyContinue).Source
if (-not $jpackage) {
    $fallback = "C:\jdk-25.0.2\bin\jpackage.exe"
    if (Test-Path $fallback) {
        $jpackage = $fallback
    } else {
        throw "jpackage.exe was not found. Install a JDK that includes jpackage."
    }
}

$wixDirectory = Join-Path $projectRoot ".tools\wix"
if (-not (Test-Path (Join-Path $wixDirectory "wix.exe"))) {
    throw "Local WiX was not found. Install wix 5.0.2 into .tools\wix first."
}
$env:PATH = $wixDirectory + ';' + $env:PATH

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$destination = Join-Path $projectRoot "build\installer\$Version-$timestamp"
New-Item -ItemType Directory -Force -Path $destination | Out-Null

$inputDirectory = Join-Path $projectRoot "build\install\simple-music-player\lib"
$mainJar = "simple-music-player-$Version.jar"
$appImage = Join-Path $destination $appName

& $jpackage `
    --type app-image `
    --dest $destination `
    --name $appName `
    --app-version $Version `
    --vendor "pyms305666" `
    --input $inputDirectory `
    --main-jar $mainJar `
    --main-class "app.musicplayer.MusicPlayerLauncher" `
    --java-options "--enable-native-access=ALL-UNNAMED" `
    --java-options "--enable-native-access=javafx.graphics" `
    --java-options "--enable-native-access=javafx.media" `
    --java-options "-Dfile.encoding=UTF-8"
if ($LASTEXITCODE -ne 0) {
    throw "Application image creation failed with exit code $LASTEXITCODE."
}

& $jpackage `
    --type exe `
    --dest $destination `
    --app-image $appImage `
    --name $appName `
    --app-version $Version `
    --vendor "pyms305666" `
    --win-menu `
    --win-shortcut `
    --win-per-user-install
if ($LASTEXITCODE -ne 0) {
    throw "Installer creation failed with exit code $LASTEXITCODE."
}

$installer = Get-ChildItem -LiteralPath $destination -Filter "*.exe" -File | Select-Object -First 1
Write-Host "Installer created: $($installer.FullName)"
