package app.musicplayer.android;

import android.database.sqlite.SQLiteDatabase;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.musicplayer.playlist.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

/** Explicit emulator-only, read-only verification of a playlist created through the actual UI. */
@RunWith(AndroidJUnit4.class)
public class PlaylistLiveCheck {
    @Test public void downloadedQaPlaylistSurvivesRestartWithReadableAudio() throws Exception {
        var args=InstrumentationRegistry.getArguments();
        String suffix=args.getString("playlistQaNameSuffix","");
        org.junit.Assume.assumeTrue("Use an explicitly named QA playlist on a disposable emulator",
                suffix.startsWith("_QA")&&(Build.MODEL.startsWith("sdk_gphone")||Build.MODEL.contains("Emulator")));
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<NamedPlaylist> playlists=new ArrayList<>();
        try(var db=SQLiteDatabase.openDatabase(context.getDatabasePath("music-player.db").toString(),null,SQLiteDatabase.OPEN_READONLY);
            var headers=db.rawQuery("select id,metadata from named_playlists",null)){
            while(headers.moveToNext()){
                var header=PlaylistCodec.header(headers.getString(1),List.of());if(!header.name().endsWith(suffix))continue;
                var entries=new ArrayList<NamedPlaylist.Entry>();
                try(var rows=db.rawQuery("select payload from named_playlist_items where playlist_id=? order by position",new String[]{headers.getString(0)})){
                    while(rows.moveToNext())entries.add(PlaylistCodec.entry(rows.getString(0)));
                }
                playlists.add(header.withEntries(entries));
            }
        }
        assertEquals("Select one uniquely named UI fixture",1,playlists.size());var p=playlists.get(0);assertEquals(9,p.entries().size());
        JSONArray entries=new JSONArray();int ready=0,failed=0;long bytes=0;
        for(var entry:p.entries()){
            var row=new JSONObject(PlaylistCodec.entry(entry));assertFalse(entry.userSelectedVersion());
            if(entry.state()==NamedPlaylist.State.READY){
                ready++;assertEquals("酷我音乐",entry.downloadTrack().source());assertTrue(PlaylistSongMatcher.matches(entry.track(),entry.downloadTrack()));
                long size=0;var hash=MessageDigest.getInstance("SHA-256");byte[] header=null;
                try(var input=context.getContentResolver().openInputStream(Uri.parse(entry.location()))){
                    assertNotNull(input);byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1){if(header==null)header=Arrays.copyOf(buffer,Math.min(n,16));hash.update(buffer,0,n);size+=n;}
                }
                assertTrue(size>32768);bytes+=size;row.put("downloadBytes",size);row.put("headerHex",hex(header));row.put("sha256",hex(hash.digest()));
                var reader=new MediaMetadataRetriever();
                try{reader.setDataSource(context,Uri.parse(entry.location()));
                    long duration=Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));assertTrue(duration>0);
                    row.put("androidDecodedMillis",duration);row.put("differenceMillis",duration-entry.durationMillis());
                    row.put("audioTitle",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                    row.put("audioArtist",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
                    row.put("audioAlbum",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
                    assertTrue("Decoded duration must agree with the requested recording: "+entry.label(),Math.abs(duration-entry.durationMillis())<250);
                }finally{reader.release();}
            }else{
                failed++;assertEquals(NamedPlaylist.State.FAILED,entry.state());
                assertTrue(entry.track().title().contains("2018王者荣耀")||entry.track().title().contains("3D环绕版"));
                for(String source:List.of("酷我音乐","咪咕音乐","QQ音乐","网易云音乐","酷狗音乐"))assertTrue(entry.message().contains(source));
                assertTrue(entry.message().contains("已尝试："));
            }
            entries.put(row);
        }
        assertEquals(7,ready);assertEquals(2,failed);
        var report=new JSONObject().put("playlist",new JSONObject(PlaylistCodec.header(p))).put("ready",ready).put("failed",failed).put("bytes",bytes).put("entries",entries);
        Path output=context.getExternalFilesDir("qa").toPath().resolve("playlist-live-check.json");Files.write(output,report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        android.util.Log.i("PlaylistLiveCheck","READ_ONLY_RESULT "+output+" ready="+ready+" failed="+failed);
    }
    private static String hex(byte[] bytes){StringBuilder text=new StringBuilder();for(byte value:bytes)text.append(String.format(Locale.ROOT,"%02x",value&255));return text.toString();}
}
