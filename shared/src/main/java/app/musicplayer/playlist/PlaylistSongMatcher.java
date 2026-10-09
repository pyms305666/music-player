package app.musicplayer.playlist;

import app.musicplayer.model.OnlineTrackInfo;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Playlist metadata identifies the requested recording; download channels are independent. */
public final class PlaylistSongMatcher {
    private static final Pattern METADATA=Pattern.compile("\\((live(?:版)?|现场(?:版)?|cover:[^()]+)\\)");
    private PlaylistSongMatcher() { }
    public static boolean matches(OnlineTrackInfo expected,OnlineTrackInfo candidate){
        if(expected.primaryId()!=null&&!expected.primaryId().isBlank()&&expected.identity().equals(candidate.identity()))return true;
        if(!known(expected.artist())||!known(candidate.artist())||!artists(expected.artist()).equals(artists(candidate.artist())))return false;
        String title=normalize(expected.title()),other=normalize(candidate.title());if(title.isBlank())return false;
        String album=normalize(expected.album()),otherAlbum=normalize(candidate.album());
        boolean sameAlbum=!album.isBlank()&&album.equals(otherAlbum);
        if(title.equals(other)){
            // A named performance must not be replaced by a different concert with the same "Live" title.
            if(live(title)&&!album.isBlank()&&!sameAlbum)return false;
            return true;
        }
        if(!sameAlbum)return false;
        boolean cover=title.contains("(cover:"),otherCover=other.contains("(cover:");
        if(cover&&otherCover)return false;
        if(live(title)!=live(other)&&(live(title)?other:title).contains("("))return false;
        // Some catalogs put Live in the album, or add the original artist as a cover credit.
        // Only reconcile those annotations with the SAME performer and an explicit identical album.
        return !metadataFree(title).isBlank()&&metadataFree(title).equals(metadataFree(other));
    }
    private static boolean live(String title){return title.contains("(live)")||title.contains("(live版)")||title.contains("(现场)")||title.contains("(现场版)");}
    private static String metadataFree(String title){return METADATA.matcher(title).replaceAll("");}
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
