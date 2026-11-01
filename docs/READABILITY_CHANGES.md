# 可读性整理与验证记录

## 范围和基线

本轮以提交 `aa0b294ec9a19cc01372c02010bafd8b6ad06301` 的工作区为基线。开始时 Git 工作区干净，312 个跟踪文件；首次物理扫描记录 103,993 个文件及 7,542 个目录。交付条目数量以最后生成的 PROJECT_INVENTORY.html 为准，包含测试和本轮证据产生的路径。

改动前执行根目录 `verify.ps1 -SkipClean` 与 `android-app/verify.ps1 -SkipClean`，均通过。桌面 JUnit 报告有 44 项、1 项跳过、0 失败；shared 在桌面和 Android 两个独立输出中各有 120 项、0 失败。基线命令使用现有 Gradle 缓存，部分任务为 UP-TO-DATE。

## 正式代码变更

- Java 自写代码统一展开紧凑写法、分组导入并添加中文类与方法用途说明；重点标明异步结果有效性、借用资源、取消、缓存和释放约定。第三方 Wrapper 和 AOSP 源码保持原字节。
- `DesktopLyricsController` 接管桌面歌词请求、10 / 20 / 30 / 60 秒重试、请求编号及调度器关闭；MusicPlayerApp 继续持有歌词服务和界面。
- `DesktopLibraryController` 接管曲库扫描、恢复、保存的串行后台队列；其他数据库工作借用同一队列，关闭时先完成已提交写入，再运行最终清理。
- `AndroidLyricsController` 接管缓存、FILE 同名 LRC 和在线歌词的选择、UTF-8 / GB18030 读取及过期 UI 回调；MainActivity 保留页面生命周期及借用资源管理。
- `DownloadedTrackPublisher` 接管 Android 单曲的私有文件及 MediaStore 保存；保留同名保护、IS_PENDING 发布、失败记录撤销与暂存文件释放。歌单 SAF 发布仍由 AndroidPlaylistFiles 处理，AndroidTrackFiles 提供文件及元数据规则。
- `AudioDownloadTransport` 与 `FallbackTrackMatcher` 从 MusicCrawler 分离传输和跨来源名称匹配；MusicCrawler 保留预算、来源优先级、取消编排和临时文件收尾。
- Python 补充模块、类和函数说明并展开紧凑写法；整理前后去除文档字符串的可执行 AST 对比保持一致。PowerShell 以 PSScriptAnalyzer 整理排版，词法核对仅允许空白、注释和语句分隔换行变化，保留字符串与命令参数。
- Gradle、XML、CSS 和 CI 增加职责说明；Gradle 紧凑配置展开。版本、数据库结构、公共服务接口、UI 资源值、构建与签名渠道沿用原约定。
- 添加 PROJECT_GUIDE.md、离线全量附录及标准库生成器。生成器读取人工职责索引，独立扫描核对全部路径，遇到遗漏和无法扫描的目录会失败。

## 本地诊断代码单独变更记录

修改位置保持在原 `.tools` 下，不强制加入 Git。首次记录的 59 个本地源码候选包含 4 个生成副本；实际整理其余 55 个自写源码文件。逐文件用途和入口均在 PROJECT_GUIDE.md 中说明。

- `.tools/performance`：性能、HPROF、通知、传输及历史发布诊断补中文用途说明、展开 Java / Python / PowerShell 写法；原版本、输入文件路径及命令行为保留。
- `.tools/playlist-diagnostic`、`.tools/playlist-qa`、`.tools/playlist-verifier`：歌单探针和设备 QA 脚本补职责及函数说明，沿用同目录夹具、参数和入口。
- `.tools/release-4.2.3`、`.tools/signing-qa` 及 `.tools` 根目录自写脚本：只整理可读性；未执行历史发布、真机安装、权限设置等副作用操作。
- `NativeControlsProbe.ps1` 原来以整段紧凑字符串替换 Java 夹具，排版后会匹配失败。改为按 Java 词法片段匹配，忽略源码排版空白、保持字符串内容一致，并要求每个片段恰好匹配一次。生成片段本身也展开为多行，按原插入位置缩进。
- `run-androidtransport-jvm.py` 通过生成字符串补充 AndroidJUnit4 桩的中文说明，明确 UTF-8 写入；不手工编辑已有生成副本。三份历史 NativeControls Java 副本和一个 AndroidJUnit4 桩已恢复为基线原字节。
- `packaged-runtime-smoke.ps1`、正式 `desktop-smoke.ps1` 和 `android-signing.py` 的直接 javac 调用补 `-encoding UTF-8`，使中文注释在 JDK 17 的本机默认字符集下仍可编译。

