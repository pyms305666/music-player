package app.musicplayer.online;

import app.musicplayer.playlist.NamedPlaylist;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaylistImportTest {
    @Test void extractsShareTextAndNormalizesThreeSources(){
        var ne=PlaylistLinks.parse(PlaylistLinks.extract("分享歌单：https://music.163.com/#/playlist?id=123&userid=456）"));
        assertEquals("123",ne.id());assertEquals("网易云音乐",ne.source());
        assertEquals("987",PlaylistLinks.parse("https://y.qq.com/n/ryqq/playlist/987").id());
        var kg=PlaylistLinks.parse("https://m.kugou.com/songlist/gcid_abc/?src_cid=abc&uid=9");
        assertEquals("gcid_abc",kg.id());assertTrue(kg.url().contains("src_cid=abc"));assertFalse(kg.url().contains("uid="));
        assertThrows(IllegalArgumentException.class,() -> PlaylistLinks.extract("https://localhost/playlist?id=1"));
        assertThrows(IllegalArgumentException.class,() -> PlaylistLinks.parse("https://y.qq.com/n/ryqq/songDetail/abc"));
        assertThrows(IllegalArgumentException.class,() -> PlaylistLinks.extract("https://music.163.com.evil.test/playlist?id=1"));
    }
    @Test void neteaseFetchesMissingDetailsAndKeepsIdOrderAndUnavailableItems(){
        List<String> calls=new ArrayList<>();
        try(var service=new PlaylistImportService((url,ref) -> {calls.add(url);
            return url.contains("playlist/detail") ? """
                {"code":200,"playlist":{"id":123,"name":"清单","trackCount":3,"creator":{"nickname":"我","id":9},
                "trackIds":[{"id":2},{"id":1},{"id":3}],"tracks":[{"id":1,"name":"原曲","ar":[{"name":"歌手"}],"al":{"name":"专辑","id":88},"dt":123456}]}}
                """ : """
                {"code":200,"songs":[{"id":2,"name":"现场版 (Live)","artists":[{"name":"A"},{"name":"B"}],"album":{"name":"演唱会"},"duration":234567}]}
                """;
        })){
            var p=service.load("https://music.163.com/playlist?id=123");assertEquals(2,calls.size());assertTrue(p.complete());
            assertEquals(List.of("2","1","3"),p.entries().stream().map(e -> e.track().primaryId()).toList());
            assertEquals("A、B",p.entries().get(0).track().artist());assertEquals(234567,p.entries().get(0).durationMillis());
            assertEquals(NamedPlaylist.State.FAILED,p.entries().get(2).state());
        }
    }
    @Test void qqStopsRepeatedPagesAndReportsPartialCount(){
        int[] calls={0};try(var service=new PlaylistImportService((url,ref) -> {calls[0]++;return """
            {"code":0,"cdlist":[{"dissname":"清单","songnum":300,"songlist":[{"songmid":"mid1","songid":1,"songname":"歌","singer":[{"name":"歌手"}],"interval":120}]}]}
            """;})){
            var p=service.load("https://y.qq.com/n/ryqq/playlist/1");assertEquals(2,calls[0]);assertEquals(1,p.entries().size());assertEquals(300,p.expectedCount());assertFalse(p.complete());
        }
    }
    @Test void qqSubtitlePreservesVersionForChannelMatching(){
        var song=PlaylistImportService.object("{\"mid\":\"a\",\"name\":\"歌曲\",\"subtitle\":\"(Live)\",\"singer\":[{\"name\":\"歌手\"}]}");
        assertEquals("歌曲 (Live)",PlaylistImportService.qqSong(song).track().title());
    }
    @Test void kugouCurrentMobileShareKeepsNineTracksVersionsAndDuration() throws Exception {
        String html;try(var input=getClass().getResourceAsStream("/playlist/kugou-mobile.html")){assertNotNull(input);html=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        var p=PlaylistImportService.parseKugouHtml(PlaylistLinks.parse("https://m.kugou.com/songlist/gcid_test/?src_cid=test"),html);
        assertEquals(9,p.entries().size());assertEquals("我的2023年度歌单",p.name());assertTrue(p.complete());
        assertEquals("毛不易",p.entries().get(0).track().artist());assertEquals("消愁 (2018王者荣耀三周年音乐盛典现场)",p.entries().get(0).track().title());
        assertEquals(250096,p.entries().get(0).durationMillis());
    }
    @Test void loginErrorsAndHomepageRedirectAreNotEmptySuccessfulPlaylists(){
        try(var service=new PlaylistImportService((u,r) -> "{\"code\":401}")){
            assertThrows(IllegalStateException.class,() -> service.load("https://music.163.com/playlist?id=1"));
        }
        assertThrows(IllegalStateException.class,() -> PlaylistImportService.parseKugouHtml(PlaylistLinks.parse("https://www.kugou.com/yy/special/single/12.html"),"<html>首页</html>"));
    }
}
