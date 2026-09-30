param(
    [string] $Version
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $projectRoot 'scripts/build-common.ps1')
$sourceSnapshot = Get-ZaSourceSnapshot $projectRoot
$canonicalVersion = (Get-ZaVersion $projectRoot).versionName
if ($Version -and $Version -ne $canonicalVersion) { throw 'Package version must match version.properties' }
$Version = $canonicalVersion
$appName = -join @([char]90, [char]65, [char]38899, [char]20048)
# Keep the renamed app separate from the old MSI, whose uninstaller removes its install directory.
$upgradeUuid = "c75b70d9-e17e-4b11-81b3-64f1ba87c760"

& (Join-Path $projectRoot "verify.ps1") -SkipClean
if ($LASTEXITCODE -ne 0) {
    throw "Project verification failed. Packaging stopped."
}

# jpackage exe packaging needs the matching WiX generation; this project builds with JDK 25 + WiX 5.
$projectJpackage = "C:\jdk-25.0.2\bin\jpackage.exe"
if (Test-Path $projectJpackage) {
    $jpackage = $projectJpackage
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/jpackage.exe'))) {
    $jpackage = Join-Path $env:JAVA_HOME 'bin/jpackage.exe'
} else {
    $jpackage = (Get-Command jpackage.exe -ErrorAction SilentlyContinue).Source
}
if (-not $jpackage) { throw "jpackage.exe was not found; install JDK 25.0.2" }
$jpackageVersion = (& $jpackage --version | Out-String).Trim()
if ($LASTEXITCODE -or $jpackageVersion -notmatch '^25[.]') { throw "Windows packaging requires JDK 25; selected jpackage is $jpackageVersion" }

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
$iconPath = Join-Path $projectRoot "packaging\icons\simple-music-player.ico"
if (-not (Test-Path -LiteralPath $iconPath)) {
    throw "Windows application icon was not found: $iconPath"
}

# UTF-8 arguments preserve the name; a fixed Chinese locale selects MSI codepage 936.
function Invoke-JpackageOptions([string[]] $Options, [string] $OptionFile) {
    $lines = $Options | ForEach-Object { '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"' }
    [IO.File]::WriteAllLines($OptionFile, [string[]]$lines, [Text.UTF8Encoding]::new($false))
    & $jpackage '-J-Dfile.encoding=UTF-8' '-J-Duser.language=zh' '-J-Duser.country=CN' "@$OptionFile"
    if ($LASTEXITCODE) { throw "jpackage failed with exit code $LASTEXITCODE" }
}
Invoke-JpackageOptions @(
    '--type', 'app-image', '--dest', $destination, '--name', $appName,
    '--app-version', $Version, '--vendor', 'pyms305666', '--input', $inputDirectory,
    '--main-jar', $mainJar, '--main-class', 'app.musicplayer.MusicPlayerLauncher', '--icon', $iconPath,
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--java-options', '--enable-native-access=javafx.graphics',
    '--java-options', '--enable-native-access=javafx.media', '--java-options', '-Dfile.encoding=UTF-8'
) (Join-Path $destination 'app-image.args')
if ($LASTEXITCODE -ne 0) {
    throw "Application image creation failed with exit code $LASTEXITCODE."
}

Invoke-JpackageOptions @(
    '--type', 'exe', '--dest', $destination, '--app-image', $appImage, '--name', $appName,
    '--app-version', $Version, '--vendor', 'pyms305666', '--win-menu', '--win-shortcut',
    '--win-per-user-install', '--install-dir', 'ZA-Music', '--win-upgrade-uuid', $upgradeUuid
) (Join-Path $destination 'installer.args')
if ($LASTEXITCODE -ne 0) {
    throw "Installer creation failed with exit code $LASTEXITCODE."
}

$installer = Get-ChildItem -LiteralPath $destination -Filter "*.exe" -File | Select-Object -First 1
Write-Host "Installer created: $($installer.FullName)"

Write-ZaBuildRecord $projectRoot $installer.FullName "windows" $sourceSnapshot
