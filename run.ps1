param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$ErrorActionPreference = "Stop"

if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @("run")
}
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleVersion = "9.6.1"
$toolsDir = Join-Path $projectRoot ".tools"
$gradleHome = Join-Path $toolsDir "gradle-$gradleVersion"
$gradleBin = Join-Path $gradleHome "bin\gradle.bat"
$gradleZip = Join-Path $toolsDir "gradle-$gradleVersion-bin.zip"
$gradleTempZip = "$gradleZip.download"
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"

if (-not (Test-Path $gradleBin)) {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

    if (Test-Path $gradleZip) {
        $zipItem = Get-Item $gradleZip
        if ($zipItem.Length -eq 0) {
            Remove-Item -LiteralPath $gradleZip -Force
        } else {
            try {
                Add-Type -AssemblyName System.IO.Compression.FileSystem
                $archive = [System.IO.Compression.ZipFile]::OpenRead($gradleZip)
                $archive.Dispose()
            } catch {
                Write-Host "Detected an incomplete Gradle archive. Downloading again..."
                Remove-Item -LiteralPath $gradleZip -Force
            }
        }
    }

    if (-not (Test-Path $gradleZip)) {
        Write-Host "Downloading Gradle $gradleVersion..."
        if (Test-Path $gradleTempZip) {
            Remove-Item -LiteralPath $gradleTempZip -Force
        }
        Invoke-WebRequest -Uri $gradleUrl -OutFile $gradleTempZip
        if ((Get-Item $gradleTempZip).Length -eq 0) {
            throw "Gradle download failed: archive is empty."
        }
        Move-Item -LiteralPath $gradleTempZip -Destination $gradleZip -Force
    }

    Write-Host "Extracting Gradle..."
    Expand-Archive -Path $gradleZip -DestinationPath $toolsDir -Force
}

Set-Location $projectRoot
& $gradleBin @GradleArgs
