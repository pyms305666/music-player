# 双端“幽蓝深空流光感”美术设计实施方案 (Deep Space Ambient Glow Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ZA音乐 Windows 桌面端（JavaFX）与 Android 手机端（原生 Android UI + Media3）的视觉体系完整升级为“幽蓝深空流光感”，实现高保真沉浸背景微光、磨砂质感卡片、自适应胶囊音质徽章与人体工学控制台。

**Architecture:** 
- **桌面端 (JavaFX 25)：** 重构 CSS 设计变量（深空石板色系 + 冰青强调色），在 `ArtworkPresenter` 引入异步轻量封面边缘取色管道以驱动右侧内容视口的 `RadialGradient` 环境柔光；曲库单元格增加轻量扩展名音质 Badge 渲染。
- **移动端 (Android API 26+)：** 迁移 `colors.xml` 与核心 Shape Drawable，将播放控制台与悬浮歌词卡片改造为半透明透光浮岛，在封面背底集成硬件加速矢量微光扩散层。

**Tech Stack:** Java 17/25, JavaFX 25, Gradle, Android SDK (API 26~34), Material Components, Media3.

**Spec:** `docs/superpowers/specs/2026-11-04-deep-space-ambient-glow-ui-design.md`

---

## Global Constraints

- **最低 API 与 JDK 标准：** 桌面端 JDK 25，Android 最低支持 API 26。
- **业务逻辑隔离：** 严禁修改 SQLite 数据库、网络解析 API、下载多任务队列与底层音频解码管道。
- **性能红线：** 任何列表滚动必须保持 60fps 顺滑，禁止在列表项中进行阻塞式 I/O、深度位图计算或复杂的高斯模糊滤镜。
- **颜色基准：** 背景基底 `#0B0E14` ~ `#131924`，主强调色 `#4FA5FF`，流光强调色 `#2CE6E6`，文字主色 `#F4F7FB`。

---

## Review Focus

1. **极端封面色调适应：** 当专辑封面为纯白色或纯黑色时，封面取色不可生成过亮刺眼或无对比度的背景光晕（需钳制亮度在 15%~35%）。
2. **纯歌词模式与常规模式切换：** 切换纯歌词模式时，JavaFX 隐藏封面后，视口背景的流光过渡不能出现闪烁或突兀白屏。
3. **曲库无后缀/特殊扩展名音频：** 当本地音频没有标准后缀或为未识别格式时，音质胶囊徽章优雅隐藏或显示简洁通用标识，不报异常。
4. **Android 小屏设备适配：** 歌词浮岛卡片在 720p 等小屏设备上不可挤压底部控制栏与顶部标题，需保持自适应权重。
5. **主题色彩一致性：** 桌面端与 Android 端的播放进度条滑块、高亮歌词色、音质徽章在两端呈现出统一的色调与视觉语言。

---

## Task Decomposition

### Task 1: 桌面端全局深空流光色彩体系与 CSS 重构 (JavaFX Tokens & Stylesheet)

**Files:**
- Modify: `src/main/resources/styles.css`
- Modify: `src/main/resources/styles-desktop.css`
- Modify: `src/main/resources/styles-playlists.css`

**Interfaces:**
- Consumes: `docs/superpowers/specs/2026-11-04-deep-space-ambient-glow-ui-design.md#5.1`
- Produces: 全局 CSS 类与变量名（`-app-background`, `-app-surface`, `-app-elevated`, `-app-accent`, `-app-accent-glow`, `.audio-badge-flac`, `.audio-badge-standard`）

- [ ] **Step 1: 编写 JavaFX 样式单元测试或静态校验规则**
  验证新的 CSS 文件无语法错误、所有 `-app-*` 自定义变量被正确声明并解析。
- [ ] **Step 2: 运行测试验证**
  运行：`.\gradlew test --tests *Style*` 或编译检查。
- [ ] **Step 3: 更新 `styles.css` 和 `styles-desktop.css`**
  - 将暗色基底由 `#101318` 调优为深空微冷石板蓝 `#0B0E14` / `#131924`；
  - 将高亮主色由 `#70aaff` 替换为鲜明纯澈的幽蓝冰青 `#4FA5FF` 与尾端流光 `#2CE6E6`；
  - 调整 Slider 轨槽、滑块圆润度及发光阴影；
  - 优化输入框、菜单与按钮的 8px~12px 圆角与 1px 微发光描边。
