package app.musicplayer.playlist;

import app.musicplayer.model.OnlineTrackInfo;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaylistSongMatcherTest {
    private OnlineTrackInfo track(String source,String title,String artist,String id){return new OnlineTrackInfo(source,title,artist,"","",id,"");}
    @Test void acceptsFullWidthPunctuationSpacingAndArtistOrderButPreservesVersion(){
        var expected=track("酷狗音乐","歌曲 （Live）","甲、乙","kg");
        assertTrue(PlaylistSongMatcher.matches(expected,track("酷我音乐","歌曲(Live)","乙&甲","kw")));
        assertFalse(PlaylistSongMatcher.matches(expected,track("咪咕音乐","歌曲","甲、乙","mg")));
        assertFalse(PlaylistSongMatcher.matches(expected,track("咪咕音乐","歌曲(Live)","甲","mg")));
        assertFalse(PlaylistSongMatcher.matches(expected,track("咪咕音乐","歌曲(Remix)","甲、乙","mg")));
    }
    @Test void unknownArtistRequiresManualChoiceUnlessSourceIdentityMatches(){
        var expected=track("网易云音乐","歌曲","未知歌手","1");
        assertFalse(PlaylistSongMatcher.matches(expected,track("酷我音乐","歌曲","歌手","kw")));
        assertTrue(PlaylistSongMatcher.matches(expected,track("网易云音乐","歌曲","歌手","1")));
    }
    @Test void manualDifferentVersionDoesNotBecomeProofForOriginalSong(){
        var original=track("QQ音乐","歌曲","歌手","qq");var live=track("酷我音乐","歌曲 (Live)","歌手","kw");
        var entry=NamedPlaylist.Entry.create(original,0).downloadFrom(live).local("file");
        var matcher=new PlaylistDuplicates(PlaylistDuplicates.associations(entry));
        assertTrue(matcher.match(List.of(NamedPlaylist.Entry.create(original,0))).isEmpty());
        assertTrue(matcher.match(List.of(NamedPlaylist.Entry.create(live,0))).get(0).confirmed());
    }
}
