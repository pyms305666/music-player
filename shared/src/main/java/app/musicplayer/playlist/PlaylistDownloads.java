package app.musicplayer.playlist;

import app.musicplayer.online.*;
import app.musicplayer.model.OnlineTrackInfo;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static app.musicplayer.playlist.NamedPlaylist.State.*;

/** Feed at most two songs to the bounded downloader, with durable per-song results. */
public final class PlaylistDownloads implements AutoCloseable {
    @FunctionalInterface public interface Publisher {
        String publish(Path downloaded, NamedPlaylist.Entry entry, String destination) throws Exception;
    }
    @FunctionalInterface public interface CandidateSearch {
        List<OnlineTrackInfo> find(NamedPlaylist.Entry entry,RequestCancellation cancellation);
    }
    private record Job(String playlist, NamedPlaylist.Entry entry, String destination,List<NamedPlaylist.Entry> aliases) { }
    private final PlaylistStore store;
    private final OnlineMusicSearchService service;
    private final Publisher publisher;
    private final CandidateSearch search;
    private final Path scratch;
    private final Executor ui;
    private final ExecutorService workers=Executors.newFixedThreadPool(2,r -> new Thread(r,"playlist-download"));
    private final ArrayDeque<Job> pending=new ArrayDeque<>();
    private final Map<String,CancellableTask<Path>> transfers=new HashMap<>();
    private final Map<String,RequestCancellation> searches=new HashMap<>();
    private final Set<String> occupied=new HashSet<>(), paused=new HashSet<>(), cancelled=new HashSet<>();
    private final CopyOnWriteArrayList<Consumer<String>> listeners=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<String>> publishedListeners=new CopyOnWriteArrayList<>();
    private int running;
    private boolean closed;
    public PlaylistDownloads(PlaylistStore store,Path scratch,Publisher publisher,Executor ui){
        this(store,scratch,publisher,ui,new OnlineMusicSearchService(false));
    }
    public PlaylistDownloads(PlaylistStore store,Path scratch,Publisher publisher,Executor ui,OnlineMusicSearchService service){
        this(store,scratch,publisher,ui,service,(entry,cancellation) -> service.playlistCandidates(entry.track(),cancellation));
    }
    public PlaylistDownloads(PlaylistStore store,Path scratch,Publisher publisher,Executor ui,OnlineMusicSearchService service,CandidateSearch search){
        this.store=store;this.scratch=scratch;this.publisher=publisher;this.ui=ui;this.service=service;this.search=search;
    }
    public List<OnlineTrackInfo> candidates(NamedPlaylist.Entry entry){try(var cancellation=new RequestCancellation()){return search.find(entry,cancellation);}}
    public void listen(Consumer<String> listener){listeners.add(listener);}
    public void unlisten(Consumer<String> listener){listeners.remove(listener);}
    public void listenPublished(Consumer<String> listener){publishedListeners.add(listener);}
    public void unlistenPublished(Consumer<String> listener){publishedListeners.remove(listener);}
    private void changed(String playlist){ui.execute(() -> listeners.forEach(l -> l.accept(playlist)));}
    public synchronized boolean active(String playlist){return occupied.stream().anyMatch(k -> k.startsWith(playlist+"/"));}
    public synchronized boolean paused(String playlist){return paused.contains(playlist);}
    public synchronized void start(NamedPlaylist playlist,Set<String> items){
        if(closed)throw new IllegalStateException("下载管理已关闭");
        cancelled.remove(playlist.id());paused.remove(playlist.id());
        synchronized(store){
            var latest=store.findPlaylist(playlist.id()).orElseThrow();
            Map<String,List<NamedPlaylist.Entry>> groups=new LinkedHashMap<>();
            Map<String,NamedPlaylist.Entry> waitingItems=new HashMap<>();
            for(var entry:latest.entries())if(items.contains(entry.id()) && !occupied.contains(key(playlist.id(),entry.id()))){
                var waiting=entry.status(WAITING,"");waitingItems.put(entry.id(),waiting);
                // Preserve repeated playlist rows, while downloading a new recording once.
                String group=entry.state()==REDOWNLOAD ? entry.id() : entry.track().identity()+"|"+(entry.downloadTrack()==null?"":entry.downloadTrack().identity());
                groups.computeIfAbsent(group,k -> new ArrayList<>()).add(waiting);
            }
            if(!waitingItems.isEmpty()){
                store.savePlaylist(latest.withEntries(latest.entries().stream().map(e -> waitingItems.getOrDefault(e.id(),e)).toList()));
                waitingItems.keySet().forEach(id -> occupied.add(key(playlist.id(),id)));
            }
            for(var group:groups.values())pending.add(new Job(playlist.id(),group.get(0),playlist.directory(),List.copyOf(group)));
        }
        pump();changed(playlist.id());
    }
    public synchronized void pause(String playlist){paused.add(playlist);changed(playlist);}
    public synchronized void resume(String playlist){paused.remove(playlist);pump();changed(playlist);}
    public synchronized void cancel(String playlist){
        cancelled.add(playlist);paused.remove(playlist);
        for(var it=pending.iterator();it.hasNext();){var job=it.next();if(job.playlist.equals(playlist)){
            it.remove();for(var entry:job.aliases){occupied.remove(key(playlist,entry.id()));store.updatePlaylistEntry(playlist,entry.status(CANCELLED,""));}
        }}
        searches.forEach((key,token) -> {if(key.startsWith(playlist+"/"))token.close();});
        transfers.forEach((key,task) -> {if(key.startsWith(playlist+"/"))task.cancel();});changed(playlist);
    }
    private synchronized void pump(){
        while(!closed && running<2){
            Job next=null;for(var it=pending.iterator();it.hasNext();){var job=it.next();if(!paused.contains(job.playlist)){next=job;it.remove();break;}}
            if(next==null)return;running++;Job job=next;workers.execute(() -> run(job));
        }
    }
    private void run(Job job){
        String key=key(job.playlist,job.entry.id());Path file=null;OnlineTrackInfo actual=job.entry.downloadTrack();
        RequestCancellation cancellation=new RequestCancellation();
        try{
            synchronized(this){if(closed||cancelled.contains(job.playlist))throw new CancellationException();
                searches.put(key,cancellation);
                for(var entry:job.aliases)store.updatePlaylistEntry(job.playlist,entry.status(DOWNLOADING,actual==null?"正在匹配下载渠道":""));
            }
            changed(job.playlist);
            var candidates=actual==null?PlaylistSongMatcher.ranked(job.entry.track(),search.find(job.entry,cancellation),true):List.of(actual);
            if(candidates.isEmpty())throw new java.io.IOException("未匹配到歌名、歌手及版本一致的歌曲，请选择下载版本");
            Exception lastError=null;
            for(var candidate:candidates){
                cancellation.check();actual=candidate;
                for(var entry:job.aliases)store.updatePlaylistEntry(job.playlist,entry.downloadFrom(actual).status(DOWNLOADING,""));
                changed(job.playlist);
                var task=service.download(candidate.withAvailability(OnlineTrackInfo.Availability.TENTATIVE,"待下载"),scratch.resolve(job.entry.id()),Runnable::run,event -> {});
                synchronized(this){transfers.put(key,task);}
                try(var registration=cancellation.onCancel(task::cancel)){file=task.result().get();break;}
                catch(Exception error){cancellation.check();lastError=error;}
                finally{synchronized(this){transfers.remove(key);}}
            }
            if(file==null)throw lastError==null?new java.io.IOException("下载渠道暂不可用"):lastError;
            cancellation.check();
            synchronized(this){if(closed||cancelled.contains(job.playlist))throw new CancellationException();}
            // Publication owns the complete file. Navigation never starts playback here.
            String location=publisher.publish(file,job.entry.downloadFrom(actual),job.destination);file=null;
            for(var entry:job.aliases)store.updatePlaylistEntry(job.playlist,entry.downloadFrom(actual).local(location));
            ui.execute(() -> publishedListeners.forEach(listener -> listener.accept(job.playlist)));
        }catch(Exception error){
            Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
            boolean stopped=cause instanceof CancellationException || cause instanceof InterruptedException;
            for(var entry:job.aliases)try{store.updatePlaylistEntry(job.playlist,entry.downloadFrom(actual).status(stopped?CANCELLED:FAILED,
                    stopped?"":errorMessage(cause)));}catch(RuntimeException ignored){ }
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
        }finally{
            if(file!=null)try{Files.deleteIfExists(file);}catch(Exception ignored){ }
            try{Files.deleteIfExists(scratch.resolve(job.entry.id()));}catch(Exception ignored){ }
            cancellation.close();
            synchronized(this){transfers.remove(key);searches.remove(key);for(var entry:job.aliases)occupied.remove(key(job.playlist,entry.id()));running--;pump();}
            changed(job.playlist);
        }
    }
    private static String errorMessage(Throwable error){
        String message=Objects.toString(error.getMessage(),"下载失败");
        if(message.contains("cannot resolve URL")||message.contains("cannot download track"))return "下载渠道未提供可用地址，可重试或选择其他下载版本";
        if(message.contains("unusable file"))return "平台返回的内容不是可用音频";
        return message;
    }
    private static String key(String playlist,String item){return playlist+"/"+item;}
    public CompletableFuture<Void> shutdown(){
        synchronized(this){closed=true;searches.values().forEach(RequestCancellation::close);transfers.values().forEach(CancellableTask::cancel);pending.clear();workers.shutdownNow();}
        return CompletableFuture.runAsync(() -> {
            service.close();
            boolean interrupted=false;
            while(!workers.isTerminated())try{workers.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted=true;}
            if(interrupted)Thread.currentThread().interrupt();
        });
    }
    @Override public void close(){shutdown();}
}
