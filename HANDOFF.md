# 项目交接：简约音乐播放器 3.2.2

## 当前状态

- Java 25 + JavaFX 25.0.1 + Gradle 9.6.1 + SQLite。
- 主入口：`app.musicplayer.MusicPlayerLauncher`。
- 应用控制器：`app.musicplayer.MusicPlayerApp`。
- 版本：`3.2.2`。
- 数据统一位于程序目录的 `downloads/`。
- 数据库 schema 保持兼容：`tracks`、`lyrics`。

## 主要模块

- `config.AppPaths`：运行目录解析、缓存目录创建、旧数据库复制迁移。
- `config.SqliteNativeTemp`：为每次启动隔离 SQLite JDBC 原生 DLL，避免 Windows 临时目录清理冲突。
- `playlist.TrackLibraryService`：音频扫描、去重、搜索、排序。
- `playback.AudioFileInspector`：按文件头识别 MP3/MP4/原始 AAC。
- `playback.PlaybackFileResolver`：把扩展名错误的 MP3 下载文件映射为缓存 `.mp3`。
- `artwork.ArtworkService`：封面下载和缓存，使用可关闭的专用线程池。
- `lyrics.LyricsService`：数据库、本地文件、缓存文件和在线 provider 编排。
- `online.MusicCrawler`：多来源编排、下载校验和跨来源回退。
- `online.*SourceProvider`：QQMP3、网易云、QQ、酷狗的独立适配器。
- `ui.PlaylistPane`：左侧播放列表。
- `ui.OnlineDrawer`：右侧在线搜索抽屉和分隔位置持久化。
- `ui.PlaybackControls`：底部播放、进度和音量控件。

## 验证命令

```powershell
.\verify.ps1
```

不要直接在中文路径下执行 `gradle test`：当前 Gradle/JDK 组合生成的测试 worker 参数文件会错误编码中文 classpath。`verify.ps1` 已通过临时盘符解决该问题。

## 打包命令

```powershell
.\package.ps1
```

脚本每次使用版本号和时间戳创建新的输出目录，不会删除旧安装包。

## 协作约束

- 修改或删除文件前先征得用户允许。
- 不删除 `downloads/` 及其中用户数据。
- 在线来源可能随网站接口调整而失效，构建测试不得依赖实时网站可用性。
