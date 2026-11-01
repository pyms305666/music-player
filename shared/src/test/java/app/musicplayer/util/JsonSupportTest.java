package app.musicplayer.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * JsonSupport 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：共享响应文本及轻量 JSON 辅助。处理 URL 编码、字段片段、HTML 实体和 Base64
 * 文本，支持来源响应解析。
 */
class JsonSupportTest {
    /** 验证读取嵌套字段和转义文本。 */
    @Test
    void readsNestedValuesAndEscapes() {
        String json = "{\"name\":\"天黑黑\\n现场\",\"id\":9272,\"data\":[{\"x\":1},{\"x\":2}]}";

        assertEquals("天黑黑\n现场", JsonSupport.stringValue(json, "name"));
        assertEquals("9272", JsonSupport.numberValue(json, "id"));
        assertEquals(
                2, JsonSupport.splitTopLevelObjects(JsonSupport.arrayValue(json, "data")).size());
        assertNull(JsonSupport.stringValue(json, "missing"));
    }

    /** 验证 HTML 实体和 Base64 文本解码。 */
    @Test
    void decodesHtmlAndBase64() {
        assertEquals("A&B", JsonSupport.htmlDecode("A&amp;B"));
        assertEquals("歌词", JsonSupport.decodeBase64Text("5q2M6K+N"));
    }
}
