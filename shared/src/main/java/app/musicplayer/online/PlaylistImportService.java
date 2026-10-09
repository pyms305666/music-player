package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.playlist.NamedPlaylist;
import app.musicplayer.util.JsonSupport;
import com.google.gson.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Public playlist metadata only. Audio resolution remains a separate operation. */
public final class PlaylistImportService implements AutoCloseable {
    @FunctionalInterface interface Fetch { String get(String url,String referer) throws IOException,InterruptedException; }
    private final CrawlerSession session = new CrawlerSession();
    private final Fetch transport;
    private final OkHttpClient redirects = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .callTimeout(12, TimeUnit.SECONDS).build();
    public PlaylistImportService(){transport=(url,referer) -> session.fetch(url,referer,url.contains("m.kugou.com/songlist/")
            ? Map.of("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36") : Map.of());}
    PlaylistImportService(Fetch transport){this.transport=transport;}
    public NamedPlaylist load(String input) {
        return session.withinTimeout(90_000, () -> {
            try {
                String url = PlaylistLinks.extract(input); PlaylistLinks.Link link;
                try { link = PlaylistLinks.parse(url); }
                catch (IllegalArgumentException shortLink) { link = PlaylistLinks.parse(expand(url)); }
                return switch (link.source()) {
                    case "网易云音乐" -> netease(link);
                    case "QQ音乐" -> qq(link);
                    case "酷狗音乐" -> kugou(link);
                    default -> throw new IllegalArgumentException("不支持的歌单来源");
                };
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new IllegalStateException("读取歌单失败：" + error.getMessage(), error);
            }
        });
    }
    private String expand(String input) throws IOException {
        String url = input;
        for (int hop = 0; hop < 6; hop++) {
            PlaylistLinks.validate(url);
            try (var response = redirects.newCall(new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute()) {
                String target = response.header("Location");
                if (response.isRedirect() && target != null) { url = java.net.URI.create(url).resolve(target).toString(); continue; }
                return url;
            }
        }
        throw new IOException("分享链接跳转次数过多");
    }
    private NamedPlaylist netease(PlaylistLinks.Link link) throws IOException, InterruptedException {
        JsonObject root = object(transport.get("https://music.163.com/api/v3/playlist/detail?id=" + link.id() + "&n=1000", "https://music.163.com/"));
        requireCode(root,"200"); JsonObject p = required(root,"playlist");
        Map<String,JsonObject> songs = new HashMap<>();
        for (var element : array(p,"tracks")) { JsonObject song = element.getAsJsonObject(); songs.put(text(song,"id"), song); }
        JsonArray ids = array(p,"trackIds");
        if(ids.size()>20_000||array(p,"tracks").size()>20_000)throw new IllegalStateException("歌单超过 20000 首，请分批导入");
        if (!ids.isEmpty()) {
            List<String> missing = new ArrayList<>();
            for (var element : ids) { String id = text(element.getAsJsonObject(),"id"); if (!songs.containsKey(id)) missing.add(id); }
            for (int offset=0; offset<missing.size(); offset+=200) {
                var batch = missing.subList(offset,Math.min(offset+200,missing.size()));
                String encoded = JsonSupport.encode("[" + String.join(",",batch) + "]");
                JsonObject details = object(transport.get("https://music.163.com/api/song/detail?ids=" + encoded, "https://music.163.com/"));
                requireCode(details,"200");
                for (var element : array(details,"songs")) { JsonObject song = element.getAsJsonObject(); songs.put(text(song,"id"),song); }
            }
        }
        List<NamedPlaylist.Entry> entries = new ArrayList<>();
        if (ids.isEmpty()) for (var element : array(p,"tracks")) entries.add(neteaseSong(element.getAsJsonObject()));
        else for (var element : ids) {
            String id=text(element.getAsJsonObject(),"id"); JsonObject song=songs.get(id);
            entries.add(song == null ? NamedPlaylist.Entry.create(new OnlineTrackInfo("网易云音乐","歌曲 " + id,"未知歌手","","",id,""),0)
                    .status(NamedPlaylist.State.FAILED,"平台未提供歌曲信息") : neteaseSong(song));
        }
        return playlist(link,text(p,"name"),text(p,"coverImgUrl"),text(child(p,"creator"),"nickname"),
                count(p,"trackCount",entries.size()),entries);
    }
    static NamedPlaylist.Entry neteaseSong(JsonObject song) {
        JsonObject album = song.has("al") ? child(song,"al") : child(song,"album");
        JsonArray artists = song.has("ar") ? array(song,"ar") : array(song,"artists");
        var info = new OnlineTrackInfo("网易云音乐",text(song,"name"),names(artists,"name"),text(album,"name"),
                text(album,"picUrl"),text(song,"id"),"");
        return NamedPlaylist.Entry.create(info,number(song,song.has("dt") ? "dt" : "duration",0));
    }
    private NamedPlaylist qq(PlaylistLinks.Link link) throws IOException, InterruptedException {
        List<NamedPlaylist.Entry> entries = new ArrayList<>(); JsonObject header = null; int expected=0;
        Set<List<String>> pages=new HashSet<>();
        for (int offset=0; offset<20_000; offset+=100) {
            String endpoint="https://i.y.qq.com/qzone-music/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0"
                    + "&nosign=1&disstid=" + link.id() + "&format=json&song_begin=" + offset + "&song_num=100";
            JsonObject root=object(transport.get(endpoint,"https://y.qq.com/")); requireCode(root,"0");
            JsonArray lists=array(root,"cdlist"); if (lists.isEmpty()) throw new IllegalStateException("歌单不存在或需要登录");
            JsonObject p=lists.get(0).getAsJsonObject(); JsonArray rows=array(p,"songlist");
            if(header==null){header=p;expected=count(p,"total_song_num",count(p,"songnum",rows.size()));}
            List<NamedPlaylist.Entry> page=new ArrayList<>();for(var row:rows)page.add(qqSong(row.getAsJsonObject()));
            if(page.isEmpty()||!pages.add(page.stream().map(e -> e.track().identity()).toList()))break;
            entries.addAll(page);
            if(entries.size()>=expected)break;
        }
        return playlist(link,text(header,"dissname"),text(header,"logo"),text(header,"nickname"),expected,entries);
    }
    static NamedPlaylist.Entry qqSong(JsonObject song) {
        JsonObject album=child(song,"album");
        String mid=first(song,"songmid","mid"), title=first(song,"songname","name","title"), albumName=first(song,"albumname");
        String subtitle=text(song,"subtitle");if(!subtitle.isBlank()&&!title.contains(subtitle))title+=" "+subtitle;
        if(albumName.isBlank())albumName=text(album,"name");
        String albumMid=first(song,"albummid"); if(albumMid.isBlank())albumMid=text(album,"mid");
        return NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐",title,names(array(song,"singer"),"name"),albumName,
                albumMid.isBlank()?"":"https://y.gtimg.cn/music/photo_new/T002R300x300M000"+albumMid+".jpg",mid,first(song,"songid","id")),
                number(song,"interval",0)*1000);
    }
    private NamedPlaylist kugou(PlaylistLinks.Link link) throws IOException, InterruptedException {
        // Current public web collections embed ordered JSON. Old mobile endpoints now redirect to the homepage.
        String html=transport.get(link.url(),"https://www.kugou.com/");
        return parseKugouHtml(link,html);
    }
    static NamedPlaylist parseKugouHtml(PlaylistLinks.Link link,String html) {
        String output=assignment(html,"window\\.\\$output",false);
        if(output!=null){
            JsonObject root=object(output), info=required(root,"info"), p=required(info,"listinfo");
            List<NamedPlaylist.Entry> entries=new ArrayList<>();
            for(var row:array(info,"songs")){
                JsonObject song=row.getAsJsonObject();String title=first(song,"songname","name","audio_name");
                String artist=first(song,"singername","author_name");
                if(artist.isBlank())artist=names(array(song,"authors"),"author_name");
                if("未知歌手".equals(artist)&&title.contains(" - ")){String[] parts=title.split(" - ",2);artist=parts[0].trim();title=parts[1].trim();}
                if(title.isBlank()){String[] parts=first(song,"filename").split(" - ",2);title=parts[parts.length-1];if(parts.length==2)artist=parts[0];}
                String suffix=text(child(song,"trans_param"),"songname_suffix");
                if(!suffix.isBlank()&&!title.endsWith(suffix))title+=suffix;
                entries.add(NamedPlaylist.Entry.create(new OnlineTrackInfo("酷狗音乐",title,artist,
                        text(child(song,"albuminfo"),"name"),text(song,"cover").replace("{size}","400"),
                        text(song,"hash"),text(song,"album_id")),number(song,"timelength",number(song,"timelen",number(song,"duration",0)))));
            }
            return playlist(link,text(p,"name"),text(p,"pic").replace("{size}","400"),text(p,"list_create_username"),count(p,"count",-1),entries);
        }
        String rows=assignment(html,"var\\s+data",true), meta=assignment(html,"var\\s+specialInfo",false);
        if(rows==null || meta==null)throw new IllegalStateException("酷狗未返回可读取的歌单；请重新复制歌单分享链接");
        JsonArray songs=JsonParser.parseString(rows).getAsJsonArray(); JsonObject p=object(meta);
        List<NamedPlaylist.Entry> entries=new ArrayList<>();
        for(var element:songs){JsonObject song=element.getAsJsonObject();String hash=first(song,"hash","HASH");
            if(hash.isBlank())continue;
            entries.add(NamedPlaylist.Entry.create(new OnlineTrackInfo("酷狗音乐",first(song,"songname","audio_name"),
                    first(song,"singername","author_name"),text(song,"album_name"),text(child(song,"trans_param"),"union_cover").replace("{size}","400"),
                    hash,text(song,"album_id")),number(song,"timelength",0)));
        }
        return playlist(link,text(p,"name"),text(p,"image"),text(p,"nickname"),count(p,"count",-1),entries);
    }
    private static String assignment(String html,String name,boolean array){
        var m=Pattern.compile(name+"\\s*=\\s*").matcher(html);if(!m.find())return null;
        String wrapped="{\"value\":"+html.substring(m.end());
        return array?JsonSupport.arrayValue(wrapped,"value"):JsonSupport.objectValue(wrapped,"value");
    }
    private static NamedPlaylist playlist(PlaylistLinks.Link link,String name,String cover,String creator,int count,List<NamedPlaylist.Entry> entries){
        if(name.isBlank())throw new IllegalStateException("平台未返回歌单名称");
        if(entries.size()>20_000)throw new IllegalStateException("歌单超过 20000 首，请分批导入");
        return new NamedPlaylist(UUID.randomUUID().toString(),name,link.source(),link.id(),link.url(),cover,creator,count,"",false,entries);
    }
    static JsonObject object(String json){
        try{return JsonParser.parseString(json).getAsJsonObject();}catch(RuntimeException error){throw new IllegalStateException("平台返回了无效数据，请稍后重试",error);}
    }
    private static void requireCode(JsonObject j,String code){if(!text(j,"code").equals(code))throw new IllegalStateException("平台拒绝读取歌单（"+text(j,"code")+"），可能需要登录或链接已失效");}
    static JsonObject required(JsonObject j,String key){if(!j.has(key)||!j.get(key).isJsonObject())throw new IllegalStateException("缺少歌单信息："+key);return j.getAsJsonObject(key);}
    static JsonObject child(JsonObject j,String key){return j.has(key)&&j.get(key).isJsonObject()?j.getAsJsonObject(key):new JsonObject();}
    static JsonArray array(JsonObject j,String key){return j.has(key)&&j.get(key).isJsonArray()?j.getAsJsonArray(key):new JsonArray();}
    static String text(JsonObject j,String key){return j!=null&&j.has(key)&&j.get(key).isJsonPrimitive()?j.get(key).getAsString():"";}
    static String first(JsonObject j,String...keys){for(String key:keys){String value=text(j,key);if(!value.isBlank())return value;}return "";}
    static long number(JsonObject j,String key,long fallback){try{return Long.parseLong(text(j,key));}catch(NumberFormatException e){return fallback;}}
    static int count(JsonObject j,String key,int fallback){return (int)number(j,key,fallback);}
    private static String names(JsonArray rows,String field){List<String> result=new ArrayList<>();for(var r:rows){String name=text(r.getAsJsonObject(),field);if(!name.isBlank())result.add(name);}return result.isEmpty()?"未知歌手":String.join("、",result);}
    @Override public void close(){session.close();redirects.dispatcher().cancelAll();redirects.connectionPool().evictAll();}
}
