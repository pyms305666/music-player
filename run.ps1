# BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
# 说明：声明脚本参数，允许运行脚本时传入 Gradle 任务名。
param(
    # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
    [Parameter(ValueFromRemainingArguments = $true)]
    # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
    [string[]] $GradleArgs
# 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
)

# 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
$ErrorActionPreference = "Stop"

# 说明：条件判断：满足条件时执行大括号里的脚本。
if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    # 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
    $GradleArgs = @("run")
# 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
}
# 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
# 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
$gradleVersion = "9.6.1"
# 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
$toolsDir = Join-Path $projectRoot ".tools"
# 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
$gradleHome = Join-Path $toolsDir "gradle-$gradleVersion"
# 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
$gradleBin = Join-Path $gradleHome "bin\gradle.bat"
# 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
$gradleZip = Join-Path $toolsDir "gradle-$gradleVersion-bin.zip"
# 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
$gradleTempZip = "$gradleZip.download"
# 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"

# 说明：条件判断：满足条件时执行大括号里的脚本。
if (-not (Test-Path $gradleBin)) {
    # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

    # 说明：条件判断：满足条件时执行大括号里的脚本。
    if (Test-Path $gradleZip) {
        # 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
        $zipItem = Get-Item $gradleZip
        # 说明：条件判断：满足条件时执行大括号里的脚本。
        if ($zipItem.Length -eq 0) {
            # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
            Remove-Item -LiteralPath $gradleZip -Force
        # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
        } else {
            # 说明：异常处理：尝试执行命令，失败时进入 catch 处理。
            try {
                # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
                Add-Type -AssemblyName System.IO.Compression.FileSystem
                # 说明：定义或更新 PowerShell 变量，后续命令会复用这个值。
                $archive = [System.IO.Compression.ZipFile]::OpenRead($gradleZip)
                # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
                $archive.Dispose()
            # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
            } catch {
                # 说明：在终端输出提示信息，方便用户知道脚本正在做什么。
                Write-Host "Detected an incomplete Gradle archive. Downloading again..."
                # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
                Remove-Item -LiteralPath $gradleZip -Force
            # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
            }
        # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
        }
    # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
    }

    # 说明：条件判断：满足条件时执行大括号里的脚本。
    if (-not (Test-Path $gradleZip)) {
        # 说明：在终端输出提示信息，方便用户知道脚本正在做什么。
        Write-Host "Downloading Gradle $gradleVersion..."
        # 说明：条件判断：满足条件时执行大括号里的脚本。
        if (Test-Path $gradleTempZip) {
            # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
            Remove-Item -LiteralPath $gradleTempZip -Force
        # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
        }
        # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
        Invoke-WebRequest -Uri $gradleUrl -OutFile $gradleTempZip
        # 说明：条件判断：满足条件时执行大括号里的脚本。
        if ((Get-Item $gradleTempZip).Length -eq 0) {
            # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
            throw "Gradle download failed: archive is empty."
        # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
        }
        # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
        Move-Item -LiteralPath $gradleTempZip -Destination $gradleZip -Force
    # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
    }

    # 说明：在终端输出提示信息，方便用户知道脚本正在做什么。
    Write-Host "Extracting Gradle..."
    # 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
    Expand-Archive -Path $gradleZip -DestinationPath $toolsDir -Force
# 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
}

# 说明：这是一条 PowerShell 脚本命令，用来准备或运行项目。
Set-Location $projectRoot
# 说明：调用外部命令或脚本，这里用于执行 Gradle。
& $gradleBin @GradleArgs
