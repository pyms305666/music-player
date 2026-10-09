package app.musicplayer.data;

import app.musicplayer.model.*;
import app.musicplayer.playlist.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NamedPlaylistDatabaseTest {
    @TempDir Path folder;
    @Test void upgradingOldDatabaseRoundTripsEntriesAndDeletingPlaylistKeepsAudioAndLyrics() throws Exception {
        Path audio=Files.write(folder.resolve("歌手 - 原曲.mp3"),new byte[]{1,2,3}),db=folder.resolve("music.db");
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+db);var statement=connection.createStatement()){
            statement.executeUpdate("create table tracks(path text primary key,title text not null,artist text not null,duration_seconds integer,imported_at text not null,updated_at text not null)");
            statement.executeUpdate("create table lyrics(track_path text primary key,source text not null,raw_lyrics text not null,updated_at text not null)");
        }
        var entry=NamedPlaylist.Entry.create(new OnlineTrackInfo("网易云音乐","原曲","歌手","专辑","","1",""),1000)
                .selectVersion(new OnlineTrackInfo("咪咕音乐","原曲 (Live)","歌手","现场","","selected","")).local(audio.toString());
        var playlist=new NamedPlaylist("p","歌单\"名字","网易云音乐","1","https://music.163.com/playlist?id=1","","我",1,folder.toString(),false,List.of(entry));
        try(var database=new MusicDatabase(db)){
            var track=new Track(audio);database.saveTracks(List.of(track));
            database.saveLyrics(track,app.musicplayer.lyrics.LrcParser.parse("fixture","[00:01.00]原有歌词"));
            database.savePlaylist(playlist);
            assertEquals(playlist,database.loadPlaylists().get(0));
            database.updatePlaylistEntry("p",entry.status(NamedPlaylist.State.FAILED,"错误"));assertEquals(NamedPlaylist.State.FAILED,database.loadPlaylists().get(0).entries().get(0).state());
        }
        try(var database=new MusicDatabase(db)){assertEquals(1,database.loadTracks().size());assertEquals(1,database.loadPlaylists().size());assertEquals(entry.status(NamedPlaylist.State.FAILED,"错误"),database.loadPlaylists().get(0).entries().get(0));database.deletePlaylist("p");assertTrue(database.loadPlaylists().isEmpty());assertEquals(1,database.loadTracks().size());assertTrue(Files.exists(audio));assertEquals("[00:01.00]原有歌词",database.loadLyrics(new Track(audio)).orElseThrow().rawText());}
    }
    @Test void playlistReplacementIsAtomicOnDuplicateItemIds() throws Exception {
        try(var db=new MusicDatabase(folder.resolve("music.db"))){var e=NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","歌","人","","","1",""),0);
            var p=new NamedPlaylist("p","歌单","QQ音乐","1","","","",1,folder.toString(),false,List.of(e));db.savePlaylist(p);
            assertThrows(IllegalStateException.class,() -> db.savePlaylist(p.withEntries(List.of(e,e))));assertEquals(p,db.loadPlaylists().get(0));}
    }
}