本轮工作所用的备份、一次性整理器、格式化工具及日志在忽略目录 `build/readability`，全量附录逐项列出；正式清单生成器不依赖这些临时工具。

## 验证结果

| 检查 | 实际结果 | 证据位置 |
| --- | --- | --- |
| 桌面及共享构建验证 | 桌面 49 项：0 失败、1 项跳过；shared 桌面输出 124 项全部通过；installDist、构建来源、签名拒绝分支和 Release 状态检查通过 | `build/readability/desktop-final.log` 与对应 `test-results` |
| Android 构建及 lint | shared Android 输出 124 项全部通过；原生 APK、测试 APK 与相关 Java 编译通过；lint 0 错误、33 警告 | `build/readability/android-final.log`、`android-app/app/build/reports/lint-results-debug.xml` |
| 桌面播放烟雾 | 异步恢复、慢 I/O 响应、最新选择、最小化播放、循环、暂停恢复、曲库筛选、在线进度、取消、重试和缓存通过 | `build/readability/desktop-smoke.log` |
| 桌面歌单烟雾 | 持久化、去重追加、预览、复用、重导入、关闭重开及安全删除通过；15 个真实控件动作通过 | `build/readability/playlist-smoke.log` |
| 本地 NativeControls 入口 | 更新后的词法替换实际生成、编译并通过全部 15 个控件动作；后续只调整生成字符串排版，再单独生成编译检查通过 | `build/readability/native-controls.log`、`powershell-static.log` |
| Android 独立设备回归 | 新建 API 35 x86_64 AVD `za_readability_qa_20261011`，只在 `emulator-5558` 安装；歌词、保存、导入、歌单、列表、播放服务共 40 项通过，HTTP 取消及 Cookie 会话 1 项通过 | `build/readability/android-instrumentation.log`、`android-transport-instrumentation.log` |
| 组件边界 | 新测试覆盖过期回调、歌词重试与取消、调度器所有权、曲库写完再关库、媒体发布失败回滚、重名内容保留及换源匹配 | 六个新增组件测试文件及上述测试报告 |
| Java 注释及行为等价 | 206 个自写 Java 文件语法检查通过，1,826 个显式类型 / 方法 / 构造声明有中文说明；除三个职责拆分入口外，191 个原有 Java 文件可执行类型语法树与基线一致 | `build/readability/final-verification.json` |
| XML 和历史生成副本 | 49 个 XML 的解析资源值与基线一致；4 个历史生成源码副本与基线字节一致 | 同上 |
| 本地脚本静态检查 | 38 个 Python 文件 AST 解析通过；18 个 PowerShell 文件解析通过；发布、安装及真机操作脚本未因语法检查而执行 | `local-source-checks.json`、`powershell-static.log` |
| 本地 Java 编译 | 18 个自写探针按桌面或 SDK 平台及所需烟雾夹具编译，全部通过；AndroidJUnit4 生成桩另以 JDK 17 编译通过 | `build/readability/local-compiled/`、`generated-stub-check/` |
| 清单生成器 | 6 项回归通过，包括隐藏文件、空目录、中文、外部目录链接、HTML 转义、自写文件自动遗漏检测及生成后遗漏核对；目录链接用例实际运行，未跳过 | `build/readability/inventory-tests-final.log` |
| 十万级浏览器界面 | 113,001 个虚构条目加载成功；每页仅 100 个 DOM 条目，路径搜索、分类与类型组合筛选、200 项分页、末页禁用、特殊文本转义通过 | 使用相同 HTML 模板的内存测试页；结果见下面的限制说明 |

桌面跳过项是 Windows 环境下现有的真实符号链接缓存用例，基线同样跳过；清单生成器的目录链接用例实际以 Windows junction 验证了不跟随目标。Android 回归使用新模拟器及独立数据库、随机命名音频或测试夹具，未连接、安装或改动个人真机。测试后已关闭模拟器、临时回环测试服务及本轮 O: 路径映射；专用 AVD 保留供复现。

**代理浏览器检查限制：** 内置浏览器安全策略拒绝 `file://`，仅允许 HTTP / HTTPS，代理没有直接用浏览器打开真实附录。没有把真实附录改用服务器绕过限制。代理的浏览器交互检查使用只有虚构路径的独立内存页面，不提供文件读取、真实清单或目录访问。最终 HTML 的真实路径集合、JSON、转义、内联脚本及无外部依赖由静态检查与生成器核对；十万级交互结果不能冒充真实附录的离线打开结果。真实文件的本机离线操作由用户在 Edge 中检查，具体反馈见下面的人工验收记录。

