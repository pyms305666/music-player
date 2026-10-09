# ZA音乐 4.2.0

ZA音乐是一款支持 Windows 桌面和 Android 手机的本地音乐播放器。项目使用 Java 编写；Windows 桌面端采用 JavaFX，Android 端采用原生 Android UI 和 Media3。两个版本共享歌曲模型、排序、歌词解析和在线音乐来源实现。

4.1.5 将 Windows 和 Android 的应用名称统一为“ZA音乐”。Windows 4.1.4 的顺序循环修复继续保留。

## 功能概览

- 导入单首或多首音频，也可递归扫描文件夹；支持按名称、歌手、文件名和创建日期排序。
- 导入酷狗、网易云、QQ 音乐的公开歌单分享文字或链接，预览并选择歌曲；默认只保存歌单，可选择同时下载，使用本软件已有渠道并优先酷我、咪咕。下载前集中确认本地重复歌曲，支持复用本地文件或保留两份。
- 独立保存多个歌单，支持搜索、重命名、调整顺序、删除歌单及播放本地歌曲；批量下载支持暂停、取消、重试未完成歌曲，离开歌单页面后任务继续。使用说明及验证记录见 [歌单导入](docs/playlist-import.md)。
- 播放、暂停、上一首、下一首、随机播放和单曲循环。
- 在线搜索酷狗、酷我、咪咕、QQ 音乐和网易云音乐；歌词提供网易云、QQ、酷狗和 LRCLIB 来源。
- 预览在线歌曲的歌词和封面，下载后加入本地曲库播放。遇到受限或失效的下载地址时会尝试其他来源。
- 在线搜索按来源完成顺序追加结果，保留选择；成功查询缓存最多 50 项、2 分钟。搜索和下载可分别取消，下载最多同时运行 2 项、等待 8 项；未知文件大小时显示已下载大小。
- 显示本地 LRC 和缓存歌词；支持桌面端歌词锁定、字体缩放及纯歌词模式。
- SQLite 保存曲库信息及歌词缓存；封面、歌词和播放兼容文件使用本地缓存。
- 两端共用歌词时间轴；安卓歌词复用全文，只更新高亮。安卓导入每 50 首提交一次事务，并报告成功、重复和失败数量。

在线来源依赖第三方网站接口，可能随网站改版而不可用；在线功能需要网络连接。请仅在遵守当地法律法规和相关服务条款的前提下使用在线搜索与下载功能。

## 项目结构

```text
├─ src/main/java/app/musicplayer/       JavaFX 界面、桌面数据与播放服务
├─ src/test/java/                       桌面模块测试
├─ src/main/resources/                  桌面图标和样式
├─ shared/                              两端直接依赖的 Java 17 共享模块
│  ├─ src/main/java/                    模型、队列、歌词、在线搜索与下载
│  └─ src/test/java/                    共享逻辑离线回归测试
├─ android-app/                         原生 Android UI、SQLite 与 Media3 服务
├─ scripts/                             构建、桌面播放烟测和发布校验
├─ .github/workflows/build.yml           Windows / 原生 Android CI
├─ version.properties                   唯一的应用版本号与 Android versionCode
├─ packaging/                           Windows 安装资源
├─ run.ps1                              桌面 Gradle 命令入口
├─ verify.ps1                           桌面、共享模块及发布工具验证
└─ package.ps1                          Windows 安装包构建
```

`MusicPlayerLauncher` 是桌面应用入口，`MusicPlayerApp` 协调 JavaFX 页面。根工程与独立的 `android-app` 工程均通过 `project(":shared")` 依赖共享模块；不再复制源码。两套构建的共享模块输出目录独立，Android 数据库和界面实现仍然独立。

## 环境要求

- Windows 10/11，用于运行下列 PowerShell 脚本。
- 桌面使用 JDK 25.0.2，设置 `JAVA_HOME`；本机也支持 `C:\jdk-25.0.2`。Gradle 9.6.1 由已提交的 Wrapper 下载并校验 SHA-256；已存在的同版本 `.tools/gradle-*` 可复用。
- Android APK 脚本会在 `.tools/` 中准备 Android SDK 与 JDK 17.0.19；Gradle 8.11.1 由 Wrapper 下载。首次构建需联网下载工具和依赖，并接受 Android SDK 许可。
- Windows 安装包需要 JDK（含 `jpackage`）和 WiX 5.0.2。`package.ps1` 优先使用本机 `C:\jdk-25.0.2`，否则使用 `JAVA_HOME` 或 PATH 的 `jpackage`；选中的工具必须为 JDK 25，避免与 WiX 5 不兼容。

