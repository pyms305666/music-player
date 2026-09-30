package app.musicplayer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonSupportTest {
    @Test
    void readsNestedValuesAndEscapes() {
        String json = "{\"name\":\"天黑黑\\n现场\",\"id\":9272,\"data\":[{\"x\":1},{\"x\":2}]}";

        assertEquals("天黑黑\n现场", JsonSupport.stringValue(json, "name"));
        assertEquals("9272", JsonSupport.numberValue(json, "id"));
        assertEquals(2, JsonSupport.splitTopLevelObjects(JsonSupport.arrayValue(json, "data")).size());
        assertNull(JsonSupport.stringValue(json, "missing"));
    }

    @Test
    void decodesHtmlAndBase64() {
        assertEquals("A&B", JsonSupport.htmlDecode("A&amp;B"));
        assertEquals("歌词", JsonSupport.decodeBase64Text("5q2M6K+N"));
    }
}