远端 CI、正式发布、正式签名安装流程及历史真机 QA 未在本轮重新运行。CI 配置只增加职责注释，构建及签名门禁在本地执行。最终附录在所有代码及测试完成后重新生成，扫描时间、文件 / 目录 / 链接数量显示在页面顶部，`--check` 再核对同一工作区全部路径。

## 2026-10-11 收尾核对与真实文件人工验收

本轮开始时，旧快照与实际路径集合相差 425 个新增条目和 4 个已删除条目，全部位于 `.git`。已重新生成包含 `.git` 的完整附录；没有排除这些变化路径。Git 对象和快照引用也属于清单范围，工作区后续操作可能改变它们，所以附录始终表示页面所标扫描时间的路径快照。

### 本轮自动检查

| 检查 | 实际结果 |
| --- | --- |
| 候选附录生成及独立扫描 | 2026-10-11 22:42:34（北京时间），113,303 项：105,460 个文件、7,843 个目录；目录与文件各占一项 |
| 候选附录 `--check` | 通过；路径集合无遗漏、无多余、无重复，职责索引检查通过 |
| 主说明核对 | 409 个文件说明、190 个目录说明、5 张 Mermaid 图；本地链接无缺失，逐文件详解小节完整 |
| `git diff --check` | 通过，无空白错误 |
| 文档更新后的附录生成与覆盖核对 | 2026-10-11 22:46:31（北京时间），113,304 项；生成后的独立扫描及 `--check` 均通过 |
| 实际 HTML 静态检查 | UTF-8 解码、字段类型、计数一致性、控件标识及 Node.js 内嵌 JavaScript 语法检查通过；无外部加载资源，主说明链接目标存在。此项未执行浏览器渲染 |

本轮只刷新附录和补充验收记录，未修改生成器或业务源码，沿用上面的代码回归基线。最后一次生成在文档写入完成后执行，最终扫描时间与数量以 [PROJECT_INVENTORY.html](PROJECT_INVENTORY.html) 顶部为准。自动收尾的命令、退出码、扫描数量和附录 SHA-256 保存于 `build/readability/inventory-closeout.log`，便于区分人工检查的文件版本。

### 真实文件离线验收：用户反馈正常

用户已在本机 Edge 直接打开实际附录，并在本轮对话中反馈“正常”，随后提供浏览器版本和页面扫描时间，另行确认“已断网”。下面结果来自用户反馈，代理没有直接观察本机浏览器。内置浏览器的 `file://` 限制及十万级合成数据测试说明继续适用。

- 浏览器名称及版本：Microsoft Edge 155.0.4283.45（用户提供）。
- 人工检查的页面扫描时间：`2026-10-11T22:46:31+08:00`。
- 验收环境：本机实际 HTML 文件；用户另行确认检查时已断网。
- 已检查文件的 SHA-256：`70b121493c82f5b416046ca211d2936e963c102c7b5010480060121f2084e72b`。

| 人工检查项 | 预期结果 | 实际结果 |
| --- | --- | --- |
| 首页加载 | 显示扫描时间及总数，没有错误提示 | 用户反馈正常 |
| 搜索 | `MainActivity.java`、`歌词`、`.git` 均能显示对应条目 | 用户反馈正常 |
| 组合筛选与重置 | 分类、类型、所属组件可组合，重置恢复全部条目 | 用户反馈正常 |
| 分页 | 50 / 100 / 200 项、前后页、跳页及末页按钮状态正常 | 用户反馈正常 |
| 中文与主说明链接 | 路径、说明显示正常，链接能找到主说明文件 | 用户反馈正常 |
| 最终附录复核 | 最后一次生成后，重新打开首页并核对最终扫描时间 | 见 `build/readability/inventory-closeout.log` 中的 `Final snapshot review`；尚未反馈时该记录为 pending |

本轮没有收到异常反馈，也没有修改 HTML 模板、生成器或业务代码。记录写入后再次生成附录并核对全量路径；已验收版本与最后一次生成版本的时间及 SHA-256 分别保存，避免混淆。最后一次生成会刷新路径快照，最终首页复核需针对刷新后的文件反馈；已完成的实际文件离线操作检查保留为通过（用户反馈）。最终首页反馈追加到已存在的日志，不新增路径，也不继续修改本节，以免记录写入造成新的 Git 路径变化；反馈后仍执行一次覆盖检查。若后续出现问题，记录复现步骤、修复内容和相应回归；修改生成器时重新运行六项清单回归。