- [ ] **Step 4: 运行桌面端预览验证**
  运行：`.\run.ps1 run`，肉眼核对主界面色彩氛围、按钮悬停态与列表边距。
- [ ] **Step 5: Git 提交**
  ```bash
  git commit -m "style(desktop): update visual theme tokens to deep space ambient glow"
  ```

---

### Task 2: 桌面端曲库列表轻量音质胶囊徽章实现 (Track Format Badge)

**Files:**
- Create: `src/main/java/app/musicplayer/ui/AudioFormatBadge.java`
- Modify: `src/main/java/app/musicplayer/MusicPlayerApp.java`
- Test: `src/test/java/app/musicplayer/ui/AudioFormatBadgeTest.java`

**Interfaces:**
- Consumes: `Track.path()`
- Produces: `AudioFormatBadge.resolve(String path) -> AudioFormatInfo(String label, BadgeStyle style)`

- [ ] **Step 1: 编写音质徽章解析单元测试**
  ```java
  @Test
  void shouldResolveFlacBadgeForFlacExtension() {
      var badge = AudioFormatBadge.resolve("D:/Music/song.flac");
      assertEquals("FLAC", badge.label());
      assertTrue(badge.isLossless());
  }
  @Test
  void shouldResolveMp3BadgeForStandardAudio() {
      var badge = AudioFormatBadge.resolve("D:/Music/song.mp3");
      assertEquals("MP3", badge.label());
      assertFalse(badge.isLossless());
  }
  ```
- [ ] **Step 2: 运行测试确认失败**
  运行：`.\gradlew test --tests app.musicplayer.ui.AudioFormatBadgeTest`
  预期：编译失败，类不存在。
- [ ] **Step 3: 实现 `AudioFormatBadge.java` 并集成至曲库列表 Cell**
  - 基于字符串后缀快速判断 `.flac`, `.wav`, `.ape`（标为高亮冰青徽章）；`.mp3`, `.m4a`, `.aac`（标为低调微灰徽章）；
  - 在 `MusicPlayerApp.java` 的曲目列表单元格末尾追加该 Badge 节点。
- [ ] **Step 4: 运行测试并验证通过**
  运行：`.\gradlew test --tests app.musicplayer.ui.AudioFormatBadgeTest`
  预期：PASS。
- [ ] **Step 5: Git 提交**
  ```bash
  git commit -m "feat(desktop): add lightweight audio quality capsule badge in track list"
  ```

---

### Task 3: 桌面端封面动态流光取色与主视口环境光晕 (Desktop Ambient Glow)

**Files:**
- Modify: `src/main/java/app/musicplayer/artwork/ArtworkPresenter.java`
- Modify: `src/main/java/app/musicplayer/MusicPlayerApp.java`
- Test: `src/test/java/app/musicplayer/artwork/DominantColorExtractorTest.java`
- Create: `src/main/java/app/musicplayer/artwork/DominantColorExtractor.java`

**Interfaces:**
- Consumes: `javafx.scene.image.Image`
- Produces: `DominantColorExtractor.extract(Image image) -> Color`，安全映射至 15%~35% 亮度范围

- [ ] **Step 1: 编写封面主色采样测试**
  测试正常色彩采样、全白防过曝裁剪、全黑兜底默认幽蓝光晕。
- [ ] **Step 2: 运行测试确认失败**
  运行：`.\gradlew test --tests app.musicplayer.artwork.DominantColorExtractorTest`
- [ ] **Step 3: 实现 `DominantColorExtractor` 与 `ArtworkPresenter` 联动**
  - 异步按步长（如 16px）读取 `PixelReader` 采样平均色；
  - 限制 HSB 亮度在安全区间；
  - 回调 UI 线程，向右侧 `content` 面板设置 `RadialGradient` 动态背景。
- [ ] **Step 4: 运行测试与真机界面验证**
  运行：`.\run.ps1 run`，测试不同专辑封面切换时流光平滑变换效果及纯歌词模式表现。
- [ ] **Step 5: Git 提交**
  ```bash
  git commit -m "feat(desktop): implement dynamic ambient glow driven by artwork colors"
  ```

---

### Task 4: Android 移动端主题色谱与 Shape Drawable 全面升级 (Android Resources)

