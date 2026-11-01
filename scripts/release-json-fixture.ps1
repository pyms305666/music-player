# 发布状态测试的 JSON/CLI 夹具。为 release-state 的正反向回归提供可控响应，不依赖真实发布。

# Native process fixture: emit GitHub-style UTF-8 JSON without PowerShell formatting.
. (Join-Path $PSScriptRoot 'release-state.ps1')
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
[Console]::Write((@{name = (Get-ZaReleaseTitle '4.1.12') } | ConvertTo-Json -Compress))