## 2026-10-13 保存桌面存储方案与说明更新

按用户要求新增 [DESKTOP_STORAGE_PLAN.md](DESKTOP_STORAGE_PLAN.md)，保存安装目录选择、音乐下载目录设置和旧歌曲迁移的完整待实施方案。迁移包括文件复制校验、曲库及歌词路径、歌单歌曲及目录路径、事务一致性、设置恢复和旧文件清理，不再把旧音乐迁移留作后续独立事项。

README 添加方案入口及当前目录行为说明；PROJECT_GUIDE 添加计划状态、逐文件职责和人工索引。全量附录在文档更新后重新扫描并覆盖检查。本次为文档维护，没有实现该方案的安装、设置或迁移功能，方案中的验收清单是后续要求；前面的构建与人工浏览器结果属于此前已记录的版本。

## 2026-10-13 实施 4.2.4 桌面存储功能

用户要求保存方案后开始改动项目。本节记录实际实施与验证，覆盖前一节“仅保存待实施方案”的后续工作；前面 2026-10-11 的可读性及浏览器结果仍是对应历史快照的证据。

### 实际功能与资源约定

- Windows EXE 增加 `--win-dir-chooser`，保留按用户安装、默认 ZA-Music 和升级 UUID。WiX 5.0.2 新增 UI 扩展依赖，本地预检查及 CI 准备步骤同时补齐；首次打包缺少该扩展的失败已修复。
- 主界面新增“下载位置”，移动竖屏预览提供同名菜单。首次单曲下载先选择并后台验证音乐文件夹，再确认保存；取消不改变当前播放器和默认目录。目录不可用提供重新选择，拒绝安装根内部和内部暂存位置，不静默改回 C 盘。
- 默认音乐目录独立保存；数据库、缓存和迁移备份继续由 AppPaths 管理。新歌单动态读取当前默认目录，已有独立歌单保留配置。单曲和歌单任务固定提交时的目标，后续更改设置不重定向任务。桌面歌单在目标盘 `.za-music-staging` 的任务目录暂存，导入、恢复和查重均排除它；Android 保留默认暂存策略。
- 修改目录时显示旧根、新根、数量、大小、可用空间和文件清单；默认勾选有旧歌曲的迁移。缺失及链接记录另行标出。确认后保存 PLANNED 并正常退出，下次启动在播放和下载服务之前独占恢复。系统文件夹选择输入可在测试注入，正式运行使用 DirectoryChooser。
- 文件迁移保留子目录、有效音频及配套 LRC；排除缓存、备份、内部暂存、链接和根外导入。目标盘暂存、大小和 SHA-256 校验、不覆盖发布、同名编号及同内容复用产生实际路径映射。源文件在曲库提交并持久化设置后逐项重新校验删除，不递归删除旧根。
- MusicDatabase 兼容新增迁移日志表和单活动计划索引；一次事务更新 tracks、lyrics、歌单条目 location、旧根内的真实歌单 directory 及 COMMITTED 标记，保留歌词、歌单身份、条目顺序、重复、状态及其他 JSON。复用目标记录时避免 INSERT OR REPLACE 级联删除目标歌词。
- SQLite 与 Preferences 分别提交：已提交标记是恢复依据，不能因设置失败回滚新曲库。提交前取消保留旧源与旧曲库；提交后取消只停止清理。目标离线、旧文件占用或内容改变时保留源文件，提供重试或明确保留旧副本。
- 新组件的职责、所有权与调用关系已写入 PROJECT_GUIDE：设置及迁移服务借用依赖，启动协调器关闭自建线程；正常 Session 在已排队曲库写入和歌单工作关闭后释放数据库及实例锁。锁失败提供关闭其他播放器后重试，不终止其他进程。

版本统一更新到 `4.2.4 / 40204`。正式 Android 页面与媒体保存策略未改动；共享扩展通过 Java 17 测试、Android 构建与 lint。没有向外上传发布，也没有安装到个人系统或移动个人音乐。

### 本轮验证

