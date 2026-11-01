# 桌面开发运行入口。调用 build-common 的 Gradle 启动辅助，处理中文路径及 Java 环境后执行应用运行任务。

param([Parameter(ValueFromRemainingArguments = $true)][string[]] $GradleArgs)
. (Join-Path $PSScriptRoot 'scripts/build-common.ps1')
if (-not $GradleArgs) {
    $GradleArgs = @('run')
}
Invoke-ZaGradle $PSScriptRoot '.' $GradleArgs
