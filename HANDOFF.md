# 项目交接：ZA音乐 4.1.5

## 当前状态

- Java 25 + JavaFX 25.0.1 + Gradle 9.6.1 + SQLite。
- 主入口：`app.musicplayer.MusicPlayerLauncher`。
- 应用控制器：`app.musicplayer.MusicPlayerApp`。
- 版本：Windows 桌面版与原生 Android 版均为 `4.1.5`。
- 数据统一位于程序目录的 `downloads/`。
- 数据库 schema 保持兼容：`tracks`、`lyrics`。

## 主要模块

- `config.AppPaths`：运行目录解析、缓存目录创建、旧数据库复制迁移。
- `config.LegacyInstallMigration`：Windows 安装版首次启动时，将旧名称安装目录的 `downloads/` 复制到“ZA音乐”目录，并修正数据库中旧下载文件的路径。
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

## 维护说明

- 本地验证以 `verify.ps1` 为准；Android 调试 APK 使用 `android-app/build-apk.ps1` 生成。Android SDK、Gradle 和 JDK 由该脚本下载到项目 `.tools/`。
- 不删除 `downloads/` 及其中用户数据。
- 在线来源可能随网站接口调整而失效，构建测试不得依赖实时网站可用性。新 Provider 的搜索/解析逻辑抽成包内可见静态方法，用真实响应裁剪的 fixture 做离线测试；改动在线逻辑后可用临时探针类对真实接口做一次性烟测，验证完删除。
- `package.ps1` 目前构建 Windows app-image 和 EXE 安装程序；发布目录 `release/` 的文件整理应以实际发布流程为准，不代表该脚本会自动生成 ZIP、APK 或校验文件。

## 2026-09-30 第一轮（4.1.6）

播放队列、过期请求、下载发布和用户数据迁移已实现。安装包使用新 UpgradeCode 与 `ZA-Music` 目录，后续版本必须沿用此身份；数据库/歌曲在 `%LOCALAPPDATA%/ZA-Music-Data`，升级卸载不可清除此目录。迁移使用 SQLite VACUUM INTO 快照与完整性检查，保留原始目录和备份。Release 二进制仅上传附件。


## 2026-09-30 第二轮（4.1.7）

- Android 播放器从 Activity 移到 `PlaybackService`（Media3 MediaSessionService）。队列、列表/单曲循环和随机模式属于服务；页面使用异步 MediaController 连接，销毁页面只释放连接。服务负责音频焦点、耳机拔出暂停、通知及播放唤醒锁。
- 页面不可见时不更新进度/歌词时间轴，也不随自动切歌重新读取歌词封面；回来后同步当前曲目。变更曲库或排序时按服务当前媒体 ID 保留位置。
- 两端将导入、曲库恢复、歌词缓存读取和封面提取移到后台；桌面格式检查及兼容文件准备同样移到后台。桌面曲库批量写入采用事务，写入失败在 UI 报错，退出时排队的数据库写入及关闭操作会完成。
- 搜索使用一个 10 秒预算，先返回歌曲列表，下载时才解析地址。来源异常可触发来源暂停；空结果视为成功。Cookie 按域发送，HTTP 连接在关闭服务时断开。新的搜索、预览或歌词请求取消旧工作，先标记结果过期再中断线程，避免竞态；歌词查询使用 15 秒预算。
- 桌面封面缓存修复单线程与同步 HTTP 请求互相等待的问题，限制单图 12 MiB，并用临时文件发布。桌面歌词表兼容新增 artwork_url，旧歌词仍可读，已有封面地址不会被空值覆盖。

最终检查：69 项 JVM 测试全部通过（包括事务回滚、旧歌词表迁移和离线封面恢复、搜索总预算/来源暂停、Cookie 域隔离、封面下载和大小限制、旧请求取消）。Windows installDist/EXE 打包成功；实际 JavaFX 运行检查通过异步恢复、慢磁盘任务期间界面响应、快速切歌只应用最后选择、最小化后自然循环、暂停恢复和正常退出。测试音频/曲库使用独立目录。

Android assembleDebug/assembleDebugAndroidTest/lintDebug 成功，lint 为 0 errors / 30 warnings（含依赖升级、资源与兼容提示，未宣称零警告）。vivo V2528A / Android 16 真机的 4 项测试通过：页面销毁后的列表回绕、无页面连接时继续播放和重连、单曲循环及切歌/随机模式开关、临时音频焦点丢失后的暂停恢复、息屏循环。使用临时缓存 WAV，恢复测试前队列配置；个人曲库的 2 条记录检查未改变，SQLite 完整性为 ok。实际曲库歌曲播放亦正常。新旧 APK 签名一致，包名不变，可覆盖安装。

本轮没有改变两端布局，没有进行多机型或长时间耗电验证，也没有验证进程被系统终止后的自动恢复。耳机拔出处理由 ExoPlayer 提供，本轮未做实体耳机拔插测试。在线来源测试采用离线响应，发布不保证第三方网站始终可用。

发布附件：Windows EXE、Android Debug APK、SHA256SUMS.txt；不向 Git 追加二进制。Windows UpgradeCode 和 ZA-Music 安装目录、ZA-Music-Data 数据目录继续沿用第一轮。
