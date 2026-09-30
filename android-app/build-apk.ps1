param(
    [switch] $Clean
)

$ErrorActionPreference = "Stop"

$androidRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $androidRoot
. (Join-Path $projectRoot 'scripts/build-common.ps1')
$sourceSnapshot = Get-ZaSourceSnapshot $projectRoot
$version = (Get-ZaVersion $projectRoot).versionName
$toolsRoot = Join-Path $projectRoot ".tools"
$sdkRoot = Join-Path $toolsRoot "android-sdk"
$commandLineHome = Join-Path $sdkRoot "cmdline-tools\latest"
$sdkManager = Join-Path $commandLineHome "bin\sdkmanager.bat"
$archive = Join-Path $toolsRoot "android-commandlinetools.zip"
$extractRoot = Join-Path $toolsRoot "android-commandlinetools-extract"
$commandLineUrl = "https://dl.google.com/android/repository/commandlinetools-win-15641748_latest.zip"
$jdkHome = Join-Path $toolsRoot "jdk-17-android"
$jdkBin = Join-Path $jdkHome "bin\java.exe"
$jdkArchive = Join-Path $toolsRoot "jdk-17-android.zip"
$jdkExtractRoot = Join-Path $toolsRoot "jdk-17-android-extract"
$jdkUrl = "https://api.adoptium.net/v3/binary/version/jdk-17.0.19%2B10/windows/x64/jdk/hotspot/normal/eclipse"

New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null

if (-not (Test-Path $sdkManager)) {
    if (-not (Test-Path $archive)) {
        Write-Host "Downloading Android command-line tools..."
        Invoke-WebRequest -UseBasicParsing -Uri $commandLineUrl -OutFile $archive
    }

    $resolvedExtract = [System.IO.Path]::GetFullPath($extractRoot)
    $resolvedTools = [System.IO.Path]::GetFullPath($toolsRoot)
    if (-not $resolvedExtract.StartsWith($resolvedTools.TrimEnd('\') + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Invalid Android tools extraction path: $resolvedExtract"
    }
    if (Test-Path $extractRoot) {
        Remove-Item -LiteralPath $extractRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
    Expand-Archive -LiteralPath $archive -DestinationPath $extractRoot -Force
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $commandLineHome) | Out-Null
    Move-Item -LiteralPath (Join-Path $extractRoot "cmdline-tools") -Destination $commandLineHome
    Remove-Item -LiteralPath $extractRoot -Recurse -Force
}

if (-not (Test-Path $jdkBin)) {
    if (-not (Test-Path $jdkArchive)) {
        Write-Host "Downloading JDK 17 for Android builds..."
        Invoke-WebRequest -UseBasicParsing -Uri $jdkUrl -OutFile $jdkArchive -TimeoutSec 900
    }
    $resolvedJdkExtract = [IO.Path]::GetFullPath($jdkExtractRoot)
    $toolsBoundary = [IO.Path]::GetFullPath($toolsRoot).TrimEnd('\') + '\'
    if (-not $resolvedJdkExtract.StartsWith($toolsBoundary, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid JDK extraction path' }
    if (Test-Path $jdkExtractRoot) {
        Remove-Item -LiteralPath $jdkExtractRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $jdkExtractRoot | Out-Null
    Expand-Archive -LiteralPath $jdkArchive -DestinationPath $jdkExtractRoot -Force
    $extractedJdk = Get-ChildItem -LiteralPath $jdkExtractRoot -Directory | Select-Object -First 1
    if ($null -eq $extractedJdk) {
        throw "JDK 17 archive did not contain a JDK directory."
    }
    Move-Item -LiteralPath $extractedJdk.FullName -Destination $jdkHome
    Remove-Item -LiteralPath $jdkExtractRoot -Recurse -Force
}

$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome "bin") + ";" + $env:Path

# SDK command-line tools also require an ASCII path on Windows.
if (-not (Test-Path (Join-Path $sdkRoot 'platforms/android-35/android.jar')) -or -not (Test-Path (Join-Path $sdkRoot 'build-tools/35.0.0/apksigner.bat')) -or -not (Test-Path (Join-Path $sdkRoot 'platform-tools/adb.exe'))) {
    $used = @(Get-PSDrive -PSProvider FileSystem | ForEach-Object { $_.Name.ToUpperInvariant() })
    $letter = @('M','N','P','Q','R','S','T','U','V','W','X','Y','Z') | Where-Object { $used -notcontains $_ } | Select-Object -First 1
    if (-not $letter) { throw 'No free drive letter for SDK setup' }
    $drive = $letter + ':'
    & subst $drive $projectRoot
    if ($LASTEXITCODE) { throw 'SDK path mapping failed' }
    try {
        $mappedSdk = Join-Path ($drive + '\') '.tools/android-sdk'
        $manager = Join-Path $mappedSdk 'cmdline-tools/latest/bin/sdkmanager.bat'
        $licenseAnswers = (1..30 | ForEach-Object { 'y' }) -join "`n"
        $licenseAnswers | & $manager "--sdk_root=$mappedSdk" --licenses | Out-Host
        if ($LASTEXITCODE) { throw 'Android license setup failed' }
        & $manager "--sdk_root=$mappedSdk" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
        if ($LASTEXITCODE) { throw 'Android SDK package installation failed' }
    } finally { & subst $drive /d }
}

$tasks = if ($Clean) { @('clean', ':shared:clean', ':shared:test', 'assembleDebug') } else { @(':shared:test', 'assembleDebug') }
Invoke-ZaGradle $projectRoot 'android-app' $tasks

$apk = Join-Path $androidRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    throw "APK was not generated: $apk"
}
$distDir = Join-Path $androidRoot "dist"
$distApk = Join-Path $distDir ((-join @([char]90, [char]65, [char]38899, [char]20048)) + "-Android-$version-debug.apk")
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
Copy-Item -LiteralPath $apk -Destination $distApk -Force
Write-Host "APK: $distApk"

Write-ZaBuildRecord $projectRoot $distApk "android-debug" $sourceSnapshot
