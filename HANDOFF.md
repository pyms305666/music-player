# 项目交接：简约音乐播放器 4.1.3

## 当前状态

- Java 25 + JavaFX 25.0.1 + Gradle 9.6.1 + SQLite。
- 主入口：`app.musicplayer.MusicPlayerLauncher`。
- 应用控制器：`app.musicplayer.MusicPlayerApp`。
- 版本：`4.1.3`。
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
- `online.MusicCrawler`：多来源编排、下载校验和跨来源回退；并行搜索（单来源 10s 超时）、来源级熔断（连续失败 3 次暂停 1/5/15 分钟）、解析结果缓存（成功 4 分钟 / 失败 45 秒）、受限歌曲下载时自动换源。
- `online.*SourceProvider`：酷狗、酷我、咪咕、QQ、网易云的独立适配器。QQMP3（qqmp3.vip）已于 2026-09 实测失效并移除。
- 酷我：`search.kuwo.cn/r.s` 搜索（单引号伪 JSON，条目含嵌套花括号，需深度扫描解析）+ `mobi.kuwo.cn` 车载播放器接口解析（免登录，VIP 歌曲返回完整 320k/FLAC）。
- 咪咕：`pd.musicapp.migu.cn` 搜索 + `app.c.nf.migu.cn` listen-url 解析；必须用移动端 UA，桌面 UA 会拿到加密流变体；偶发 `ftp://218.200.160.122:21/` 形式地址需改写到 `https://freetyst.nf.migu.cn/` 并对中文路径做百分号编码。
- `ui.PlaylistPane`：左侧播放列表。
- `ui.OnlineDrawer`：右侧在线搜索抽屉和分隔位置持久化。
- `ui.PlaybackControls`：底部播放、进度和音量控件。
- `ui.MobileViewSwitcher`：9:16 移动端的歌单、歌词和在线搜索底部导航。
- `config.LayoutMode`：通过 `--mobile` 或 `musicplayer.mobile` 系统属性选择移动布局。
- `lyrics.OnlineLyricsProvider` + 四个歌词渠道（网易云/QQ/酷狗/LRCLIB）：双端共享，HTTP 通过 `LyricsHttp` 接口注入——桌面用 java.net.http，Android 用共享的 `CrawlerSession`。注意 `LyricsService` 依赖桌面 `MusicDatabase`，不参与 Android 同步。
- `android-app`：原生 Java Android 工程，使用 Media3、Android SQLite、SAF 文件导入，并在构建时同步共享模型、歌词渠道和在线来源代码。`AndroidLyricsService` 在 Android 端运行同一套歌词四连查（与在线下载搜索完全解耦），歌词页右上角有强制刷新按钮。
- Android 在线下载优先写入手机根目录 `music/`；未授予所有文件访问权限时使用 MediaStore 写入 `Music/music/`，数据库 schema 2 同时兼容文件路径和 `content://` 地址。

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

Android Debug APK：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android-app\build-apk.ps1 -Clean
```

## 协作约束

- 修改或删除文件前先征得用户允许。
- 不删除 `downloads/` 及其中用户数据。
- 在线来源可能随网站接口调整而失效，构建测试不得依赖实时网站可用性。新 Provider 的搜索/解析逻辑抽成包内可见静态方法，用真实响应裁剪的 fixture 做离线测试；改动在线逻辑后可用临时探针类对真实接口做一次性烟测，验证完删除。