工具下载和构建产物保存在项目内的 `.tools/`、`build/`、`android-app/app/build/` 等目录；这些目录不属于源代码。

## Windows 桌面版

在项目根目录打开 PowerShell：

```powershell
.\run.ps1 run
```

如果 PowerShell 不允许执行本地脚本，可在当前进程中临时放宽策略后运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 run
```

### 移动竖屏预览

桌面版包含 9:16 移动布局预览，可在 Windows 窗口中检查 Android 风格的歌单、歌词和在线搜索页面：

```powershell
.\run.ps1 run '--args=--mobile'
```

这是 JavaFX 预览模式，不是 Android 模拟器或 Android 应用。真实 Android 应用见下文。

## Android 版

在项目根目录运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android-app\build-apk.ps1
```

添加 `-Clean` 可先清理 Android 构建目录，再构建 Debug APK：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android-app\build-apk.ps1 -Clean
```

开发用 APK 默认生成到 `android-app\app\build\outputs\apk\debug\app-debug.apk`。GitHub Release 从 4.1.9 起提供关闭调试的 Release APK；正式发布请使用下面的轮换签名打包流程，不要分发 Gradle 的中间 APK。

Android 应用要求 Android 9（API 28）或更高版本。首次导入或管理音频时，系统可能请求音频读取或文件管理权限；也可通过系统文件选择器导入文件。在线搜索与下载需要网络。下载音乐优先保存到内部存储 `music/`，必要时回退到共享存储 `Music/music/`。Android 数据和媒体文件位于设备上，与 Windows 版数据目录不自动同步。

## 验证

```powershell
.\verify.ps1
```

脚本运行桌面端 JUnit 测试并生成 `installDist`。项目位于中文路径时，脚本会临时使用 Windows `subst` 映射到 ASCII 盘符，以避免 Gradle/JDK 在测试 classpath 中错误编码路径；映射会在脚本结束时移除。在线来源测试使用离线样例，不要求实时服务可用。

## 构建 Windows 安装包

先准备 WiX 5.0.2：

```powershell
dotnet tool install --tool-path .\.tools\wix wix --version 5.0.2
wix extension add --global WixToolset.Util.wixext/5.0.2
```

再运行：

```powershell
.\package.ps1
```

脚本先运行 `verify.ps1`，再生成含 Java 运行时的 Windows 安装包。每次构建会在 `build\installer\<版本>-<时间戳>\` 新建输出目录，不覆盖旧目录。版本从 `version.properties` 读取；传入不同的 `-Version` 会被拒绝，避免文件名与程序版本不一致。安装后的应用在用户数据目录保存曲库，通常不需要用户另行安装 Java。

## 数据和缓存

Windows 安装版从 4.1.6 起将数据库、下载音乐及缓存保存在 `%LOCALAPPDATA%\ZA-Music-Data\`，与安装目录分离。开发版继续使用项目 `downloads/`。目录内容如下：

```text
downloads/
├─ music-player.db                 曲库与歌词缓存数据库
├─ 下载的音频文件
└─ cache/
   ├─ lyrics/                       歌词缓存
   ├─ artwork/                      封面缓存
   ├─ playback/                     播放兼容缓存
   └─ sqlite-native/                 SQLite JDBC 原生库临时文件
