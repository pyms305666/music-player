package app.musicplayer.android;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.musicplayer.android.data.*;
import app.musicplayer.model.*;
import app.musicplayer.playlist.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlaylistPersistenceTest {
    private Context context;private File root,dbFile;private AndroidMusicDatabase database;
    @Before public void setup() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();root=new File(context.getCacheDir(),"qa-playlists-"+UUID.randomUUID());assertTrue(root.mkdir());dbFile=new File(root,"fixture.db");
    }
    @After public void cleanup() throws Exception {if(database!=null)database.close();BenchmarkSupport.deleteFixture(context,root);}
    @Test public void schemaThreeUpgradePreservesAudioLyricsImportsAndAddsIndependentPlaylists() throws Exception {
        File audio=new File(root,"original.mp3");Files.write(audio.toPath(),new byte[]{1,2,3});
        try(SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(dbFile,null)){
            old.execSQL("create table tracks(path text primary key,title text not null,artist text not null,created_at integer not null,storage_type text not null default 'FILE',file_name text)");
            old.execSQL("create table lyrics(path text primary key,source text not null,raw_text text not null,artwork_url text)");
            old.execSQL("create table imports(source_uri text primary key,path text not null)");old.execSQL("create index imports_path on imports(path)");
            old.execSQL("insert into tracks values(?,?,?,?,?,?)",new Object[]{audio.toString(),"原曲","歌手",1,"FILE","original.mp3"});
            old.execSQL("insert into lyrics values(?,?,?,?)",new Object[]{audio.toString(),"fixture","[00:00]fixture",null});
            old.execSQL("insert into imports values(?,?)",new Object[]{"content://fixture/song",audio.toString()});old.setVersion(3);
        }
        database=new AndroidMusicDatabase(BenchmarkSupport.databaseContext(context,dbFile));
        assertEquals(4,database.getWritableDatabase().getVersion());assertEquals(1,database.loadTracks().size());assertTrue(database.hasImported("content://fixture/song"));
        assertEquals("[00:00]fixture",database.loadLyrics(database.loadTracks().get(0)).rawText());
        var e=NamedPlaylist.Entry.create(new OnlineTrackInfo("酷狗音乐","原曲","歌手","","","123",""),1000)
                .selectVersion(new OnlineTrackInfo("咪咕音乐","原曲 (Live)","歌手","现场","","selected","")).local(audio.toString());
        var p=new NamedPlaylist("p","歌单","酷狗音乐","1","https://m.kugou.com/songlist/gcid_test/?src_cid=test","","fixture",1,AndroidPlaylistFiles.DEFAULT,false,List.of(e));
        database.savePlaylist(p);assertEquals(p,database.loadPlaylists().get(0));database.updatePlaylistEntry("p",e.status(NamedPlaylist.State.FAILED,"失败"));
        assertEquals(e.status(NamedPlaylist.State.FAILED,"失败"),database.loadPlaylists().get(0).entries().get(0));database.deletePlaylist("p");
        assertTrue(database.loadPlaylists().isEmpty());assertEquals(1,database.loadTracks().size());assertTrue(audio.exists());assertTrue(database.hasImported("content://fixture/song"));assertNotNull(database.loadLyrics(database.loadTracks().get(0)));
    }
    @Test public void transactionRollsBackInvalidDuplicateIds() {
        database=new AndroidMusicDatabase(BenchmarkSupport.databaseContext(context,dbFile));
        var e=NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","歌","歌手","","","id",""),0);
        var p=new NamedPlaylist("p","歌单","QQ音乐","1","","","",1,AndroidPlaylistFiles.DEFAULT,false,List.of(e));database.savePlaylist(p);
        try{database.savePlaylist(p.withEntries(List.of(e,e)));fail("Expected duplicate item rejection");}catch(android.database.sqlite.SQLiteConstraintException expected){ }
        assertEquals(p,database.loadPlaylists().get(0));
    }
    @Test public void publishingRepeatedNamesCreatesSeparateReadableAudioAndKeepsBoth() throws Exception {
        database=new AndroidMusicDatabase(BenchmarkSupport.databaseContext(context,dbFile));
        var files=new AndroidPlaylistFiles(context,database);
        var entry=NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","测试曲目","测试歌手","","","fixture",""),1000);
        String name="qa-playlist-"+UUID.randomUUID()+".wav";List<String> locations=new ArrayList<>();
        int size=16000*2;var wav=java.nio.ByteBuffer.allocate(44+size).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36+size).put(new byte[]{'W','A','V','E','f','m','t',' '});
        wav.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(size);
        try{
            for(int i=0;i<2;i++)locations.add(files.publish(Files.write(new File(root,name).toPath(),wav.array()),entry,AndroidPlaylistFiles.DEFAULT));
            assertNotEquals(locations.get(0),locations.get(1));assertEquals(2,database.loadTracks().size());
            for(String location:locations){assertTrue(files.readable(location));
                if(location.startsWith("content:"))try(var input=context.getContentResolver().openInputStream(android.net.Uri.parse(location))){assertArrayEquals(wav.array(),input.readAllBytes());}
                else assertArrayEquals(wav.array(),Files.readAllBytes(java.nio.file.Path.of(location)));
            }
            var p=new NamedPlaylist("p","测试","QQ音乐","fixture","","","",1,AndroidPlaylistFiles.DEFAULT,false,List.of(entry.local(locations.get(0))));
            database.savePlaylist(p);database.deletePlaylist(p.id());locations.forEach(location -> assertTrue(files.readable(location)));
        }finally{
            for(String location:locations){if(location.startsWith("content:"))context.getContentResolver().delete(android.net.Uri.parse(location),null,null);else Files.deleteIfExists(java.nio.file.Path.of(location));}
        }
    }
    @Test public void publicationAndDuplicateScanWorkThroughPersistedDocumentTreeGrant() throws Exception {
        String target=InstrumentationRegistry.getArguments().getString("playlistQaTree");
        org.junit.Assume.assumeTrue("Run with a disposable authorized QA tree",target!=null&&target.endsWith("QA-Playlist"));
        database=new AndroidMusicDatabase(BenchmarkSupport.databaseContext(context,dbFile));var files=new AndroidPlaylistFiles(context,database);
        var e=NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","SAF测试","测试歌手","","","fixture",""),0);
        List<String> created=new ArrayList<>();String name="qa-saf-"+UUID.randomUUID()+".mp3";byte[] bytes={73,68,51,1,2,3};
        try{
            assertFalse(files.scan(target,false).isEmpty());
            for(int i=0;i<2;i++)created.add(files.publish(Files.write(new File(root,name).toPath(),bytes),e,target));
            assertNotEquals(created.get(0),created.get(1));assertEquals(2,database.loadTracks().size());
            for(String location:created){assertTrue(files.readable(location));try(var input=context.getContentResolver().openInputStream(android.net.Uri.parse(location))){assertArrayEquals(bytes,input.readAllBytes());}}
            var p=new NamedPlaylist("p","SAF测试","QQ音乐","fixture","","","",1,target,false,List.of(e.local(created.get(0))));database.savePlaylist(p);
            var match=new PlaylistDuplicates(files.scan(target,false)).match(List.of(e));assertEquals(1,match.size());assertTrue(match.get(0).confirmed());
        }finally{for(String location:created)android.provider.DocumentsContract.deleteDocument(context.getContentResolver(),android.net.Uri.parse(location));}
    }
}
