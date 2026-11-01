# GitHub Release 状态检查函数。解析 CLI JSON、匹配版本和附件，拒绝重复、缺失或不符合要求的发布状态。

# Works in Windows PowerShell 5.1 and PowerShell 7. Array filtering must happen
# after ConvertFrom-Json, which enumerates arrays differently between runtimes.
# 临时使用 UTF-8 执行 Release 查询，结束时恢复控制台及输出编码。
function Invoke-ZaReleaseUtf8([scriptblock] $Work) {
    $previousConsole = [Console]::OutputEncoding
    $previousPipeline = $OutputEncoding
    try {
        [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
        $OutputEncoding = [Text.UTF8Encoding]::new($false)
        & $Work
    } finally {
        [Console]::OutputEncoding = $previousConsole
        $OutputEncoding = $previousPipeline
    }
}
# 用标准中文标题与版本拼接 Release 名称。
function Get-ZaReleaseTitle([string] $Version) {
    return 'ZA' + [char]0x97f3 + [char]0x4e50 + ' ' + $Version
}
# 读取指定仓库的草稿 Release 状态，供发布流程和测试夹具复用。
function Get-ZaDraftRelease($Releases, [string] $Version, [string] $SourceCommit) {
    $releaseMatches = @($Releases | Where-Object { $_.tag_name -eq $Version })
    if ($releaseMatches.Count -ne 1 -or -not $releaseMatches[0].draft -or $releaseMatches[0].target_commitish -ne $SourceCommit) {
        throw 'Expected unique draft at the artifact source commit'
    }
    return $releaseMatches[0]
}
