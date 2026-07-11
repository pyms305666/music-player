# 简约音乐播放器 3.2.2

基于 Java 25、JavaFX 25、SQLite 和 Gradle 的 Windows 桌面音乐播放器。

## 功能

- 导入文件夹，递归扫描支持的音频文件。
- 通过文件选择器导入一首或多首音频。
- 播放、暂停、上一首、下一首、随机播放、单曲循环。
- 按名称、歌手、文件名、创建日期进行正序或倒序排列。
- 从歌单和数据库缓存中移除歌曲，不删除本地音频文件。
- 显示本地 LRC、数据库缓存歌词和多个在线来源歌词。
- 在线搜索、歌词/封面预览、下载到本地后播放。
- 下载歌曲、SQLite 数据库、歌词、封面和播放兼容缓存统一放在 `downloads/`。
- 三栏宽度和在线搜索抽屉状态会自动保存。
- 支持歌词锁定、字体缩放和纯歌词模式。
- 安装包自带 Java 运行时，用户无需单独安装 JDK。

## 目录结构

```text
app.musicplayer
├─ config      运行目录和 downloads 路径
├─ model       歌曲、歌词、在线结果等数据模型
├─ data        SQLite 数据访问
├─ lyrics      LRC 解析、歌词缓存和歌词来源
├─ online      在线站点 provider、搜索、下载和网络会话
├─ playback    音频格式识别和兼容播放文件处理
├─ playlist    导入、去重、搜索和排序
├─ artwork     封面下载与缓存
├─ ui          播放列表、在线抽屉和播放控制组件
└─ util        JSON 和哈希等通用工具
```

`MusicPlayerApp` 是应用控制器，负责协调模块和处理 JavaFX 生命周期；
`MusicPlayerLauncher` 是打包入口，用于避免 jpackage 启动 JavaFX 主类时报运行时缺失。

## 开发运行

```powershell
.\run.ps1 run
```

第一次运行会把 Gradle 下载到项目的 `.tools/` 目录。

### Android 竖屏界面预览

移动端界面复用桌面版的播放、歌单、歌词、数据库和在线搜索逻辑，以 `405×720` 的 9:16 窗口预览。拖动宽度或高度时，窗口会自动维持 9:16 竖屏比例：

```powershell
.\run.ps1 run '--args=--mobile'
```

该模式用于在 Windows 上快速预览移动端布局。真正的 Android 应用位于 `android-app/`，使用原生 Java、Media3、Android SQLite 和系统文件选择器，并复用桌面版的数据模型、歌词解析、排序与在线来源代码。

### 生成 Android APK

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android-app\build-apk.ps1 -Clean
```

脚本会把 Android SDK、Gradle 8.11.1 和 JDK 17 缓存在项目 `.tools/`，不会修改系统环境。Debug APK 输出到：

```text
android-app\app\build\outputs\apk\debug\app-debug.apk
android-app\dist\simple-music-player-3.2.2-debug.apk
```

Android 版支持竖屏歌单、歌词、在线搜索下载、Media3 本地播放、多文件导入、删除、SQLite 缓存及名称/歌手/文件名/创建日期排序。

## 验证

```powershell
.\verify.ps1
```

验证脚本会执行测试并生成 `installDist`。由于 Gradle/JDK 在 Windows 中文项目路径下可能生成错误的测试 classpath，脚本会临时映射一个 ASCII 盘符，结束后自动取消映射，不移动项目文件。

## 生成安装包

```powershell
.\package.ps1
```

安装包输出到：

```text
build\installer\3.2.2-时间戳\简约音乐播放器-3.2.2.exe
```

统一发布文件整理到 `release/3.2.2/`，其中包含 Windows 安装包、Windows 便携版 ZIP、Android APK 和 SHA-256 校验文件。该目录不提交到 Git 历史，二进制文件通过 GitHub Release 发布。

打包依赖项目本地 WiX 5：

```powershell
dotnet tool install --tool-path .\.tools\wix wix --version 5.0.2
wix extension add --global WixToolset.Util.wixext/5.0.2
```

## 数据目录

开发环境使用项目目录下的 `downloads/`。jpackage 安装版优先使用 exe 所在目录下的 `downloads/`。

```text
downloads/
├─ music-player.db
├─ 下载的音频文件
└─ cache/
   ├─ lyrics/
   ├─ artwork/
   ├─ playback/
   └─ sqlite-native/  SQLite JDBC 每次启动使用的隔离原生库目录
```

重构保留原 SQLite 表结构和已有缓存路径，旧数据可以继续读取。
