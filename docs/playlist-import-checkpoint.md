# 歌单导入暂停点：2026-10-08

用户要求先保存断点，明天继续。暂停期间不继续开发、测试、打包或发布，不创建定时任务。

2026-10-09 用户已要求继续，暂停结束。此文件后文保留 10 月 8 日的历史断点；最新进度见 docs/playlist-import.md 和 HANDOFF.md。原待办及最终独立审查已完成，审查发现的三项功能错误已修复并通过第二轮复核。公开发布仍需另行授权。

## 本轮要求与范围

- Windows 与 Android 导入酷狗、网易云、QQ 的公开歌单链接 / 分享文字，预览后选择曲目并保存独立歌单。
- 可选下载、指定目录及子目录检查；重复歌曲集中询问复用本地或保留两份，不覆盖原文件。
- 管理名称、搜索、排序、删除；按歌单顺序播放本地可用曲目、加入队列；下载并发 2，暂停、取消、重试，离开页面后继续，完成后不自动播放。
- 用户最新要求：音频下载使用本软件已有渠道，例如酷我、咪咕。当前实现优先酷我、咪咕，符合歌曲 / 歌手 / 版本的候选失败后尝试其他已有渠道；原歌单来源与实际下载来源分别保存。无法自动匹配时可人工选择版本。
- 用户允许联网研究；参考原项目和公开接口，独立实现适配，没有复制第三方代码。详见 playlist-import.md。
- 用户此前批准的是 4.1.12 发布；本轮 4.2.0 尚未获准公开发布，不沿用旧版的发布批准。

## 已完成及已核实

- 两端界面、共享导入器、独立歌单持久化、旧库迁移、目录重复判断、批量下载和播放队列接入均已编写。
- 实际读取用户《我的2023年度歌单》9 / 9、网易云样本 98 / 98、QQ 样本 30 / 30；这些数量是当日响应。
- 新版真实批量下载器并发下载用户酷狗歌单中的《和宇宙的温柔关联》和《追光者》，均通过酷我成功（10683244 / 9128355 字节）。原歌单保留酷狗来源，下载来源为酷我。桌面探针音频已清理。
- API 35 隔离模拟器验证了预览、只导入、读取音乐权限、SAF 持久授权、疑似重复确认、复用本地测试 WAV 和播放。测试 WAV 是人工生成的测试素材，不代表真实在线音频下载成功。
- 渠道调整后的 Android 实际页面下载《和宇宙的温柔关联》成功，显示“下载渠道：酷我音乐”。离开页面后任务继续，返回可看到完成项，未自动播放。证据位于忽略目录 .tools/playlist-qa/actual-channel.png 及对应 XML；真实音频保留在模拟器 QA-Playlist 目录。
- Android 人工选择版本列表可显示各渠道候选，保留 Live 等版本供用户核对；截图 .tools/playlist-qa/manual-versions-ready.png。
- 共享测试覆盖 1000 首、并发限制、暂停 / 取消 / 重试、重复行共用文件、显式保留两份、首个渠道失败转咪咕、严格版本匹配、实际下载信息持久化和数据库事务失败后重新启动任务。
- 桌面测试与新的真实 JavaFX 歌单烟测通过，含重复行 A、A、B 的播放顺序。原播放器烟测也已通过。
- Android 播放及数据回归一次 11 项中 10 项通过、个人曲库为空的真机专用项跳过；实际 SAF 授权的 4 项持久化检查另行全部通过。详细日志见 .tools/playlist-qa/instrumentation.txt 与 saf-instrumentation.txt。
- 渠道调整后的 Android assembleDebug、assembleDebugAndroidTest、lintDebug 与 shared:test 已通过。
- 最后一次 Android 构建（会话 18617）已完成，BUILD SUCCESSFUL：包含下述最新修复，但尚未安装到模拟器验证。

## 最新修改与尚未完成的检查

