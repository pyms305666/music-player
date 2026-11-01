package app.musicplayer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * LayoutMode 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：解析桌面或手机比例布局选项。控制 JavaFX 窗口布局，与原生 Android
 * 应用是不同的运行入口。
 */
class LayoutModeTest {
    /** 验证没有选项时使用桌面布局。 */
    @Test
    void defaultsToDesktop() {
        assertEquals(LayoutMode.DESKTOP, LayoutMode.resolve(List.of(), false));
    }

    /** 验证命令行参数或系统属性可启用手机比例布局。 */
    @Test
    void enablesMobileModeFromArgumentOrProperty() {
        assertEquals(LayoutMode.MOBILE, LayoutMode.resolve(List.of("--mobile"), false));
        assertEquals(LayoutMode.MOBILE, LayoutMode.resolve(List.of(), true));
    }

    /** 验证 Android 环境下的布局选项解析。 */
    @Test
    void enablesMobileModeOnAndroid() {
        assertEquals(LayoutMode.MOBILE, LayoutMode.resolve(List.of(), false, "android"));
        assertEquals(LayoutMode.DESKTOP, LayoutMode.resolve(List.of(), false, "windows"));
    }
}
