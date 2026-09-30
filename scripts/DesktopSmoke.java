import app.musicplayer.MusicPlayerApp;
import app.musicplayer.model.Track;
import app.musicplayer.data.MusicDatabase;
import app.musicplayer.lyrics.LrcParser;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.media.MediaPlayer;
import java.nio.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class DesktopSmoke {
    static MusicPlayerApp app;
    static Stage stage;
    static Object field(String name) throws Exception { var f=MusicPlayerApp.class.getDeclaredField(name);f.setAccessible(true);return f.get(app); }
    static Object call(String name,Class<?> type,Object argument) throws Exception {
        var method=type==null ? MusicPlayerApp.class.getDeclaredMethod(name) : MusicPlayerApp.class.getDeclaredMethod(name,type);
        method.setAccessible(true);return type==null? method.invoke(app):method.invoke(app,argument);
    }
    interface Work<T>{T get() throws Exception;}
    static <T>T fx(Work<T> work) throws Exception { return fx(work,2); }
    static <T>T fx(Work<T> work,int timeout) throws Exception {
        var result=new CompletableFuture<T>();
        Platform.runLater(()->{try{result.complete(work.get());}catch(Throwable error){result.completeExceptionally(error);}});
        return result.get(timeout,TimeUnit.SECONDS);
    }
    static void await(Work<Boolean> check,String reason)throws Exception{
        for(int i=0;i<120;i++){if(fx(check))return;Thread.sleep(100);}
        throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception {
        Path data=Path.of(System.getProperty("musicplayer.data-dir"));Files.createDirectories(data);
        List<Track> initial=new ArrayList<>();
        for(String name:List.of("QA-A.wav","QA-B.wav")){
            int size=44100*2*2;
            ByteBuffer wav=ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN);
            wav.put(new byte[]{'R','I','F','F'}).putInt(36+size).put(new byte[]{'W','A','V','E','f','m','t',' '});
            wav.putInt(16).putShort((short)1).putShort((short)1).putInt(44100).putInt(88200).putShort((short)2).putShort((short)16);
            wav.put(new byte[]{'d','a','t','a'}).putInt(size);
            initial.add(new Track(Files.write(data.resolve(name),wav.array())));
        }
        try(var db=new MusicDatabase(data.resolve("music-player.db"))){db.saveTracks(initial);for(var track:initial)db.saveLyrics(track,LrcParser.parse("fixture","[00:00]silent smoke test"));}
        Platform.startup(()->{});
        try{
            fx(()->{
                app=new MusicPlayerApp();
                Class<?> parameters=Class.forName("com.sun.javafx.application.ParametersImpl");
                Object value=parameters.getConstructor(List.class).newInstance(List.of());
                parameters.getMethod("registerParameters",javafx.application.Application.class,javafx.application.Application.Parameters.class).invoke(null,app,value);
                stage=new Stage();app.start(stage);stage.setIconified(true);return null;
            },15);
            await(()->((List<?>)field("tracks")).size()==2,"Library was not restored");
            List<?> tracks=fx(()->new ArrayList<>((List<?>)field("tracks")));
            Track first=(Track)tracks.get(0),last=(Track)tracks.get(1);
            // Artificial slow disk work must not block the FX thread or apply an obsolete selection.
            fx(()->{((ExecutorService)field("libraryExecutor")).submit(()->{try{Thread.sleep(1500);}catch(InterruptedException error){Thread.currentThread().interrupt();}});call("playTrack",Track.class,first);call("playTrack",Track.class,last);return null;});
            for(int i=0;i<8;i++){fx(()->true);Thread.sleep(100);}
            await(()->field("mediaPlayer") instanceof MediaPlayer p && p.getStatus()==MediaPlayer.Status.PLAYING,"Selected track did not play");
            if(fx(()->field("currentTrack"))!=last)throw new AssertionError("Obsolete selection applied");
            await(()->field("currentTrack")==first,"Last track did not loop to first");
            await(()->field("mediaPlayer") instanceof MediaPlayer p && p.getStatus()==MediaPlayer.Status.PLAYING,"Looped track did not play");
            fx(()->{call("togglePlayPause",null,null);return null;});
            await(()->((MediaPlayer)field("mediaPlayer")).getStatus()==MediaPlayer.Status.PAUSED,"Pause failed");
            fx(()->{call("togglePlayPause",null,null);return null;});
            await(()->((MediaPlayer)field("mediaPlayer")).getStatus()==MediaPlayer.Status.PLAYING,"Resume failed");
            System.out.println("DESKTOP SMOKE PASSED: async restore, responsive slow I/O, latest selection, natural queue loop while minimized, pause/resume");
        }finally{fx(()->{if(app!=null)app.stop();if(stage!=null)stage.close();return null;});Platform.exit();}
    }
}
