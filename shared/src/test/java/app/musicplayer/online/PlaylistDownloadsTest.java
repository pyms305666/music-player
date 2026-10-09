package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.playlist.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PlaylistDownloadsTest {
    @TempDir Path folder;
    @Test void thousandSongBatchIsBoundedAndFailedSongsRemainRetryable() throws Exception {
        var store=new MemoryStore();var p=playlist(1000);store.savePlaylist(p);
        AtomicInteger inFlight=new AtomicInteger(),maximum=new AtomicInteger(),attempts=new AtomicInteger();
        var service=new OnlineMusicSearchService(new MusicCrawler(List.of(),100),System::nanoTime,(track,dir,cancel,progress) -> {
            int current=inFlight.incrementAndGet();maximum.accumulateAndGet(current,Math::max);
            try{if(track.primaryId().equals("3")&&attempts.getAndIncrement()==0)throw new java.io.IOException("fixture unavailable");
                Files.createDirectories(dir);Path file=dir.resolve("song.mp3");Files.write(file,new byte[]{1});return file;
            }finally{inFlight.decrementAndGet();}
        });
        var downloads=new PlaylistDownloads(store,folder,(file,e,dest) -> {Files.delete(file);return "local/"+e.track().primaryId();},Runnable::run,service,(entry,token) -> List.of(entry.track()));
        try{
            downloads.start(p,ids(p));await(() -> !downloads.active(p.id()));
            var result=store.findPlaylist(p.id()).orElseThrow();assertEquals(999,result.entries().stream().filter(e -> e.state()==NamedPlaylist.State.READY).count());
            var failed=result.entries().stream().filter(e -> e.state()==NamedPlaylist.State.FAILED).findFirst().orElseThrow();assertEquals("3",failed.track().primaryId());assertTrue(maximum.get()<=2);
            downloads.start(result,Set.of(failed.id()));await(() -> !downloads.active(p.id()));
            assertTrue(store.findPlaylist(p.id()).orElseThrow().entries().stream().allMatch(e -> e.state()==NamedPlaylist.State.READY));
        }finally{downloads.shutdown().get(10,TimeUnit.SECONDS);}
    }
    @Test void pausingKeepsPendingAndCancellationDoesNotEraseFinishedFiles() throws Exception {
        var store=new MemoryStore();var p=playlist(8);store.savePlaylist(p);CountDownLatch started=new CountDownLatch(2),release=new CountDownLatch(1);
        var service=new OnlineMusicSearchService(new MusicCrawler(List.of(),100),System::nanoTime,(track,dir,cancel,progress) -> {
            started.countDown();release.await();Files.createDirectories(dir);return Files.write(dir.resolve("song.mp3"),new byte[]{1});
        });
        var downloads=new PlaylistDownloads(store,folder,(file,e,d) -> {Files.delete(file);return "local/"+e.id();},Runnable::run,service,(entry,token) -> List.of(entry.track()));
        try{
            downloads.start(p,ids(p));assertTrue(started.await(5,TimeUnit.SECONDS));downloads.pause(p.id());release.countDown();
            await(() -> store.findPlaylist(p.id()).orElseThrow().entries().stream().filter(e -> e.state()==NamedPlaylist.State.READY).count()==2);
            assertTrue(downloads.active(p.id()));assertTrue(downloads.paused(p.id()));downloads.cancel(p.id());await(() -> !downloads.active(p.id()));
            var entries=store.findPlaylist(p.id()).orElseThrow().entries();assertEquals(2,entries.stream().filter(e -> e.state()==NamedPlaylist.State.READY).count());
            assertEquals(6,entries.stream().filter(e -> e.state()==NamedPlaylist.State.CANCELLED).count());
        }finally{release.countDown();downloads.shutdown().get(10,TimeUnit.SECONDS);}
    }
    @Test void repeatedRowsShareOneFileUnlessUserRequestsSeparateCopies() throws Exception {
        var store=new MemoryStore();var track=new OnlineTrackInfo("QQ音乐","重复曲目","歌手","","","same","");
        var first=NamedPlaylist.Entry.create(track,0);var second=NamedPlaylist.Entry.create(track,0);
        var third=NamedPlaylist.Entry.create(track,0).status(NamedPlaylist.State.REDOWNLOAD,"");
        var fourth=NamedPlaylist.Entry.create(track,0).status(NamedPlaylist.State.REDOWNLOAD,"");
        var p=playlist(0).withEntries(List.of(first,second,third,fourth));store.savePlaylist(p);
        AtomicInteger fetched=new AtomicInteger(),published=new AtomicInteger();
        var service=new OnlineMusicSearchService(new MusicCrawler(List.of(),100),System::nanoTime,(info,dir,cancel,progress) -> {
            fetched.incrementAndGet();Files.createDirectories(dir);return Files.write(dir.resolve("song.mp3"),new byte[]{1});
        });
        var downloads=new PlaylistDownloads(store,folder,(file,e,d) -> {Files.delete(file);return "local/"+published.incrementAndGet();},Runnable::run,service,(entry,token) -> List.of(entry.track()));
        try{
            downloads.start(p,ids(p));await(() -> !downloads.active(p.id()));
            var rows=store.findPlaylist(p.id()).orElseThrow().entries();
            assertTrue(rows.stream().allMatch(e -> e.state()==NamedPlaylist.State.READY));
            assertEquals(3,fetched.get());assertEquals(3,published.get());
            assertEquals(rows.get(0).location(),rows.get(1).location());
            assertEquals(3,rows.stream().map(NamedPlaylist.Entry::location).distinct().count());
        }finally{downloads.shutdown().get(10,TimeUnit.SECONDS);}
    }
    @Test void failedInitialDatabaseWriteDoesNotReserveJobsOrBlockRetry() throws Exception {
        var store=new MemoryStore();var p=playlist(2);store.savePlaylist(p);store.rejectSave=true;
        var service=new OnlineMusicSearchService(new MusicCrawler(List.of(),100),System::nanoTime,(track,dir,cancel,progress) -> {
            Files.createDirectories(dir);return Files.write(dir.resolve("song.mp3"),new byte[]{1});
        });
        var downloads=new PlaylistDownloads(store,folder,(file,e,d) -> {Files.delete(file);return "local/"+e.id();},Runnable::run,service,(entry,token) -> List.of(entry.track()));
        try{
            assertThrows(IllegalStateException.class,() -> downloads.start(p,ids(p)));assertFalse(downloads.active(p.id()));
            store.rejectSave=false;downloads.start(p,ids(p));await(() -> !downloads.active(p.id()));
            assertTrue(store.findPlaylist(p.id()).orElseThrow().entries().stream().allMatch(e -> e.state()==NamedPlaylist.State.READY));
        }finally{downloads.shutdown().get(10,TimeUnit.SECONDS);}
    }
    @Test void softwareChannelsArePreferredAndFailedChannelFallsBackToMatchingVersion() throws Exception {
        var store=new MemoryStore();var p=playlist(1);store.savePlaylist(p);var requested=p.entries().get(0).track();
        var kuwo=new OnlineTrackInfo("酷我音乐",requested.title(),requested.artist(),"","","kw","");
        var live=new OnlineTrackInfo("酷我音乐",requested.title()+" (Live)",requested.artist(),"","","live","");
        var migu=new OnlineTrackInfo("咪咕音乐",requested.title(),requested.artist(),"","","mg","");List<String> attempted=Collections.synchronizedList(new ArrayList<>());
        var service=new OnlineMusicSearchService(new MusicCrawler(List.of(),100),System::nanoTime,(track,dir,cancel,progress) -> {
            attempted.add(track.source());if(track.source().equals("酷我音乐"))throw new java.io.IOException("fixture unavailable");
            Files.createDirectories(dir);return Files.write(dir.resolve("song.mp3"),new byte[]{1});
        });
        var downloads=new PlaylistDownloads(store,folder,(file,e,d) -> {Files.delete(file);return "local/song";},Runnable::run,service,(entry,token) -> List.of(requested,live,migu,kuwo));
        try{
            downloads.start(p,ids(p));await(() -> !downloads.active(p.id()));var saved=store.findPlaylist(p.id()).orElseThrow().entries().get(0);
            assertEquals(List.of("酷我音乐","咪咕音乐"),attempted);assertEquals(NamedPlaylist.State.READY,saved.state());assertEquals(requested,saved.track());assertEquals("咪咕音乐",saved.downloadTrack().source());
            assertEquals(saved,PlaylistCodec.entry(PlaylistCodec.entry(saved)));
        }finally{downloads.shutdown().get(10,TimeUnit.SECONDS);}
    }
    @Test void manuallySelectedVersionSurvivesFailurePersistenceAndRestartedRetry() throws Exception {
        var store=new MemoryStore();var p=playlist(1);var original=p.entries().get(0).track();
        var chosen=new OnlineTrackInfo("酷我音乐",original.title()+" (Live)",original.artist(),"现场","","chosen","");
        p=p.withEntries(List.of(p.entries().get(0).selectVersion(chosen)));store.savePlaylist(p);
        List<OnlineTrackInfo> attempted=new ArrayList<>();AtomicInteger searches=new AtomicInteger();
        for(int round=0;round<2;round++){
            var service=new OnlineMusicSearchService(new MusicCrawler(List.of(),100),System::nanoTime,(track,dir,cancel,progress) -> {
                attempted.add(track);if(attempted.size()==1)throw new java.io.IOException("fixture unavailable");
                Files.createDirectories(dir);return Files.write(dir.resolve("song.mp3"),new byte[]{1});
            });
            var downloads=new PlaylistDownloads(store,folder,(file,e,d) -> {Files.delete(file);return "local/chosen";},Runnable::run,service,(entry,token) -> {searches.incrementAndGet();return List.of(original);});
            try{
                var latest=store.findPlaylist(p.id()).orElseThrow();downloads.start(latest,ids(latest));await(() -> !downloads.active(latest.id()));
                var result=store.findPlaylist(p.id()).orElseThrow();var entry=result.entries().get(0);
                assertTrue(entry.userSelectedVersion());assertEquals(chosen,entry.downloadTrack());assertEquals(original,entry.track());
                if(round==0){
                    assertEquals(NamedPlaylist.State.FAILED,entry.state());
                    var restored=PlaylistCodec.entry(PlaylistCodec.entry(entry)).prepareDownload().pendingCopy(NamedPlaylist.State.MISSING);
                    store.savePlaylist(result.withEntries(List.of(restored)));
                }else assertEquals(NamedPlaylist.State.READY,entry.state());
            }finally{downloads.shutdown().get(10,TimeUnit.SECONDS);}
        }
        var expectedAttempt=chosen.withAvailability(OnlineTrackInfo.Availability.TENTATIVE,"待下载");
        assertEquals(List.of(expectedAttempt,expectedAttempt),attempted);assertEquals(0,searches.get());
    }
    private NamedPlaylist playlist(int count){List<NamedPlaylist.Entry> entries=new ArrayList<>();for(int i=0;i<count;i++)entries.add(NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","歌"+i,"歌手","","",Integer.toString(i),""),0));
        return new NamedPlaylist("playlist","歌单","QQ音乐","1","https://y.qq.com/n/ryqq/playlist/1","","我",count,"destination",false,entries);}
    private Set<String> ids(NamedPlaylist p){Set<String> ids=new HashSet<>();p.entries().forEach(e -> ids.add(e.id()));return ids;}
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue(condition.getAsBoolean());}
    static final class MemoryStore implements PlaylistStore {
        private final Map<String,NamedPlaylist> values=new LinkedHashMap<>();
        boolean rejectSave;
        public synchronized List<NamedPlaylist> loadPlaylists(){return List.copyOf(values.values());}
        public synchronized void savePlaylist(NamedPlaylist p){if(rejectSave)throw new IllegalStateException("fixture database write failure");values.put(p.id(),p);}
        public synchronized void updatePlaylistEntry(String id,NamedPlaylist.Entry item){var p=values.get(id);if(p==null)throw new IllegalStateException();values.put(id,p.withEntries(p.entries().stream().map(e -> e.id().equals(item.id())?item:e).toList()));}
        public synchronized void deletePlaylist(String id){values.remove(id);}
    }
}
