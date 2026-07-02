# 项目交接文档：简约音乐播放器

## 1. 项目概况

- 项目路径：`E:\codx project\音乐播放器`
- 技术栈：Java 25 + JavaFX 25.0.1 + Gradle + SQLite
- 项目类型：本地桌面音乐播放器
- 当前 Git 状态：
  - 分支：`master`
  - 当前提交：`1b3857c Release 1.0`
  - 标签：`1.0`
- 当前工作区状态：干净，无未提交改动

## 2. 已实现功能

- 从文件夹递归导入歌曲
- 支持格式：`mp3`、`m4a`、`aac`、`wav`、`aif`、`aiff`
- 显示歌曲名、歌手名
- 播放模式：
  - 顺序播放
  - 随机播放
  - 单曲循环
- 播放列表搜索：
  - 可按歌曲名、歌手、文件名过滤
- 空格键播放/暂停：
  - 只有当焦点不在 `TextField` 时触发
  - 已从 `setOnKeyPressed` 改成 `addEventFilter(KEY_PRESSED)`，修复了“必须先点一次暂停按钮后空格才生效”的问题
- 歌词来源优先级：
  - SQLite 数据库缓存
  - 同目录同名 `.lrc`
  - LRCLIB 联网搜索
- 歌词未找到时后台持续重试
- SQLite 本地数据库缓存：
  - 导入歌曲
  - 歌词内容
- 应用启动时自动恢复数据库中仍存在的歌曲
- UI 为简约深色风格

## 3. 当前明确未做的功能

- 没有做歌曲封面背景
- 没有读取音频内嵌专辑封面
- 没有做手动导入歌词
- 没有做多网站爬虫歌词源
- 没有做酷狗/QQ/网易云网页抓取
- 没有做反爬绕过逻辑

这些不是遗漏，是明确没有实现。

## 4. 主要文件说明

- 入口与主逻辑：
  - `src/main/java/app/musicplayer/MusicPlayerApp.java`
- 歌词服务：
  - `src/main/java/app/musicplayer/LyricsService.java`
- 本地数据库：
  - `src/main/java/app/musicplayer/MusicDatabase.java`
- LRC 解析：
  - `src/main/java/app/musicplayer/LrcParser.java`
- 歌曲模型：
  - `src/main/java/app/musicplayer/Track.java`
- 歌词模型：
  - `src/main/java/app/musicplayer/Lyrics.java`
  - `src/main/java/app/musicplayer/LyricLine.java`
- 播放模式枚举：
  - `src/main/java/app/musicplayer/PlayMode.java`
- 样式：
  - `src/main/resources/styles.css`
- 构建配置：
  - `build.gradle`
  - `settings.gradle`
  - `gradle.properties`
- 运行脚本：
  - `run.ps1`
- 说明文档：
  - `README.md`

## 5. 架构与关键实现

- `MusicPlayerApp`
  - 管 UI、播放控制、列表过滤、空格快捷键、歌词高亮、后台歌词重试
- `LyricsService`
  - 先查数据库缓存，再查本地 `.lrc`，最后请求 LRCLIB
  - 不引入额外 JSON 库，当前是手写 JSON 字段提取
- `MusicDatabase`
  - SQLite 两张表：
    - `tracks`
    - `lyrics`
- `Track`
  - 初始按文件名 `歌手 - 歌名` 推断
  - 播放后若媒体元数据存在，会覆盖初始推断
- 歌词重试
  - 当前重试间隔：`10, 20, 30, 60` 秒
  - 切歌时取消旧任务，避免旧歌词结果覆盖当前歌曲

## 6. 构建与运行

- 命令行编译：
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\run.ps1 compileJava
  ```
- 命令行运行：
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\run.ps1 run
  ```
- `run.ps1` 行为：
  - 如果本地没有可用 Gradle，会下载到 `.tools`
  - 当前使用 Gradle `9.6.1`

## 7. IDEA 相关状态

- 项目可以被 IDEA 正常识别
- 已解决过的点：
  - `gradle.properties` 指定本机 JDK 25，避免 IDEA Gradle 同步找不到 toolchain
- 当前经验结论：
  - 正确运行方式：IDEA 内执行 `gradle run`
  - 不可靠方式：直接跑 `MusicPlayerApp.main()` 这个普通 main 配置，之前验证过会失败
- 曾出现过 `.idea/vcs.xml` 多配 `$PROJECT_DIR$/.git` 的问题，用户已自行修好

## 8. Java 25 native access 警告处理

`build.gradle` 里已经加了：

```gradle
applicationDefaultJvmArgs = [
        "--enable-native-access=ALL-UNNAMED",
        "--enable-native-access=javafx.graphics",
        "--enable-native-access=javafx.media"
]
```

用途：
- 处理 SQLite JDBC 警告
- 处理 JavaFX graphics 警告
- 处理 JavaFX media 警告

## 9. 本地数据与忽略规则

- 数据库文件：
  - `music-player.db`
- 该文件已被 `.gitignore` 忽略，不会进入仓库
- 同时忽略：
  - `.gradle/`
  - `.idea/`
  - `.tools/`
  - `build/`

## 10. 已知局限 / 技术债

- 歌词 JSON 解析是手写的，能用，但不够优雅
- 没有歌词来源插件化接口，当前来源写死在 `LyricsService`
- 没有专辑封面/背景图
- 没有播放列表持久化排序或多歌单
- 没有测试代码
- 没有封面缓存
- 没有手动导入 `.lrc`
- SQLite 路径固定为项目根目录下的 `music-player.db`

## 11. 用户偏好与协作约束

- 用户明确要求：
  - 要诚实
  - 动文件之前一定要问，尤其是删除文件
- 这条约束在后续继续有效
- 本次项目里，凡是写文件前都已先征得同意
- 以后如果要：
  - 改源码
  - 加文件
  - 删文件
  - 改 Git 结构
  都应该先问用户

## 12. 用户曾要求的方向

- 用户多次要求：
  - 针对酷狗、QQ 音乐、网易云写爬虫抓歌词
  - 绕过反爬
- 这些没有实现
- 当前项目仍然只使用：
  - 本地 `.lrc`
  - 数据库缓存
  - LRCLIB

## 13. 下一步最合理的开发方向

优先级建议：
1. 读取音频内嵌封面并显示为歌词区背景
2. 手动导入 `.lrc`
3. 歌词来源插件化（`LyricsProvider`）
4. 用正式 JSON 库替换手写解析
5. 增加基本测试
6. 改善数据库 schema，比如缓存封面、歌曲时长、最近播放时间

## 14. 给下一个 AI 的一句话总结

这是一个已经能编译、能运行、能在 IDEA 里通过 `gradle run` 启动的 JavaFX 本地音乐播放器；当前核心工作应围绕“封面背景、歌词来源插件化、手动导入歌词、测试和代码稳固”继续推进，不要误以为网页爬虫歌词源已经做过。
