# 验证入口。根目录版本检查桌面、共享测试及构建发布门禁；Android 版本检查共享 Java 17 测试、APK 构建及 lint。

param([switch] $SkipClean)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
. (Join-Path $root 'scripts/build-common.ps1')
$tasks = if ($SkipClean) {
    @('test', ':shared:test', 'installDist')
} else {
    @('clean', ':shared:clean', 'test', ':shared:test', 'installDist')
}
Invoke-ZaGradle $root '.' $tasks
& (Join-Path $root 'scripts/test-build-tools.ps1')
& (Join-Path $root 'scripts/test-release-state.ps1')
Write-Host 'Verification passed: desktop and shared tests; installDist.'
