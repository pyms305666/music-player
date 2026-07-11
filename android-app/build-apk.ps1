param(
    [switch] $Clean
)

$ErrorActionPreference = "Stop"

$androidRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $androidRoot
$toolsRoot = Join-Path $projectRoot ".tools"
$sdkRoot = Join-Path $toolsRoot "android-sdk"
$commandLineHome = Join-Path $sdkRoot "cmdline-tools\latest"
$sdkManager = Join-Path $commandLineHome "bin\sdkmanager.bat"
$archive = Join-Path $toolsRoot "android-commandlinetools.zip"
$extractRoot = Join-Path $toolsRoot "android-commandlinetools-extract"
$commandLineUrl = "https://dl.google.com/android/repository/commandlinetools-win-15641748_latest.zip"
$gradleVersion = "8.11.1"
$gradleHome = Join-Path $toolsRoot "gradle-$gradleVersion"
$gradleBin = Join-Path $gradleHome "bin\gradle.bat"
$gradleArchive = Join-Path $toolsRoot "gradle-$gradleVersion-bin.zip"
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$jdkHome = Join-Path $toolsRoot "jdk-17-android"
$jdkBin = Join-Path $jdkHome "bin\java.exe"
$jdkArchive = Join-Path $toolsRoot "jdk-17-android.zip"
$jdkExtractRoot = Join-Path $toolsRoot "jdk-17-android-extract"
$jdkUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"

New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null

if (-not (Test-Path $sdkManager)) {
    if (-not (Test-Path $archive)) {
        Write-Host "Downloading Android command-line tools..."
        Invoke-WebRequest -UseBasicParsing -Uri $commandLineUrl -OutFile $archive
    }

    $resolvedExtract = [System.IO.Path]::GetFullPath($extractRoot)
    $resolvedTools = [System.IO.Path]::GetFullPath($toolsRoot)
    if (-not $resolvedExtract.StartsWith($resolvedTools, [System.StringComparison]::OrdinalIgnoreCase)) {
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

$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$licenseAnswers = (1..30 | ForEach-Object { "y" }) -join "`n"
$licenseAnswers | & $sdkManager --sdk_root=$sdkRoot --licenses | Out-Host
& $sdkManager --sdk_root=$sdkRoot "platform-tools" "platforms;android-35" "build-tools;35.0.0"
if ($LASTEXITCODE -ne 0) {
    throw "Android SDK package installation failed."
}

if (-not (Test-Path $gradleBin)) {
    if (-not (Test-Path $gradleArchive)) {
        Write-Host "Downloading Gradle $gradleVersion..."
        Invoke-WebRequest -UseBasicParsing -Uri $gradleUrl -OutFile $gradleArchive -TimeoutSec 600
    }
    Expand-Archive -LiteralPath $gradleArchive -DestinationPath $toolsRoot -Force
}

if (-not (Test-Path $jdkBin)) {
    if (-not (Test-Path $jdkArchive)) {
        Write-Host "Downloading JDK 17 for Android builds..."
        Invoke-WebRequest -UseBasicParsing -Uri $jdkUrl -OutFile $jdkArchive -TimeoutSec 900
    }
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

$mappedRoot = "M:\"
$mappedAndroid = "M:\android-app"
& subst M: $projectRoot
if ($LASTEXITCODE -ne 0) {
    throw "Unable to create the temporary M: project mapping."
}
$mappedSdk = "M:\.tools\android-sdk"
$env:ANDROID_HOME = $mappedSdk
$env:ANDROID_SDK_ROOT = $mappedSdk
$escapedMappedSdk = $mappedSdk.Replace("\", "\\").Replace(":", "\:")
Set-Content -LiteralPath (Join-Path $androidRoot "local.properties") -Encoding ASCII -Value "sdk.dir=$escapedMappedSdk"

Push-Location $mappedAndroid
try {
    if (-not (Test-Path ".\gradlew.bat")) {
        & $gradleBin -p $mappedAndroid wrapper --gradle-version $gradleVersion --distribution-type bin
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to create the Android Gradle wrapper."
        }
    }

    [string[]] $tasks = if ($Clean) { @("clean", "assembleDebug") } else { @("assembleDebug") }
    & $gradleBin @tasks --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
    & subst M: /d
}

$apk = Join-Path $androidRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    throw "APK was not generated: $apk"
}
$distDir = Join-Path $androidRoot "dist"
$distApk = Join-Path $distDir "simple-music-player-3.2.2-debug.apk"
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
Copy-Item -LiteralPath $apk -Destination $distApk -Force
Write-Host "APK: $distApk"
