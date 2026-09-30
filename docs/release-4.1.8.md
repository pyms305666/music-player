第三轮：维护与发布。

- Windows 与 Android 直接依赖同一 Java 17 共享模块，移除构建时复制源码。
- 单一版本配置；Gradle Wrapper 对齐并校验发行包 SHA-256。
- CI 构建真实 JavaFX Windows 安装包和原生 Android APK，运行共享测试、Android lint 与模拟器播放回归。
- 提供正式 Android 签名配置；本次 GitHub APK 继续使用原调试证书，支持原版本覆盖升级。
- 发布前检查源码提交、版本、签名、附件散列和对应提交的 CI；新增 build-info.json 与 SHA256SUMS.txt。
- 保持桌面、手机版界面布局和数据兼容。

Android 附件为调试签名渠道版本。CI 生成的测试 APK 使用临时证书，不用于已安装版本覆盖升级。
