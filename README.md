# 简约音乐播放器 4.1.3

简约音乐播放器是一款支持 Windows 桌面和 Android 手机的本地音乐播放器。项目使用 Java 编写；Windows 桌面端采用 JavaFX，Android 端采用原生 Android UI 和 Media3。两个版本共享歌曲模型、排序、歌词解析和在线音乐来源实现。

## 功能概览

- 导入单首或多首音频，也可递归扫描文件夹；支持按名称、歌手、文件名和创建日期排序。
- 播放、暂停、上一首、下一首、随机播放和单曲循环。
- 在线搜索酷狗、酷我、咪咕、QQ 音乐和网易云音乐；歌词提供网易云、QQ、酷狗和 LRCLIB 来源。
- 预览在线歌曲的歌词和封面，下载后加入本地曲库播放。遇到受限或失效的下载地址时会尝试其他来源。
- 显示本地 LRC 和缓存歌词；支持桌面端歌词锁定、字体缩放及纯歌词模式。
- SQLite 保存曲库信息及歌词缓存；封面、歌词和播放兼容文件使用本地缓存。

在线来源依赖第三方网站接口，可能随网站改版而不可用；在线功能需要网络连接。请仅在遵守当地法律法规和相关服务条款的前提下使用在线搜索与下载功能。

## 项目结构

```text
├─ src/main/java/app/musicplayer/       桌面端代码及 Android 共用逻辑
│  ├─ config/                           路径、布局和 SQLite 原生库配置
│  ├─ model/                            歌曲、歌词和在线结果模型
│  ├─ data/                             桌面端 SQLite 数据访问
│  ├─ lyrics/                           LRC 解析、歌词服务及在线歌词来源
│  ├─ online/                           在线来源、搜索和下载编排
│  ├─ playback/                         音频格式识别和播放文件处理
│  ├─ playlist/                         曲库导入、去重、搜索和排序
│  ├─ artwork/                          封面下载和缓存
│  └─ ui/                               JavaFX 播放器界面
├─ src/test/java/                       桌面端自动化测试
├─ src/main/resources/                  桌面端图标和样式
├─ android-app/                         原生 Android 工程
├─ packaging/                           Windows 安装包资源
├─ run.ps1                              Windows 桌面端 Gradle 命令入口
├─ verify.ps1                           桌面端测试和发行目录验证
└─ package.ps1                          Windows 安装包构建脚本
```

`MusicPlayerLauncher` 是桌面应用入口，`MusicPlayerApp` 负责 JavaFX 生命周期和界面协调。Android 工程在构建时从 `src/main/java` 同步指定的共享源码；Android 数据库和界面实现独立于桌面端。

## 环境要求

- Windows 10/11，用于运行下列 PowerShell 脚本。
- 桌面开发需要 JDK 25；`run.ps1` 会在项目 `.tools/` 中下载 Gradle 9.6.1。需能访问 Gradle 分发站点及 Maven 仓库。
- Android APK 脚本会在 `.tools/` 中准备 Android SDK、Gradle 8.11.1 和 JDK 17。首次构建需联网下载工具和依赖，并接受 Android SDK 许可。
- Windows 安装包需要 JDK（含 `jpackage`）和 WiX 5.0.2。`package.ps1` 查找 `C:\jdk-25.0.2\bin\jpackage.exe`，找不到时使用 PATH 中的 `jpackage.exe`。

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

APK 默认生成到 `android-app\app\build\outputs\apk\debug\app-debug.apk`，脚本也会复制一份到 `android-app\dist\simple-music-player-4.1.3-debug.apk`。Debug APK 使用 Android 默认调试密钥签名，适合测试安装；发布到商店前需配置正式签名和发布构建流程。

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

脚本先运行 `verify.ps1`，再生成含 Java 运行时的 Windows 安装包。每次构建会在 `build\installer\<版本>-<时间戳>\` 新建输出目录，不覆盖旧目录。`package.ps1 -Version 4.1.3` 可指定版本号。安装后的应用可在 exe 所在目录写入 `downloads/`，通常不需要用户另行安装 Java。

## 数据和缓存

Windows 开发版和安装版都将 SQLite 数据库、下载音乐及缓存集中保存在应用基目录的 `downloads/`：

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

## 发布文件

发布整理目录约定为 `release/4.1.3/`，用于存放 Windows 安装包、便携版 ZIP、Android APK 和 SHA-256 校验文件。目录通常被 Git 忽略；本次桌面版改版的 Windows 安装包及校验文件单独保存在 `release/4.1.3-desktop-ui/` 并随源码提交。

## 常见问题

- **首次启动或构建时间较长：** 脚本需要下载 Gradle、JDK、Android SDK 或依赖，请确认网络可访问相应下载站点。
- **桌面端找不到 JavaFX 或无法启动：** 确认使用 JDK 25，并从项目根目录通过 `run.ps1` 启动。
- **在线歌曲或歌词缺失：** 第三方接口会变化，搜索结果不保证始终可用；可稍后重试或选择其他来源。在线接口故障不会影响本地曲库播放。
- **Android 导入后无法播放或无法管理文件：** 检查系统授予的音频/文件权限，或通过应用内文件选择器重新导入。
- **Windows 打包失败并提示缺少 WiX：** 确认本地 `.tools\wix\wix.exe` 为 WiX 5，并已安装对应的 `WixToolset.Util.wixext/5.0.2` 扩展。

## 许可

项目使用 [MIT License](LICENSE)。第三方库及在线音乐服务遵循各自的许可与服务条款。
