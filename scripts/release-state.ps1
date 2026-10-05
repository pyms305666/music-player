# Works in Windows PowerShell 5.1 and PowerShell 7. Array filtering must happen
# after ConvertFrom-Json, which enumerates arrays differently between runtimes.
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
function Get-ZaReleaseTitle([string] $Version) {
    return 'ZA' + [char]0x97f3 + [char]0x4e50 + ' ' + $Version
}
function Get-ZaDraftRelease($Releases, [string] $Version, [string] $SourceCommit) {
    $releaseMatches = @($Releases | Where-Object { $_.tag_name -eq $Version })
    if ($releaseMatches.Count -ne 1 -or -not $releaseMatches[0].draft -or $releaseMatches[0].target_commitish -ne $SourceCommit) {
        throw 'Expected unique draft at the artifact source commit'
    }
    return $releaseMatches[0]
}
