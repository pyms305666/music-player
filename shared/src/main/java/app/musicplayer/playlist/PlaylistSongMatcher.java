package app.musicplayer.playlist;

import app.musicplayer.model.OnlineTrackInfo;
import java.text.Normalizer;
import java.util.*;

/** Playlist metadata identifies the requested recording; download channels are independent. */
public final class PlaylistSongMatcher {
    private PlaylistSongMatcher() { }
    public static boolean matches(OnlineTrackInfo expected,OnlineTrackInfo candidate){
        if(expected.primaryId()!=null&&!expected.primaryId().isBlank()&&expected.identity().equals(candidate.identity()))return true;
        return known(expected.artist())&&known(candidate.artist())&&!normalize(expected.title()).isBlank()
                &&normalize(expected.title()).equals(normalize(candidate.title()))
                &&artists(expected.artist()).equals(artists(candidate.artist()));
    }
    public static List<OnlineTrackInfo> ranked(OnlineTrackInfo expected,List<OnlineTrackInfo> results,boolean exactOnly){
        Map<String,OnlineTrackInfo> unique=new LinkedHashMap<>();
        for(var info:results)if(info.primaryId()!=null&&!info.primaryId().isBlank()&&(!exactOnly||matches(expected,info)))unique.putIfAbsent(info.identity(),info);
        return unique.values().stream().sorted(Comparator.comparingInt((OnlineTrackInfo info) -> matches(expected,info)?0:1)
                .thenComparingInt(info -> priority(info.source()))).toList();
    }
    private static int priority(String source){return switch(source){case "酷我音乐" -> 0;case "咪咕音乐" -> 1;case "QQ音乐" -> 2;case "网易云音乐" -> 3;default -> 4;};}
    private static boolean known(String artist){return artist!=null&&!artist.isBlank()&&!artist.equals("未知歌手");}
    private static Set<String> artists(String text){return new TreeSet<>(Arrays.asList(normalize(text).split("[、/&,+;，；]+")));}
    private static String normalize(String value){return Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+","");}
    public static String label(OnlineTrackInfo info){return info.source()+" · "+info.artist()+" - "+info.title()+(info.album()==null||info.album().isBlank()?"":" · "+info.album());}
}