1. Android 主列表从 CHOICE_MODE_SINGLE 改为 CHOICE_MODE_NONE。原生 ListView 会在重新布局时重置自有 CheckBox 并清除勾选；现在由页面维护选中状态。需要安装最新 APK，并核实勾选在重新布局、打开和关闭人工版本选择后仍保留。
2. Windows 与 Android 人工版本搜索结束后调用 showEntries()，恢复下载进度摘要。需要核实关闭选择框后不残留“正在搜索”。Android 最新构建已包含此修复；桌面此最后一处小修改尚需重新编译。
3. 建议用最新 Android APK 运行 PlaylistPersistenceTest 的 4 项检查，以及 PlaybackServiceTest#libraryRefreshDoesNotReplaceNamedPlaylistOrderOrRepeatedRows；传入已授权的 playlistQaTree，可避免 SAF 用例跳过。
4. 补充最终验证记录：明确区分 10 通过 / 1 跳过、4 项 SAF 通过、渠道调整后的实际下载和最后修复的验证情况。
5. 实现检查完成后使用 check-work 技能要求的验证子代理，审查全部改动及未跟踪文件、边界与测试。该技能已读取，尚未执行本轮最终代理验证。提示词位置：C:/Users/22051/.agents/skills/check-work/SKILL.md。构建必须串行，避免两个 build-common.ps1 的临时 SUBST 盘符冲突。
6. 解决审查问题并完成对应验证，然后再决定是否生成本地预览安装包。正式 Android 签名必须沿用既有轮换与仓库外保险库，禁止生成新生产密钥；调试 APK 不覆盖用户正式版。未执行 Git 提交、推送或公开发布。

## 续做环境与命令

仓库 E:/codx project/音乐播放器，当前分支 codex/android-apk，原基线 d26fcc1893e59a5270973cf7a25918bc626bfff2。本轮源代码尚未提交，保留在工作区；开始续做时先查看 git status，不覆盖其他新增修改。

Windows 默认 PowerShell，可用 Python 处理复杂脚本。中文路径的 Gradle 构建必须使用辅助脚本，不同时启动多个构建：

```powershell
. ./scripts/build-common.ps1
Invoke-ZaGradle $PWD.Path '.' @('test',':shared:test','installDist')
Invoke-ZaGradle $PWD.Path 'android-app' @('assembleDebug','assembleDebugAndroidTest','lintDebug',':shared:test')
.\scripts\playlist-desktop-smoke.ps1
# 完整仓库校验（按需要运行）
.\verify.ps1 -SkipClean
```

- 隔离 AVD 名称 codex_playlist_35，路径 C:/Users/22051/.codex/android-qa/playlist-20261008，API 35 google_apis x86_64，320 × 640 / density 160。暂停时已关闭模拟器，QA 数据与目录授权保留在 AVD 磁盘中。
- 模拟器此前使用 V: 映射本仓库以避免 SDK 中文路径问题；暂停时解除该映射。续做时先确认 V: 空闲，再映射并启动，不接管他人的盘符或设备。
- 启动参数：-avd codex_playlist_35 -sysdir V:/.tools/android-sdk/system-images/android-35/google_apis/x86_64 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect -memory 2048 -cores 2。启动前核对实际路径；调用 V:/.tools/android-sdk/emulator/emulator.exe。
- 之前设备 serial 为 emulator-5554。真实手机目前未连接，不能宣称本轮已经真机验证。
- 已授予 QA 目录 content://com.android.externalstorage.documents/tree/primary%3AMusic%2FQA-Playlist；持久化测试参数名 playlistQaTree。
- UI 辅助脚本 .tools/playlist-qa/ui.py 支持 dump、tap、text、back、swipe、screenshot、logs。点击坐标须来自当前 UI XML；中文 PowerShell 参数可使用 JSON Unicode 转义。所有探针、截图及日志放在忽略目录 .tools，不加入源码提交。
- 用户酷狗分享测试链接：https://m.kugou.com/songlist/gcid_3zxiymv9z9z0fe/?src_cid=3zxiymv9z9z0fe&iszlist=1 （已移除用户跟踪参数）。

主要源文件：shared 的 playlist 包、PlaylistImportService / PlaylistLinks / OnlineMusicSearchService / MusicCrawler；Windows 的 PlaylistWindow / DesktopPlaylistFiles / MusicPlayerApp / MusicDatabase；Android 的 PlaylistActivity / PlaylistRuntime / AndroidPlaylistFiles / AndroidMusicDatabase / MainActivity。

实现说明与边界详见 docs/playlist-import.md；此文件记录暂停时的事实和待办，不表示功能已完成最终验收。
