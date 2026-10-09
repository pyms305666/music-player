package app.musicplayer.online;

import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.musicplayer.android.data.*;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.playlist.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import static org.junit.Assert.*;

/** Offline batch behavior on ART, using a private database and only owned temporary audio. */
@RunWith(AndroidJUnit4.class)
public class AndroidPlaylistDownloadsTest {
    private Context context,fixtureContext;
    private Path root;
    private AndroidMusicDatabase database;
    private final List<String> published=new ArrayList<>();
    @Before public void setup() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        root=Files.createTempDirectory(context.getCacheDir().toPath(),"qa-playlist-download-");
        var file=root.resolve("fixture.db").toFile();
        fixtureContext=new ContextWrapper(context){
            @Override public java.io.File getDatabasePath(String name){return file;}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(file,factory);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(file.toString(),factory,handler);}
        };
        database=new AndroidMusicDatabase(fixtureContext);
    }
    @After public void cleanup() throws Exception {
        for(String location:published)if(location.startsWith("content:"))context.getContentResolver().delete(Uri.parse(location),null,null);else Files.deleteIfExists(Path.of(location));
        if(database!=null)database.close();
        assertEquals(context.getCacheDir().toPath().toAbsolutePath(),root.toAbsolutePath().getParent());
        try(var files=Files.walk(root)){for(var file:files.sorted(Comparator.reverseOrder()).toList()){assertTrue(file.startsWith(root));Files.deleteIfExists(file);}}
    }
    private OnlineTrackInfo track(String source,String title,String artist,String album,String id){return new OnlineTrackInfo(source,title,artist,album,"",id,"");}
    private NamedPlaylist playlist(NamedPlaylist.Entry entry){return new NamedPlaylist("fixture","批量测试","酷狗音乐","fixture","","","",1,AndroidPlaylistFiles.DEFAULT,false,List.of(entry));}
    private OnlineSourceProvider provider(String name,Supplier<List<OnlineTrackInfo>> results){return new OnlineSourceProvider(){
        public String sourceName(){return name;}public String referer(){return "http://localhost/";}
        public List<OnlineTrackInfo> search(String query){return results.get();}public String resolve(OnlineTrackInfo track){return null;}
    };}
    private void await(BooleanSupplier done) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!done.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(20);assertTrue(done.getAsBoolean());
    }
    private Path audio(Path directory) throws Exception {
        Files.createDirectories(directory);int size=16000*2;var wav=ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36+size).put(new byte[]{'W','A','V','E','f','m','t',' '});
        wav.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(size);
        return Files.write(directory.resolve("qa-batch-"+UUID.randomUUID()+".wav"),wav.array());
    }
    private PlaylistDownloads.Publisher publisher(){var files=new AndroidPlaylistFiles(context,database);return (file,entry,destination) -> {
        String location=files.publish(file,entry,destination);published.add(location);return location;
    };}
    private NamedPlaylist.Entry reload(){database.close();database=new AndroidMusicDatabase(fixtureContext);return database.findPlaylist("fixture").orElseThrow().entries().get(0);}

    @Test public void androidNormalizationMatchesFourActualCatalogVariantsAndRejectsOtherVersions(){
        assertTrue(PlaylistSongMatcher.matches(track("酷狗音乐","爱最闪耀","何锘情","梦","kg"),track("酷我音乐","爱最闪耀 (cover: 许馨文)","何锘情","梦","kw")));
        assertTrue(PlaylistSongMatcher.matches(track("酷狗音乐","像我这样的人 (Live)","毛不易","2017阅文超级IP风云盛典","kg"),track("酷我音乐","像我这样的人","毛不易","2017阅文超级IP风云盛典","kw")));
        assertTrue(PlaylistSongMatcher.matches(track("酷狗音乐","像我这样的人 (Live)","毛不易、徐航","嗨，唱起来  第12期","kg"),track("酷我音乐","像我这样的人","徐航&毛不易","嗨，唱起来 第12期","kw")));
        var live=track("酷狗音乐","平凡之路 （Live）","朴树","朴树 猎户星座·专场","kg");
        assertTrue(PlaylistSongMatcher.matches(live,track("酷我音乐","平凡之路","朴树","朴树 猎户星座·专场","kw")));
        assertFalse(PlaylistSongMatcher.matches(live,track("酷我音乐","平凡之路 (Live)","朴树","其他演唱会","kw")));
        assertFalse(PlaylistSongMatcher.matches(live,track("酷我音乐","平凡之路 (Remix)","朴树",live.album(),"kw")));
        assertFalse(PlaylistSongMatcher.matches(live,track("酷我音乐","平凡之路","朴树","","kw")));
        assertFalse(PlaylistSongMatcher.matches(live,track("酷我音乐","平凡之路","其他歌手",live.album(),"kw")));
        assertFalse(PlaylistSongMatcher.matches(track("酷狗音乐","海阔天空 (3D环绕版)","BEYOND","","kg"),track("咪咕音乐","海阔天空","BEYOND","","mg")));
        assertFalse(PlaylistSongMatcher.matches(track("酷狗音乐","消愁 (2018王者荣耀三周年音乐盛典现场)","毛不易","","kg"),track("酷我音乐","消愁 (Live)","毛不易","","kw")));
    }
    @Test public void androidKuwoSearchKeepsVersionAndMatchesDeclaredSoundtrackCredit() {
        String body="{'abslist':[{'MUSICRID':'MUSIC_1','NAME':'追光者','SONGNAME':'追光者&nbsp;(Exclusive&nbsp;Remix)','ARTIST':'岑宁儿'},"
                +"{'MUSICRID':'MUSIC_2','NAME':'追光者','SONGNAME':'追光者&nbsp;(3d环绕)','ARTIST':'岑宁儿'},"
                +"{'MUSICRID':'MUSIC_3','SONGNAME':'追光者-《夏至未至》电视剧插曲','SUBTITLE':'《夏至未至》电视剧插曲','ARTIST':'岑宁儿'}]}";
        var results=KuwoSourceProvider.parseSearchResponse(body);var requested=track("酷狗音乐","追光者","岑宁儿","夏至未至 电视剧原声带","original");
        assertEquals("追光者 (Exclusive Remix)",results.get(0).title());assertFalse(PlaylistSongMatcher.matches(requested,results.get(0)));
        assertEquals("追光者 (3d环绕)",results.get(1).title());assertFalse(PlaylistSongMatcher.matches(requested,results.get(1)));
        assertEquals("追光者",results.get(2).title());assertTrue(PlaylistSongMatcher.matches(requested,results.get(2)));
    }
    @Test public void automaticRetryResearchesAndFallsBackThenPublishesDurableAudio() throws Exception {
        var original=track("酷狗音乐","测试曲","测试歌手","","original");
        var kuwo=track("酷我音乐",original.title(),original.artist(),"","kw");var migu=track("咪咕音乐",original.title(),original.artist(),"","mg");
        var entry=NamedPlaylist.Entry.create(original,1000).downloadFrom(original).status(NamedPlaylist.State.FAILED,"old failure");
        var p=playlist(entry);database.savePlaylist(p);List<String> attempted=Collections.synchronizedList(new ArrayList<>());
        var service=new OnlineMusicSearchService(new MusicCrawler(List.of(provider("酷我音乐",() -> List.of(kuwo)),provider("咪咕音乐",() -> List.of(migu))),1000),System::nanoTime,
                (info,dir,token,progress) -> {attempted.add(info.source());if(info.source().equals("酷我音乐"))throw new java.io.IOException("cannot resolve URL");return audio(dir);});
        var downloads=new PlaylistDownloads(database,root.resolve("scratch"),publisher(),Runnable::run,service);
        try{downloads.start(p,Set.of(entry.id()));await(() -> !downloads.active(p.id()));}finally{downloads.shutdown().get(10,TimeUnit.SECONDS);}
        var saved=reload();assertEquals(List.of("酷我音乐","咪咕音乐"),attempted);assertEquals(NamedPlaylist.State.READY,saved.state());
        assertFalse(saved.userSelectedVersion());assertEquals("咪咕音乐",saved.downloadTrack().source());assertEquals(original,saved.track());
        assertTrue(new AndroidPlaylistFiles(context,database).readable(saved.location()));assertEquals(1,database.loadTracks().size());
        try(var input=saved.location().startsWith("content:")?context.getContentResolver().openInputStream(Uri.parse(saved.location())):Files.newInputStream(Path.of(saved.location()))){byte[] header=new byte[4];assertEquals(4,input.read(header));assertEquals("RIFF",new String(header,java.nio.charset.StandardCharsets.US_ASCII));}
    }
    @Test public void failureDetailsAndAllAttemptedSourcesSurviveAndroidDatabaseReopen() throws Exception {
        var original=track("QQ音乐","测试曲","测试歌手","","original");var kuwo=track("酷我音乐",original.title(),original.artist(),"","kw");
        var entry=NamedPlaylist.Entry.create(original,0);var p=playlist(entry);database.savePlaylist(p);
        var service=new OnlineMusicSearchService(new MusicCrawler(List.of(provider("酷我音乐",() -> List.of(kuwo)),provider("咪咕音乐",List::of),provider("QQ音乐",List::of),
                provider("网易云音乐",() -> {throw new IllegalStateException("offline");})),1000),System::nanoTime,(info,dir,token,progress) -> {throw new java.io.IOException("cannot resolve URL");});
        var downloads=new PlaylistDownloads(database,root.resolve("scratch"),publisher(),Runnable::run,service);
        try{downloads.start(p,Set.of(entry.id()));await(() -> !downloads.active(p.id()));}finally{downloads.shutdown().get(10,TimeUnit.SECONDS);}
        var saved=reload();assertEquals(NamedPlaylist.State.FAILED,saved.state());assertTrue(saved.message().contains("酷我音乐：同版候选 1"));
        assertTrue(saved.message().contains("咪咕音乐：同版候选 0"));assertTrue(saved.message().contains("网易云音乐：搜索失败"));
        assertTrue(saved.message().contains("已尝试：酷我音乐、QQ音乐"));assertTrue(published.isEmpty());
    }
    @Test public void manuallyPinnedVersionSurvivesFailureAndReopenedRetry() throws Exception {
        var original=track("酷狗音乐","测试曲","测试歌手","","original");var chosen=track("咪咕音乐","测试曲 (Live)","测试歌手","指定现场","selected");
        var entry=NamedPlaylist.Entry.create(original,0).selectVersion(chosen);database.savePlaylist(playlist(entry));List<String> attempted=new ArrayList<>();
        for(int round=0;round<2;round++){
            boolean unavailable=round==0;var service=new OnlineMusicSearchService(new MusicCrawler(List.of(),1000),System::nanoTime,(info,dir,token,progress) -> {
                attempted.add(info.source());if(unavailable)throw new java.io.IOException("cannot resolve URL");return audio(dir);
            });
            var downloads=new PlaylistDownloads(database,root.resolve("scratch"),publisher(),Runnable::run,service,(e,token) -> {throw new AssertionError("Manual choice must not be replaced by search");});
            try{var p=database.findPlaylist("fixture").orElseThrow();downloads.start(p,Set.of(entry.id()));await(() -> !downloads.active(p.id()));}finally{downloads.shutdown().get(10,TimeUnit.SECONDS);}
            var saved=reload();assertEquals(unavailable?NamedPlaylist.State.FAILED:NamedPlaylist.State.READY,saved.state());
            assertTrue(saved.userSelectedVersion());assertEquals(chosen,saved.downloadTrack());assertEquals(original,saved.track());
        }
        assertEquals(List.of("咪咕音乐","咪咕音乐"),attempted);
    }
    @Test public void twoBatchSearchesStartHealthySourcesWithoutWaitingForTheBlockedSong() throws Exception {
        var started=new java.util.concurrent.CountDownLatch(5);var release=new java.util.concurrent.CountDownLatch(1);
        List<OnlineSourceProvider> providers=new ArrayList<>();
        for(int i=0;i<5;i++){String name="source"+i;providers.add(new OnlineSourceProvider(){
            public String sourceName(){return name;}public String referer(){return "http://localhost/";}
            public List<OnlineTrackInfo> search(String query){
                if(query.equals("blocked")){
                    started.countDown();boolean interrupted=false;
                    while(release.getCount()>0)try{release.await();}catch(InterruptedException error){interrupted=true;}
                    if(interrupted)Thread.currentThread().interrupt();
                }
                return List.of(track(name,"曲目","歌手","",name));
            }
            public String resolve(OnlineTrackInfo track){return null;}
        });}
        var calls=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var crawler=new MusicCrawler(providers,1500,2)){
            try{var blocked=calls.submit(() -> crawler.search("blocked"));assertTrue(started.await(1,TimeUnit.SECONDS));
                var healthy=crawler.searchIncrementally("healthy",ignored -> {});assertEquals(5,healthy.tracks().size());
                assertTrue(healthy.sources().stream().allMatch(s -> s.outcome()==OnlineSearchSnapshot.Outcome.COMPLETE));
                release.countDown();blocked.get(3,TimeUnit.SECONDS);
            }finally{release.countDown();calls.shutdownNow();}
        }
    }
}
