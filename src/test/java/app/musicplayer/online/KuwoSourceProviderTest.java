package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KuwoSourceProviderTest {
    /** 基于 2026-09 实测 r.s 响应裁剪的单引号伪 JSON（条目内含嵌套花括号对象）。 */
    private static final String SEARCH_BODY =
            "{'HIT':'3600','HITMODE':'song','PN':'0','RN':'2','TOTAL':'3600',"
                    + "'abslist':["
                    + "{'AARTIST':'Jay&nbsp;Chou','ALBUM':'叶惠美','ALBUMID':'1293','ARTIST':'周杰伦',"
                    + "'ARTISTID':'336','DURATION':'269','MUSICRID':'MUSIC_228908','NAME':'晴天','PAY':'16711935',"
                    + "'payInfo':{'down':'1','download':'1','play':'1','vid':'8132306'},"
                    + "'quality':{'AR501':8,'DB':7,'F':3,'H':1,'S':2}},"
                    + "{'AARTIST':'','ALBUM':'','ALBUMID':'0','ARTIST':'蓝心羽','ARTISTID':'1122778',"
                    + "'DURATION':'232','MUSICRID':'MUSIC_78932517','SONGNAME':'晴天&nbsp;Cover','PAY':'8913032',"
                    + "'payInfo':{'down':'0','download':'0','play':'0','vid':'0'}},"
                    + "{'AARTIST':'','ALBUM':'叶惠美','ALBUMID':'1293','ARTIST':'周杰伦',"
                    + "'DURATION':'269','MUSICRID':'MUSIC_228908','NAME':'晴天','PAY':'16711935'}"
                    + "]}";

    @Test
    void parsesPseudoJsonSearchResults() {
        List<OnlineTrackInfo> results = KuwoSourceProvider.parseSearchResponse(SEARCH_BODY);

        assertEquals(2, results.size());
        OnlineTrackInfo first = results.get(0);
        assertEquals("酷我音乐", first.source());
        assertEquals("228908", first.primaryId());
        assertEquals("晴天", first.title());
        assertEquals("周杰伦", first.artist());
        assertEquals("叶惠美", first.album());
        assertEquals("1293", first.secondaryId());
    }

    @Test
    void stripsHtmlEntitiesFromText() {
        List<OnlineTrackInfo> results = KuwoSourceProvider.parseSearchResponse(SEARCH_BODY);

        assertEquals("晴天 Cover", results.get(1).title());
    }

    @Test
    void deduplicatesByRid() {
        List<OnlineTrackInfo> results = KuwoSourceProvider.parseSearchResponse(SEARCH_BODY);

        assertEquals(2, results.stream().map(OnlineTrackInfo::primaryId).distinct().count());
    }

    @Test
    void handlesBlankBody() {
        assertTrue(KuwoSourceProvider.parseSearchResponse(null).isEmpty());
        assertTrue(KuwoSourceProvider.parseSearchResponse("  ").isEmpty());
    }

    @Test
    void parsesCarPlayerPlayUrl() {
        String json = "{\"code\":200,\"data\":{\"bitrate\":320,\"duration\":269,\"format\":\"mp3\","
                + "\"p2p_audiosourceid\":\"2066566530106trackmediaM500000s9OL31Pcu9omp3\",\"rid\":22965611,"
                + "\"sig\":\"8314206873599217931\",\"type\":0,"
                + "\"url\":\"http://kw-er.kuwo.cn/9e3942e98c48c15d70b9b1930158a0ab/6aa4f2e8/resource/30106"
                + "/trackmedia/M500000s9OL31Pcu9o.mp3?bitrate$128\\u0026format$mp3\\u0026source$\"}}";

        assertEquals(
                "http://kw-er.kuwo.cn/9e3942e98c48c15d70b9b1930158a0ab/6aa4f2e8/resource/30106"
                        + "/trackmedia/M500000s9OL31Pcu9o.mp3?bitrate$128&format$mp3&source$",
                KuwoSourceProvider.parsePlayUrl(json));
    }

    @Test
    void returnsNullWhenPlayUrlMissing() {
        assertNull(KuwoSourceProvider.parsePlayUrl("{\"code\":500,\"msg\":\"error\"}"));
        assertNull(KuwoSourceProvider.parsePlayUrl(null));
    }
}
