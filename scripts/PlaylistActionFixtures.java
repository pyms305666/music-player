package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.playlist.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline transport fixture; UI and durable download state use the real implementations. */
public final class PlaylistActionFixtures {
    public volatile boolean hold,fail;
    public final AtomicInteger attempts=new AtomicInteger();
    public final PlaylistDownloads downloads;
    public PlaylistActionFixtures(PlaylistStore store,Path scratch,Executor ui){
        var service=new OnlineMusicSearchService(new MusicCrawler(List.of(),100),System::nanoTime,(track,directory,cancel,progress) -> {
            attempts.incrementAndGet();
            while(hold){cancel.check();Thread.sleep(20);}cancel.check();
            if(fail)throw new java.io.IOException("测试渠道暂不可用");
            Files.createDirectories(directory);return Files.write(directory.resolve(track.primaryId()+".wav"),new byte[2048]);
        });
        downloads=new PlaylistDownloads(store,scratch,(file,entry,destination) -> {
            Files.createDirectories(Path.of(destination));return Files.move(file,Path.of(destination).resolve(entry.id()+".wav")).toString();
        },ui,service,(entry,cancel) -> List.of(new OnlineTrackInfo("酷我音乐",entry.track().title(),entry.track().artist(),"测试版本","",entry.track().primaryId()+"-kuwo","")));
    }
}
