package app.musicplayer.config;

import java.util.List;

/** 选择桌面三栏界面或共享业务逻辑的移动竖屏界面。 */
public enum LayoutMode {
    DESKTOP,
    MOBILE;

    public static LayoutMode resolve(List<String> arguments, boolean mobileProperty) {
        return resolve(arguments, mobileProperty, System.getProperty("javafx.platform"));
    }

    static LayoutMode resolve(List<String> arguments, boolean mobileProperty, String platform) {
        if (mobileProperty || "android".equalsIgnoreCase(platform)) {
            return MOBILE;
        }
        List<String> safeArguments = arguments == null ? List.of() : arguments;
        return safeArguments.stream().anyMatch("--mobile"::equalsIgnoreCase) ? MOBILE : DESKTOP;
    }

    public boolean isMobile() {
        return this == MOBILE;
    }
}
