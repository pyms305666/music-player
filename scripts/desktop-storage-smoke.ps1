# 桌面目录设置与启动迁移烟雾。所有歌曲、数据库和偏好设置都使用独立夹具。
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'build-common.ps1')
Invoke-ZaGradle $root '.' @('installDist')
$jdk = if (Test-Path 'C:/jdk-25.0.2/bin/java.exe') {
    'C:/jdk-25.0.2'
} else {
    $env:JAVA_HOME
}
if (-not $jdk) {
    throw 'JAVA_HOME must point to JDK 25'
}
$work = Join-Path $root ('.tools/storage-smoke-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$libs = Join-Path $root 'build/install/simple-music-player/lib/*'
& (Join-Path $jdk 'bin/javac.exe') -encoding UTF-8 -cp $libs -d $work (Join-Path $PSScriptRoot 'DesktopStorageSmoke.java')
if ($LASTEXITCODE) {
    throw 'Storage smoke compilation failed'
}
$qaPreferences = '/app/musicplayer/qa/storage/' + (Split-Path -Leaf $work)
& (Join-Path $jdk 'bin/java.exe') '--enable-native-access=ALL-UNNAMED' "-Dmusicplayer.data-dir=$work/data" "-Dmusicplayer.preferences-node=$qaPreferences" -cp "$libs;$work" DesktopStorageSmoke
if ($LASTEXITCODE) {
    throw 'Storage smoke failed'
}
# 原进程完整退出后再启动第二个 JVM，真实验证 Windows 播放句柄释放和持久化恢复。
& (Join-Path $jdk 'bin/java.exe') '--enable-native-access=ALL-UNNAMED' "-Dmusicplayer.data-dir=$work/data" "-Dmusicplayer.preferences-node=$qaPreferences" -cp "$libs;$work" DesktopStorageSmoke resume
if ($LASTEXITCODE) {
    throw 'Storage restart smoke failed'
}
