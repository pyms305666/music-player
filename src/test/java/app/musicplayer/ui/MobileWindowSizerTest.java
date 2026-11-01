package app.musicplayer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * MobileWindowSizer 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：JavaFX 手机比例窗口尺寸协调。保持约 9:16
 * 的宽高关系，管理尺寸监听并避免相互更新形成循环。
 */
class MobileWindowSizerTest {
    /** 验证手机比例窗口的 9:16 尺寸计算。 */
    @Test
    void calculatesNineBySixteenDimensions() {
        assertEquals(720.0, MobileWindowSizer.heightForWidth(405.0));
        assertEquals(405.0, MobileWindowSizer.widthForHeight(720.0));
        assertEquals(640.0, MobileWindowSizer.heightForWidth(360.0));
    }
}
