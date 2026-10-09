param([string] $LivePlaylistLink)
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'build-common.ps1')
Invoke-ZaGradle $root '.' @('test',':shared:test','installDist')
$jdk = if (Test-Path 'C:/jdk-25.0.2/bin/java.exe') { 'C:/jdk-25.0.2' } else { $env:JAVA_HOME }
if (-not $jdk) { throw 'JAVA_HOME must point to JDK 25' }
$work = Join-Path $root ('.tools/playlist-smoke-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$libs = Join-Path $root 'build/install/simple-music-player/lib/*'
& (Join-Path $jdk 'bin/javac.exe') -encoding UTF-8 -cp $libs -d $work (Join-Path $PSScriptRoot 'DesktopSmoke.java') (Join-Path $PSScriptRoot 'OnlineSmokeFixtures.java') (Join-Path $PSScriptRoot 'PlaylistDesktopSmoke.java') (Join-Path $PSScriptRoot 'PlaylistActionFixtures.java') (Join-Path $PSScriptRoot 'PlaylistControlsSmoke.java')
if ($LASTEXITCODE) { throw 'Playlist smoke harness compilation failed' }
$qaPreferences = '/app/musicplayer/qa/' + (Split-Path -Leaf $work)
& (Join-Path $jdk 'bin/java.exe') '--enable-native-access=ALL-UNNAMED' "-Dmusicplayer.data-dir=$work/data" "-Dmusicplayer.preferences-node=$qaPreferences" -cp "$libs;$work" PlaylistDesktopSmoke
if ($LASTEXITCODE) { throw 'Playlist desktop smoke failed' }
& (Join-Path $jdk 'bin/java.exe') '--enable-native-access=ALL-UNNAMED' "-Dmusicplayer.data-dir=$work/controls" "-Dmusicplayer.preferences-node=$qaPreferences/controls" "-Dmusicplayer.qa.playlist-link=$LivePlaylistLink" -cp "$libs;$work" PlaylistControlsSmoke
if ($LASTEXITCODE) { throw 'Playlist controls smoke failed' }
