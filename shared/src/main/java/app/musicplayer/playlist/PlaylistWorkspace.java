package app.musicplayer.playlist;

import app.musicplayer.online.PlaylistImportService;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Serial background edits; all file checks and database work are outside the UI thread. */
public final class PlaylistWorkspace implements AutoCloseable {
    public final PlaylistStore store;
    public final PlaylistDownloads downloads;
    private final PlaylistImportService importer=new PlaylistImportService();
    private final ExecutorService io=Executors.newSingleThreadExecutor(r -> new Thread(r,"playlist-library"));
    public PlaylistWorkspace(PlaylistStore store,Path scratch,PlaylistDownloads.Publisher publisher,Executor ui){
        this.store=store;downloads=new PlaylistDownloads(store,scratch,publisher,ui);
    }
    public <T> CompletableFuture<T> work(Supplier<T> operation){return CompletableFuture.supplyAsync(operation,io);}
    public CompletableFuture<NamedPlaylist> read(String link){return work(() -> importer.load(link));}
    public CompletableFuture<List<NamedPlaylist>> list(){return work(store::loadPlaylists);}
    public CompletableFuture<Void> refreshFiles(PlaylistFiles files){return work(() -> {
        Map<String,Boolean> readable=new HashMap<>();
        for(var p:store.loadPlaylists())if(!downloads.active(p.id()))for(var e:p.entries()){
            if(e.state()==NamedPlaylist.State.READY && !readable.computeIfAbsent(e.location(),files::readable))
                store.updatePlaylistEntry(p.id(),e.pendingCopy(NamedPlaylist.State.MISSING).status(NamedPlaylist.State.MISSING,"本地文件已失效"));
            else if(e.state()==NamedPlaylist.State.WAITING || e.state()==NamedPlaylist.State.DOWNLOADING)
                store.updatePlaylistEntry(p.id(),e.status(NamedPlaylist.State.CANCELLED,"上次任务中断，可继续下载"));
        }return null;
    });}
    public CompletableFuture<NamedPlaylist> save(NamedPlaylist incoming,String existingId){return work(() -> {
        if(existingId!=null && downloads.active(existingId))throw new IllegalStateException("请先取消该歌单的下载任务再更新");
        synchronized(store){
            NamedPlaylist playlist=incoming;
            if(existingId!=null){
                var existing=store.findPlaylist(existingId).orElseThrow();
                Map<String,ArrayDeque<NamedPlaylist.Entry>> old=new HashMap<>();
                for(var e:existing.entries())old.computeIfAbsent(e.track().identity(),k->new ArrayDeque<>()).add(e);
                List<NamedPlaylist.Entry> merged=new ArrayList<>();
                for(var e:incoming.entries()){
                    var candidates=old.get(e.track().identity());var prior=candidates==null?null:candidates.poll();
                    if(prior!=null)e=new NamedPlaylist.Entry(prior.id(),e.track(),e.durationMillis(),e.location(),e.state(),e.message(),e.downloadTrack(),e.userSelectedVersion());
                    merged.add(e);
                }
                playlist=incoming.configured(existingId,incoming.name(),incoming.directory(),incoming.recursive()).withEntries(merged);
            }
            store.savePlaylist(playlist);return playlist;
        }
    });}
    public CompletableFuture<List<app.musicplayer.model.OnlineTrackInfo>> candidates(NamedPlaylist.Entry entry){return work(() -> downloads.candidates(entry));}
    public CompletableFuture<Void> edit(String id,java.util.function.UnaryOperator<NamedPlaylist> change){return work(() -> {
        if(downloads.active(id))throw new IllegalStateException("请先取消该歌单的下载任务再编辑");
        synchronized(store){store.savePlaylist(change.apply(store.findPlaylist(id).orElseThrow()));}return null;
    });}
    public CompletableFuture<Void> delete(String id){return work(() -> {
        if(downloads.active(id))throw new IllegalStateException("请先取消该歌单的下载任务再删除");store.deletePlaylist(id);return null;
    });}
    public CompletableFuture<Void> shutdown(){importer.close();io.shutdown();return downloads.shutdown().thenRunAsync(() -> {
        boolean interrupted=false;
        while(!io.isTerminated())try{io.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted=true;}
        if(interrupted)Thread.currentThread().interrupt();
    });}
    @Override public void close(){shutdown();}
}