**Files:**
- Modify: `android-app/app/src/main/res/values/colors.xml`
- Modify: `android-app/app/src/main/res/values/styles.xml`
- Modify: `android-app/app/src/main/res/drawable/player_panel_background.xml`
- Modify: `android-app/app/src/main/res/drawable/field_background.xml`
- Modify: `android-app/app/src/main/res/drawable/artwork_background.xml`
- Modify: `android-app/app/src/main/res/drawable/play_button_background.xml`

**Interfaces:**
- Consumes: `@color/accent` (`#4FA5FF`), `@color/surface_glass` (`#CC131924`), 16dp 圆角规范
- Produces: 统一的深空流光移动端设计基准资源

- [ ] **Step 1: 修改 `colors.xml` 与 `styles.xml`**
  更新 `accent` 为 `#4FA5FF`，新增 `accent_glow` 为 `#2CE6E6`，调整暗色层阶；
- [ ] **Step 2: 重绘各控制部件 Drawable**
  - `play_button_background.xml`：渐变球体背景（`#4FA5FF` 到 `#2CE6E6`），带轻微发光内阴影；
  - `player_panel_background.xml`：半透明深空色加圆角；
  - 增加 `lyrics_island_background.xml` 悬浮卡片 Drawable。
- [ ] **Step 3: 执行 Android 资源编译检查**
  运行：`.\android-app\gradlew.bat assembleDebug`
- [ ] **Step 4: Git 提交**
  ```bash
  git commit -m "style(android): update color palette and glassmorphic drawables"
  ```

---

### Task 5: Android 歌词浮岛与沉浸播放主界面重构 (Android Layout & Player View)

**Files:**
- Modify: `android-app/app/src/main/res/layout/activity_main.xml`
- Modify: `android-app/app/src/main/res/layout/item_track.xml`
- Modify: `android-app/app/src/main/java/app/musicplayer/android/MainActivity.java`

**Interfaces:**
- Consumes: `lyrics_island_background.xml`, 硬件加速矢量微光
- Produces: 悬浮式磨砂歌词岛、带光晕的封面展示容器及列表胶囊徽章

- [ ] **Step 1: 重构 `activity_main.xml` 歌词与封面布局**
  - 将 `artworkView` 配置大圆角，其后附着 `artwork_glow_view`；
  - 将歌词滚动区域包裹在 `lyrics_island_background` 浮岛容器内；
  - 调整播放控制条各图标间距与大拇指触控区。
- [ ] **Step 2: 在 `item_track.xml` 中引入轻量音质胶囊徽章**
- [ ] **Step 3: 在 `MainActivity.java` 中绑定新控件并配置封面动态微光**
  - 当封面加载完成后，异步采样微光或启用微光渐变层；
  - 确保小屏幕分辨率下歌词卡片与底栏的弹性缩放无越界。
- [ ] **Step 4: 执行 Android 编译与验证**
  运行：`.\android-app\gradlew.bat assembleDebug`
- [ ] **Step 5: Git 提交**
  ```bash
  git commit -m "feat(android): implement floating lyrics island and ambient artwork glow"
  ```

---

### Task 6: 双端联动全量视觉走查与回归验证 (End-to-End Verification)

**Files:**
- Test / Verify: Windows 桌面端运行与 Android APK 构建

- [ ] **Step 1: 桌面端全量自动化回归与验证**
  运行：`powershell.exe -File .\verify.ps1`
  预期：全部现有桌面与共享模块离线测试通过。
- [ ] **Step 2: 桌面端 16:9 与竖屏预览视觉走查**
  运行：`.\run.ps1 run` 以及 `.\run.ps1 run '--args=--mobile'`
  核对：歌曲播放、歌词滚动、纯歌词模式、换源下载、流光平滑度。
- [ ] **Step 3: Android 端 Debug APK 构建与运行验证**
  运行：`powershell.exe -File .\android-app\build-apk.ps1`
  预期：APK 顺利打包构建成功。
- [ ] **Step 4: 归档版本记录**
  在 `docs/PROJECT_PROGRESS.md` 中记录本次视觉重构成果。
- [ ] **Step 5: 最终分支提交**
  ```bash
  git commit -m "chore: complete deep space ambient glow dual-end UI upgrade"
  ```
