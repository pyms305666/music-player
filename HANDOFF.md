# 项目交接：ZA音乐 4.1.12

## 当前状态

- Java 25 + JavaFX 25.0.1 + Gradle 9.6.1 + SQLite。
- 主入口：`app.musicplayer.MusicPlayerLauncher`。
- 应用控制器：`app.musicplayer.MusicPlayerApp`。
- 版本：Windows 桌面版与原生 Android 版均为 `4.1.12`。
- Windows 安装版数据：`%LOCALAPPDATA%/ZA-Music-Data`；开发版：`downloads/`；Android：设备私有数据库和用户媒体目录。
- 数据库保持兼容：`tracks`、`lyrics`；Android schema 3 额外保存导入来源映射 `imports`，升级保留原表和用户数据。

## 主要模块

- 第三轮（4.1.12）：`BoundedExpiringCache` / `ResolutionCache` 限制下载解析缓存；`GeneratedFileCache` 用租约保护在用封面和播放修正文件；`LyricTimeline` 双端共用，Android `AndroidLyricsPresenter` 保持全文并更新高亮；`AndroidLibraryImporter` 按 50 首事务导入、按 URI 去重并清理失败副本，`AndroidTrackFiles` 原子预留文件名。性能和完整验证范围见 `docs/performance-4.1.12.md`。
- 三组独立进程性能对照已完成。长歌词稳定堆增加 0.566MiB / 15.257%，用户于 2026-10-05 明确接受，以保留约 99.5% 的高亮耗时下降；该例外不扩大到其他指标。真机候选覆盖升级与 24 项功能回归通过，保留 102 首歌曲、4 条歌词缓存。最终附件来源、全部 CI 与散列以 Release 的构建记录为准。

- `playlist.SearchSnapshot` / `LocalSearch`：不可变匹配字段、后台过滤、200ms 大曲库防抖和请求版本校验。
- Android `ui.LocalTrackList` / `TrackRow` / `TrackAdapter`：曲库变更时排序并建快照，搜索仅更新显示；DiffUtil 比较不可变字段，选择按歌曲标识保留。
- `artwork.ArtworkPresenter` / `ArtworkDecoder` / `DecodedArtworkCache`：显示尺寸及 DPI 采样、切歌取消、8 张/16MiB 解码缓存。`ArtworkService` 按地址合并下载，最后一个消费者取消时中断工作。

第一轮已公开发布，记录见 `docs/performance-4.1.10.md`。第二轮记录见 `docs/performance-4.1.11.md`；最终提交、CI 与附件来源以 Release 构建记录为准。

- `online.OnlineSearchSnapshot` / `SearchCoordinator`：不可变来源状态及到达顺序结果；独立协调线程，最多每 100ms 推送一次界面，旧查询回调失效。
- `SearchResultCache`：规范化关键词，LRU 50 项/2 分钟；仅所有来源正常完成的结果可入缓存，包括正常的空结果。
- `RequestCancellation`：请求拥有连接/进程的取消注册；只取消本任务，服务整体关闭才断开全部连接。
- `CrawlerSession`：OkHttp 4.12.0 同步请求复用会话连接池，延迟创建客户端；取消注册绑定单个 Call，截止时间覆盖响应体。CookieManager 在网络拦截器逐跳按域发送/接收 Cookie。避免重新使用会等待阻塞 body read 的 HttpURLConnection.disconnect；下载取消必须实际释放工作线程。
- `DownloadQueue`：2 个工作线程、8 个等待任务，同歌曲/规范化目标目录共享传输，各订阅独立取消，最后一个取消才中断传输。
- `DownloadController`：两端共享下载与加入曲库的交接、去重、重试状态；界面组件 `DesktopOnlineTasks` / `AndroidOnlineTasks` 接入现有控件。业务判断使用枚举，不解析提示文字。
- Android 在线结果使用不可变 `OnlineTrackInfo` 与 `ListAdapter`，按歌曲标识保留选择。正式 APK 继续沿用现有证书轮换和安装身份。

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
- `lyrics.OnlineLyricsProvider` + 四个歌词渠道（网易云/QQ/酷狗/LRCLIB）：双端共享，HTTP 通过 `LyricsHttp` 接口注入——桌面用 java.net.http，Android 用共享的 `CrawlerSession`。注意 `LyricsService` 依赖桌面 `MusicDatabase`，保留在桌面模块。
- `android-app`：原生 Java Android 工程，使用 Media3、Android SQLite、SAF 文件导入，直接依赖 `shared` Java 17 模块中的模型、歌词渠道和在线来源代码。`AndroidLyricsService` 在 Android 端运行同一套歌词四连查（与在线下载搜索完全解耦），歌词页右上角有强制刷新按钮。
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

