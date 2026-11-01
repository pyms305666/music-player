package app.musicplayer.config;

import java.util.List;

/** 选择桌面三栏界面或共享业务逻辑的移动竖屏界面。 */
public enum LayoutMode {
    DESKTOP,
    MOBILE;

    /** 准备或解析可实际使用的地址、路径或运行配置。 */
    public static LayoutMode resolve(List<String> arguments, boolean mobileProperty) {
        return resolve(arguments, mobileProperty, System.getProperty("javafx.platform"));
    }

    /** 准备或解析可实际使用的地址、路径或运行配置。 */
    static LayoutMode resolve(List<String> arguments, boolean mobileProperty, String platform) {
        if (mobileProperty || "android".equalsIgnoreCase(platform)) {
            return MOBILE;
        }
        List<String> safeArguments = arguments == null ? List.of() : arguments;
        return safeArguments.stream().anyMatch("--mobile"::equalsIgnoreCase) ? MOBILE : DESKTOP;
    }

    /** 检查是否使用 JavaFX 手机比例布局。 */
    public boolean isMobile() {
        return this == MOBILE;
    }
}
