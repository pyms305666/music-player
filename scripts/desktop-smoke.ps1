$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'build-common.ps1')
Invoke-ZaGradle $root '.' @('installDist')
$jdk = if (Test-Path 'C:/jdk-25.0.2/bin/java.exe') { 'C:/jdk-25.0.2' } else { $env:JAVA_HOME }
if (-not $jdk) { throw 'JAVA_HOME must point to JDK 25' }
$work = Join-Path $root ('.tools/smoke-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$libs = Join-Path $root 'build/install/simple-music-player/lib/*'
& (Join-Path $jdk 'bin/javac.exe') -cp $libs -d $work (Join-Path $PSScriptRoot 'DesktopSmoke.java') (Join-Path $PSScriptRoot 'OnlineSmokeFixtures.java')
if ($LASTEXITCODE) { throw 'Smoke harness compilation failed' }
$qaPreferences = '/app/musicplayer/qa/' + (Split-Path -Leaf $work)
& (Join-Path $jdk 'bin/java.exe') '--enable-native-access=ALL-UNNAMED' "-Dmusicplayer.data-dir=$work/data" "-Dmusicplayer.preferences-node=$qaPreferences" -cp "$libs;$work" DesktopSmoke
if ($LASTEXITCODE) { throw 'Desktop smoke failed' }