- 本地验证以 `verify.ps1` 为准；Android 调试 APK 使用 `android-app/build-apk.ps1` 生成。Android SDK/JDK 由该脚本准备，Gradle 使用固定版本及 SHA-256 的 Wrapper。
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


## 2026-09-30 第三轮（4.1.8）

- 两端共享模块：`shared`（Java 17），独立工程分别输出到 `shared/build/simple-music-player` 与 `shared/build/simple-music-player-android`；没有 `syncSharedJava`。
- `version.properties` 是唯一应用版本来源；根 Wrapper 9.6.1、Android Wrapper 8.11.1，均有官方 SHA-256。
- 清理 Gluon 构建路径，CI 改为实际 JavaFX Windows / 原生 Android；CI APK 为测试用临时签名。
- 正式 Android 签名由四个 `ZA_*` 环境变量配置；本次发布沿用原调试渠道，不向 GitHub 上传私钥。
- 发布脚本要求两端附件在干净提交后构建，并检查对应提交的 CI、源码和签名；发布记录及校验文件随 Release 上传。
- 新增共享缓存键回归、8 种无效发布记录拒绝用例；将桌面真实播放烟测纳入可复用脚本。
- 本地回归：桌面 28 + 共享 43 项（Java 25），共享 43 项（Java 17）全部通过；发布记录 8 种无效输入拒绝通过；共享 JAR 重建散列相同。
- Windows JavaFX 实际播放烟测通过；Android lint 0 错误、28 警告。
- vivo V2528A / Android 16 覆盖升级至 4.1.8 成功，4 项播放服务回归通过；升级前后 2 条个人曲库记录完全一致，SQLite integrity_check 为 ok。测试 APK 已卸载，主应用与数据保留。
- 每个提交的 CI 和最终安装包来源以 Actions 记录及 Release 的 build-info.json 为准；发布脚本只允许对应提交 CI 成功后公开。

## 2026-10-04 Android 正式签名（4.1.9）

- 包名保持 `app.musicplayer.android`，新证书 SHA-256 为 `ccb15116f0a591ef3a6c49f88aa73cb9458afb6f69775be0659a0432d3f48e68`。Android 13+ 选择新证书，API 28–32 继续使用原证书；所有发布 APK 均关闭调试。
- `android-app/signing/` 只存公钥证书、策略和轮换证明。正式私钥和重新加密的原私钥保存在仓库外的 Windows 当前用户签名保险库；密码由 DPAPI 保护，密钥 ZIP 与独立密码恢复文件需分别离线备份。禁止重新生成既有生产密钥。
- 旧证书保留 installed-data 与 permission 能力：真机证实关闭 permission 会触发 AndroidX 签名权限重复声明错误，阻止覆盖升级；修复后数据校验通过。rollback/shared-uid/auth 能力关闭。
- `scripts/android-signing.py package` 执行 Release 构建、共享测试及 lint，再签入 API 33 边界的轮换证明；`verify` 重新读取 APK 二进制验证 API 28/31/32/33/35/36 的证书、包名、版本和关闭调试状态。发布脚本拒绝 QA 包和伪造的构建记录。
- CI 增加 API 28/31/32/33 迁移矩阵，使用一次性旧/新密钥，覆盖带测试数据的旧版→正式版→更高版本正式版、权限/设置/可读文件/UID 保留、播放回归以及全新安装。上传列表不包含任何密钥或密码。
- vivo V2528A / Android 16 原版有 102 首歌曲、3 条歌词缓存；升级前一致性数据库备份通过。正式包覆盖升级后的曲库、文件可读性、设置、权限与 UID 散列检查通过。后续完整回归及最终附件的来源以 CI 和 Release 构建记录为准。
- 保持两端 UI 与业务逻辑；Windows 沿用原 UpgradeCode、安装目录和数据目录。未执行个人主应用卸载或清除数据。
- 本地桌面回归、共享 Java 17/25 测试、桌面真实播放烟测及正式 APK lint 通过。真机旧版→轮换正式包→更高版本正式包均成功，两个正式包各通过 4 项播放回归；最终包再次核对升级前数据散列一致，102 首歌曲及 3 条歌词缓存保留。临时测试 APK 和数据快照已删除。
- 个人曲库歌曲实际播放并确认进度增长；亮屏设置已恢复原值。生产密钥 ZIP 中的加密 PKCS12 已使用独立恢复密码解密并导出证书核对，恢复校验通过。
