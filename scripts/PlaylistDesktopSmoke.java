import app.musicplayer.MusicPlayerApp;
import app.musicplayer.data.MusicDatabase;
import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.*;
import app.musicplayer.playlist.*;
import app.musicplayer.ui.PlaylistWindow;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Disposable JavaFX smoke for named playlists, duplicate review, and repeated queue rows. */
public class PlaylistDesktopSmoke extends DesktopSmoke {
    static Object read(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{var m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
    static Window window(String title){return Window.getWindows().stream().filter(w -> w instanceof Stage s&&title.equals(s.getTitle())&&w.isShowing()).findFirst().orElse(null);}
    static Button findButton(Window w,String text){return w.getScene().getRoot().lookupAll(".button").stream().filter(n -> n instanceof Button b&&b.getText().equals(text)).map(n -> (Button)n).findFirst().orElseThrow();}
    public static void main(String[]args)throws Exception{
        Path data=Path.of(System.getProperty("musicplayer.data-dir"));Files.createDirectories(data);
        List<Track> audio=new ArrayList<>();for(String title:List.of("甲曲","乙曲","丙曲")){
            int size=44100*2*20;ByteBuffer wav=ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN);
            wav.put(new byte[]{'R','I','F','F'}).putInt(36+size).put(new byte[]{'W','A','V','E','f','m','t',' '});wav.putInt(16).putShort((short)1).putShort((short)1).putInt(44100).putInt(88200).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(size);
            audio.add(new Track(Files.write(data.resolve("测试歌手 - "+title+".wav"),wav.array())));
        }
        var first=NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","甲曲","测试歌手","","","first",""),20000).local(audio.get(0).path().toString());
        var last=NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","乙曲","测试歌手","","","last",""),20000).local(audio.get(1).path().toString());
        var repeat=NamedPlaylist.Entry.create(first.track(),20000).local(first.location());
        var p=new NamedPlaylist("fixture","我的测试歌单","QQ音乐","fixture","","","测试",3,data.toString(),false,List.of(first,repeat,last));
        try(var db=new MusicDatabase(data.resolve("music-player.db"))){db.saveTracks(audio.subList(0,1));db.saveLyrics(audio.get(0),LrcParser.parse("fixture","[00:00]fixture"));db.savePlaylist(p);}
        Platform.startup(() -> {});
        try{
            fx(() -> {app=new MusicPlayerApp();var parameters=Class.forName("com.sun.javafx.application.ParametersImpl");var value=parameters.getConstructor(List.class).newInstance(List.of());parameters.getMethod("registerParameters",javafx.application.Application.class,javafx.application.Application.Parameters.class).invoke(null,app,value);stage=new Stage();app.start(stage);return null;},15);
            await(() -> ((List<?>)field("tracks")).size()==1,"Library restore failed");
            fx(() -> {invoke(app,"playSavedPlaylist",new Class<?>[]{List.class,boolean.class},List.of(audio.get(1).path().toString(),audio.get(2).path().toString()),true);return null;});
            await(() -> ((List<?>)field("playbackOrder")).size()>=3,"Appended songs did not enter queue");
            fx(() -> {var queue=(List<Track>)field("playbackOrder");if(!queue.stream().map(Track::path).toList().equals(audio.stream().map(Track::path).toList()))throw new AssertionError("Appending new songs duplicated or reordered rows: "+queue);
                if(field("currentTrack")!=null)throw new AssertionError("Appending to idle queue started playback");return null;});
            fx(() -> {((List<?>)field("tracks")).clear();((List<?>)field("playbackOrder")).clear();invoke(app,"playSavedPlaylist",new Class<?>[]{List.class,boolean.class},List.of(audio.get(1).path().toString(),audio.get(2).path().toString()),true);return null;});
            await(() -> ((List<?>)field("playbackOrder")).size()>=2,"Appending to empty queue failed");
            fx(() -> {var queue=(List<Track>)field("playbackOrder");if(!queue.stream().map(Track::path).toList().equals(audio.subList(1,3).stream().map(Track::path).toList()))throw new AssertionError("Empty queue append duplicated rows");return null;});
            fx(() -> {call("showSavedPlaylists",Stage.class,stage);return null;});
            PlaylistWindow manager=(PlaylistWindow)fx(() -> field("savedPlaylistWindow"));
            await(() -> read(manager,"current") instanceof NamedPlaylist value&&value.entries().size()==3,"Named playlist did not restore");
            fx(() -> {((TextField)read(manager,"search")).setText("乙曲");if(((ListView<?>)read(manager,"entries")).getItems().size()!=1)throw new AssertionError("Playlist search failed");((TextField)read(manager,"search")).clear();return null;});
            fx(() -> {invoke(app,"playSavedPlaylist",new Class<?>[]{List.class,boolean.class},List.of(first.location(),repeat.location(),last.location()),false);return null;});
            await(() -> field("currentTrack") instanceof Track t&&t.path().equals(audio.get(0).path()),"Playlist did not start first row");
            await(() -> field("mediaPlayer") instanceof javafx.scene.media.MediaPlayer player&&player.getStatus()==javafx.scene.media.MediaPlayer.Status.PLAYING,"Named playlist audio did not play");
            fx(() -> {call("nextTrack",boolean.class,true);if((int)field("savedPlaybackIndex")!=1)throw new AssertionError("Repeated row cursor was lost");call("nextTrack",boolean.class,true);return null;});
            await(() -> field("currentTrack") instanceof Track t&&t.path().equals(audio.get(1).path()),"Repeated row prevented advancing");
            var imported=p.configured("new-import","导入测试",data.toString(),false).withEntries(List.of(NamedPlaylist.Entry.create(first.track(),20000),NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","新歌","歌手","","","new",""),0)));
            fx(() -> {invoke(manager,"preview",new Class<?>[]{NamedPlaylist.class},imported);return null;});
            await(() -> window("导入预览")!=null,"Import preview missing");
            fx(() -> {Window preview=window("导入预览");var option=preview.getScene().getRoot().lookupAll(".check-box").stream().filter(n -> n instanceof CheckBox c&&c.getText().startsWith("导入后下载")).map(n -> (CheckBox)n).findFirst().orElseThrow();if(option.isSelected())throw new AssertionError("Metadata-only import must be default");Platform.runLater(() -> findButton(preview,"确认导入").fire());return null;});
            await(() -> window("发现重复歌曲")!=null,"Duplicate review missing");
            fx(() -> {var dialog=(DialogPane)window("发现重复歌曲").getScene().getRoot();((Button)dialog.lookupButton(ButtonType.OK)).fire();return null;});
            await(() -> window("导入预览")==null,"Preview did not close after duplicate approval");
            await(() -> window("已导入过此歌单")!=null||Window.getWindows().stream().anyMatch(w -> w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane d&&d.getButtonTypes().stream().anyMatch(b -> b.getText().equals("另存一份"))),"Reimport review missing");
            fx(() -> {Window dialog=Window.getWindows().stream().filter(w -> w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane d&&d.getButtonTypes().stream().anyMatch(b -> b.getText().equals("另存一份"))).findFirst().orElseThrow();findButton(dialog,"另存一份").fire();return null;});
            await(() -> read(manager,"current") instanceof NamedPlaylist value&&value.id().equals("new-import"),"Import did not persist");
            fx(() -> {var saved=(NamedPlaylist)read(manager,"current");if(!saved.entries().get(0).location().equals(first.location())||saved.entries().get(1).state()!=NamedPlaylist.State.MISSING)throw new AssertionError("Duplicate reuse or metadata-only import failed");((Stage)read(manager,"window")).close();call("showSavedPlaylists",Stage.class,stage);return null;});
            await(() -> read(manager,"current") instanceof NamedPlaylist value&&value.id().equals("new-import"),"Reopened playlist lost selection");
            fx(() -> {((Set<String>)read(manager,"selected")).add(((NamedPlaylist)read(manager,"current")).entries().get(0).id());Platform.runLater(() -> findButton((Stage)readUnchecked(manager,"window"),"删除歌单").fire());return null;});
            await(() -> Window.getWindows().stream().anyMatch(w -> w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane d&&d.getContentText().contains("保留所有音频文件")),"Delete confirmation missing");
            fx(() -> {var dialog=Window.getWindows().stream().filter(w -> w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane d&&d.getContentText().contains("保留所有音频文件")).findFirst().orElseThrow();((Button)((DialogPane)dialog.getScene().getRoot()).lookupButton(ButtonType.OK)).fire();return null;});
            await(() -> read(manager,"current") instanceof NamedPlaylist value&&value.id().equals("fixture"),"Deleting current playlist did not switch to remaining playlist");
            fx(() -> {if(!((Set<?>)read(manager,"selected")).isEmpty())throw new AssertionError("Deleted playlist left stale selected rows");if(!Files.exists(audio.get(0).path()))throw new AssertionError("Deleting playlist deleted audio");return null;});
            System.out.println("PLAYLIST DESKTOP SMOKE PASSED: persistence, filtering, append without duplicates, repeated playback order, metadata-only preview, duplicate reuse, reimport copy, reopen, deletion selection cleanup");
        }finally{fx(() -> {app.stop();for(var w:new ArrayList<>(Window.getWindows()))w.hide();return null;},15);Platform.exit();}
    }
    static Object readUnchecked(Object target,String name){try{return read(target,name);}catch(Exception error){throw new RuntimeException(error);}}
}
