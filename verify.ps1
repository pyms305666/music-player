param(
    [switch] $SkipClean
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradle = Join-Path $projectRoot ".tools\gradle-9.6.1\bin\gradle.bat"

if (-not (Test-Path $gradle)) {
    throw "Project Gradle was not found: $gradle. Run .\run.ps1 first."
}

$mappedDrive = $null
$workingDirectory = $projectRoot

try {
    # Gradle and the JDK can corrupt test classpaths when the Windows project path is non-ASCII.
    # A temporary drive mapping avoids the problem without moving or copying project files.
    if ($projectRoot -match '[^\x00-\x7F]') {
        $usedDrives = @(Get-PSDrive -PSProvider FileSystem | ForEach-Object { $_.Name.ToUpperInvariant() })
        $driveLetter = @('M', 'N', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z') |
                Where-Object { $usedDrives -notcontains $_ } |
                Select-Object -First 1
        if (-not $driveLetter) {
            throw "No free drive letter is available for the temporary test mapping."
        }
        $mappedDrive = $driveLetter + ':'
        & subst $mappedDrive $projectRoot
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to create temporary drive mapping $mappedDrive."
        }
        $workingDirectory = $mappedDrive + '\'
    }

    Push-Location $workingDirectory
    try {
        $tasks = if ($SkipClean) { @('test', 'installDist') } else { @('clean', 'test', 'installDist') }
        # Keep Gradle's lock files on the real path; locks created through subst can fail to delete on Windows.
        $projectCache = Join-Path $projectRoot ".tools\gradle-project-cache-absolute"
        & $gradle @tasks '--no-daemon' '--project-cache-dir' $projectCache '--console=plain'
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle verification failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
} finally {
    if ($mappedDrive) {
        & subst $mappedDrive /d
    }
}

Write-Host "Verification passed: tests and installDist completed."