```

桌面版会尝试将旧位置的 `music-player.db` 复制到新目录。数据库沿用 `tracks` 和 `lyrics` 表；重构不要求删除旧数据。曲库中移除歌曲只会修改曲库记录，不会删除原始本地音频。Windows 的 `downloads/` 被 `.gitignore` 排除；请自行备份，其中可能包含个人音乐和数据库。

桌面封面磁盘缓存预算为 256MiB，播放格式修正缓存为 512MiB。后台按最近使用时间清理直接位于对应目录、符合应用生成名称的文件；原始歌曲、其他文件、子目录和符号链接不参与淘汰。正在使用的缓存受租约保护，可能暂时超过预算，释放后再清理。两端下载地址缓存最多 500 项，成功有效期 4 分钟，失败有效期 45 秒。

安卓数据库升级到版本 3，新增导入 URI 与私有歌曲路径的映射；原曲库及歌词表保留。重复选择同一 URI 会跳过已有且可读的歌曲，移除歌曲后可以重新导入；导入仍会复制到私有曲库。文件复制或某批事务失败时清理该批未入库的副本，之前成功的批次保留。

Windows 4.1.5 的“ZA音乐”使用新的安装目录。首次启动时，如果新版曲库尚未建立，会从同级旧目录“简约音乐播放器/downloads”复制歌曲、数据库和缓存，并修正数据库中下载歌曲的路径。旧目录不会被此迁移删除；首次启动前请关闭旧版播放器。若旧版使用自定义安装路径，请先备份 `downloads/`，再手动迁移。

## 发布文件

最新安装包位于 [GitHub Releases](https://github.com/pyms305666/music-player/releases)。从 4.1.6 起不再向 Git 提交二进制附件；历史已跟踪文件保留。4.1.8 起额外发布 `build-info.json`，包含源码提交、对应 CI、构建工具、Android 证书及附件 SHA-256；`SHA256SUMS.txt` 同时覆盖两个安装包与构建记录。

## 常见问题

- **首次启动或构建时间较长：** 脚本需要下载 Gradle、JDK、Android SDK 或依赖，请确认网络可访问相应下载站点。
- **桌面端找不到 JavaFX 或无法启动：** 确认使用 JDK 25，并从项目根目录通过 `run.ps1` 启动。
- **在线歌曲或歌词缺失：** 第三方接口会变化，搜索结果不保证始终可用；可稍后重试或选择其他来源。在线接口故障不会影响本地曲库播放。
- **Android 导入后无法播放或无法管理文件：** 检查系统授予的音频/文件权限，或通过应用内文件选择器重新导入。
- **Windows 打包失败并提示缺少 WiX：** 确认本地 `.tools\wix\wix.exe` 为 WiX 5，并已安装对应的 `WixToolset.Util.wixext/5.0.2` 扩展。

## 许可

项目使用 [MIT License](LICENSE)。第三方库及在线音乐服务遵循各自的许可与服务条款。

## 4.1.6 第一轮优化

- Android 排序同步更新完整播放队列；搜索只过滤显示。两端切换歌曲后会忽略旧预览，Android 搜索按最新请求更新。
- 下载先写独立 `.part` 文件，检查内容后发布；并发同名文件不会互相覆盖。桌面遇到不支持的 FLAC/Ogg/原始 AAC 来源会尝试其他来源。
- Windows 安装到 `ZA-Music`，与 4.1.5 的安装包身份分离，避免迁移前卸载旧版数据。首次启动会查找同级 `ZA音乐/downloads`、`简约音乐播放器/downloads` 和当前安装目录 `downloads`。
- 迁移前创建 SQLite 一致性快照，备份保存在用户数据目录 `backups/`；迁移校验完成后才启用新数据库。旧歌曲与数据库保留，已有新曲库不被覆盖；冲突或失败可以重试。
- 自定义旧位置可在启动时通过 `-Dmusicplayer.migrate-from=<旧 downloads 目录>` 指定；仅对尚未建立的新曲库生效。请先关闭旧版；迁移完成并确认歌曲正常后再处理旧安装。
- 后续安装包放在 GitHub Releases，不再向 Git 历史追加大体积二进制；历史发布文件保留。


## 4.1.7 第二轮优化

- Android 使用 Media3 `MediaSessionService` 持有完整队列。顺序播放采用列表循环，单曲循环和随机播放由播放器执行；关闭播放页面后仍可后台切歌。通知栏提供播放控制，点击通知返回应用，页面重新连接当前播放状态。
- 播放服务处理音频焦点及耳机拔出事件；息屏播放持有播放期间需要的唤醒锁。系统强制停止、清理应用数据或终止进程后的自动恢复不在本轮范围内。
- 两端将曲库恢复、导入、歌词缓存和封面读取移到后台线程。桌面音频格式检查与兼容文件准备也在后台执行；数据库批量写入使用事务，失败时回滚。
- Android 页面不可见时停止进度和歌词定时更新，返回页面时同步当前歌曲；同一歌词行内不重复构造全文样式。
- 在线歌曲搜索使用一次 10 秒总预算并保留已经返回的来源结果；下载地址在请求下载时解析。新的搜索与歌词预览会取消旧任务，在线歌词使用 15 秒查询预算；下载任务独立执行。搜索结果“可尝试下载”不保证来源最终提供可播放文件。
- 封面 HTTP 请求使用独立执行资源，图片限制为 12 MiB，缓存通过临时文件发布。桌面歌词缓存一并保存封面地址，重新启动后可复用已下载封面。
- 本轮保持桌面和 Android 现有界面布局；Windows 安装身份、数据目录与 Android 包名及签名沿用 4.1.6。

Android 真机回归测试位于 `android-app/app/src/androidTest/`。构建测试 APK 后，安装主 APK 和测试 APK，再执行：

```powershell
.tools\android-sdk\platform-tools\adb.exe -s <设备序列号> shell am instrument -w app.musicplayer.android.test/androidx.test.runner.AndroidJUnitRunner
```

测试使用缓存 WAV 音频，保留个人曲库；包括关闭页面后的循环与重连、单曲循环和切歌、临时音频焦点、息屏循环。测试需要手机已解锁，息屏测试后可能需要再次解锁。验证结果及具体设备见 `HANDOFF.md`。


## 4.1.8 第三轮：维护与发布

版本统一维护在 `version.properties`；升级时同时递增 `versionName` 与 `versionCode`。Gradle Wrapper 固定版本和分发包校验码。CI 使用 JDK 25.0.2 / 17.0.19、AGP 8.9.2、WiX 5.0.2，Actions 固定至提交。

### 本地回归与 CI

```powershell
.\verify.ps1
.\android-app\verify.ps1
.\scripts\desktop-smoke.ps1
```

前两项执行两端构建、共享逻辑在两个 Java 版本下的测试、Android lint，以及发布记录校验的正反向用例。最后一项需要 Windows 桌面及可用音频设备，使用独立临时曲库测试恢复、慢 I/O 时响应、快速选歌、末曲循环和暂停/恢复，不读取个人曲库。Android 服务回归见上一节。

`Build and verify` 工作流构建 Windows 安装包及原生 Android APK，API 35 模拟器运行开发包回归；API 28、31、32、33 模拟器分别验证旧证书覆盖升级、正式版本连续升级、数据和权限保留、播放及全新安装。CI 为每个任务创建一次性新旧密钥，**其测试 APK 不能覆盖用户安装的官方版本**，CI 不保管生产私钥。共享 JAR 在同一环境重复构建时校验字节一致；不承诺 EXE/APK 跨环境字节完全一致。

### 签名管理

包名保持 `app.musicplayer.android`。4.1.9 采用 APK v3/v3.1 证书轮换：Android 13/API 33 及以上使用专用正式证书，Android 9/API 28 至 12L/API 32 保留原证书用于兼容；两者均为关闭调试的 Release 构建。旧版无需卸载，可覆盖升级并保留数据。旧证书的数据迁移和签名权限兼容能力开启，回退、共享 UID、认证能力关闭。旧 Android 系统仍依赖旧证书，因此不能称为所有系统均已更换证书。

公钥证书、证书指纹及轮换证明位于 `android-app/signing/`，可公开。私钥、密码和密钥备份必须放在仓库外。首次配置可执行 `python scripts/signing_vault.py init`：仅允许一次初始化，使用本机原调试证书生成轮换证明，新私钥采用加密 PKCS12，密码由 Windows 当前用户 DPAPI 保护；密钥 ZIP 和独立密码恢复文件须分别离线备份。已有签名身份禁止重新生成。

```powershell
python scripts/android-signing.py package
python scripts/android-signing.py verify --apk android-app/dist/ZA-Music-Android-4.1.12.apk
```

打包先执行共享测试、Release 构建和 lint，再用新旧密钥与轮换证明签名，逐个验证 API 28/31/32/33/35/36 的证书选择和二进制清单，生成 APK 旁的构建记录。测试 APK 仅用于本地验证，验证后卸载，不上传 Release。外部构建须同时提供新旧两组四个环境变量（`ZA_KEYSTORE`、`ZA_STORE_PASSWORD`、`ZA_KEY_ALIAS`、`ZA_KEY_PASSWORD`，旧组增加 `OLD_`：如 `ZA_OLD_KEYSTORE`）。直接 `assembleRelease` 得到的中间包不包含完整发布轮换流程。Windows EXE 尚未配置 Authenticode 签名。

### 发布流程

1. 修改唯一版本文件和发布说明，执行回归后提交并推送源码。
2. 确认该提交的 `Build and verify` 全部 CI job 通过。
3. 在干净工作树重新执行 `package.ps1`、`python scripts/android-signing.py package`；记录绑定源码和散列。真机验证正式包覆盖升级、数据保留及播放回归。
4. 设置已授权的 `GH_TOKEN` 或登录 `gh`，执行发布脚本；也可以先加 `-VerifyOnly` 校验。

```powershell
.\scripts\publish-release.ps1 -WindowsInstaller '<本次 EXE 绝对路径>' -NotesFile '.\docs\release-4.1.12.md'
```

脚本检查干净源码、附件构建提交、应用版本、包名/证书、散列、远端分支和对应提交的 CI。它先创建草稿并校验上传附件，再公开并检查 tag 与源码一致。不覆盖已有 Release；失败后检查保留的草稿再处理。构建记录是发布核对信息，不是第三方签发的供应链证明。