| 检查 | 实际结果 | 证据位置 |
| --- | --- | --- |
| 桌面 JUnit 和构建门禁 | 72 项，0 失败、0 错误、1 项已有 Windows 符号链接用例跳过；installDist、构建工具、签名拒绝分支和发布状态门禁通过 | `build/readability/storage-desktop-final.log`、桌面 test-results |
| shared 两套输出 | 桌面和 Android 各 126 项，全部通过 | `shared/build/simple-music-player/test-results/test`、`shared/build/simple-music-player-android/test-results/test` |
| Android 构建及 lint | 调试 APK、测试 APK 和 Java 17 编译通过；lint 0 错误、33 个已有警告 | `build/readability/storage-android-final.log`、lint-results-debug.xml |
| 新增存储单元回归 | 23 项：设置 2、迁移 19、实例锁 1、桌面暂存 1；真实 C/E 跨卷及两个硬中断子进程用例实际运行，未跳过 | `DownloadDirectorySettingsTest`、`DownloadMigrationServiceTest`、`StorageInstanceLockTest`、`DesktopPlaylistStagingTest` |
| 共享新增约定 | 2 项：默认 Publisher lambda 的暂存兼容；两个运行任务及一个排队任务始终使用提交时目录，同时验证提交读取最新歌单配置 | `PlaylistStagingPolicyTest`、`PlaylistDownloadsTest.queuedSongsKeepDirectoryCapturedAtSubmission` |
| 真实 JavaFX 存储交互 | 6 场景通过：锁占用提示及重试、首次选择取消、确认取消、仅改变未来位置、保存迁移正常退出、第二个独立 JVM 启动迁移并清源 | `build/readability/storage-ui-smoke-final.log` |
| 既有播放及歌单交互 | 桌面播放、下载进度/取消/重试、歌单持久化和全部 15 个控件动作通过 | `storage-playback-smoke-final.log`、`storage-playlist-smoke-final.log` |
| 新 EXE 与安装页只读检查 | JDK 25.0.2、WiX 5.0.2 构建成功；最终 MSI 含 InstallDirDlg、Folder PathEdit、ChangeFolder 按钮及 Welcome 下一步导航；ProductVersion 4.2.4，升级 UUID 保留 | `storage-package-final.log`、`storage-installer-check.json` |
| 清单工具及文档 | 6 个标准库清单回归通过；423 个自写文件、190 个目录的人工说明及 5 张主说明关系图，本地链接和逐文件小节核对通过 | `storage-inventory-tests-final.log`、guide-check.json |
| 脚本静态检查 | 修改的 package.ps1、desktop-smoke.ps1、新 desktop-storage-smoke.ps1 解析通过；Java 烟雾在 JDK 25 实际编译运行 | `storage-powershell-static.json`、对应烟雾日志 |

初版存储烟雾在同一 JVM 内 stop 后再次 start，曾遇到 Windows 音频句柄尚未释放；应用保留文件并给出待清理提示。正式迁移约定要求原进程退出，因此烟雾调整为两个独立 JVM，以真实验证进程退出、持久化和启动恢复。最终 6 场景日志记录成功结果；没有将旧同进程失败记录作为功能通过依据。构建门禁中的预期无效签名堆栈及 JavaFX classpath 警告不作为测试失败，实际退出码和 JUnit 结果分别核对。

### 安装包与尚未执行的检查

最终 Windows 包：[ZA音乐-4.2.4.exe](../build/installer/4.2.4-20261013-145118/ZA音乐-4.2.4.exe)。SHA-256：`1f583523dc83f0f9f7140e7792ed75acca496c591c7e18b09c57205222ec1481`。构建记录在同目录 `ZA音乐-4.2.4.exe.build.json`，本地源码尚有未提交改动；此包用于本地检查，不表示已发布版本。

本机未找到可直接使用的 Windows Sandbox 或 Hyper-V 隔离入口。本轮没有在个人系统实际执行新装、升级、卸载，也未验证安装向导中的不同盘、中文路径和卸载后的数据保留；已完成的是包构建与最终 MSI 数据表、生成 WiX 的只读检查。这些实际安装项仍须在隔离 Windows 环境验收。远端 CI、正式上传和 Android 仪器测试没有重新执行；此前 Android 独立设备回归保留为历史基线。

### 本轮附录刷新

代码、测试、构建、说明和检查文件完成后重新运行生成器及 `--check`，扫描 `.git`、隐藏目录、空目录、所有工具与失败/成功构建附件；全量路径集合无遗漏、无多余、无重复。最终数量、扫描时间和 SHA-256 写入 `build/readability/storage-inventory-closeout.log`，以实际生成结果为准。

本轮真实 HTML 通过 UTF-8、JSON、计数、字段、内嵌 JavaScript 语法、无外部资源和主说明链接检查。未重新用浏览器打开本轮附录，不能将 2026-10-11 用户在 Edge 155.0.4283.45 断网验收的旧快照当成本轮文件的人工结果。HTML 模板和生成器没有修改，十万级合成数据与此前离线检查记录保留原有说明。
