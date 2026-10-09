package app.musicplayer.playlist;

import app.musicplayer.data.MusicDatabase;
import app.musicplayer.model.Track;
import app.musicplayer.online.DownloadFiles;
import java.nio.file.*;
import java.util.*;

public final class DesktopPlaylistFiles implements PlaylistFiles, PlaylistDownloads.Publisher {
    private final MusicDatabase database;
    private final TrackLibraryService library=new TrackLibraryService();
    public DesktopPlaylistFiles(MusicDatabase database){this.database=database;}
    @Override public boolean readable(String location){
        try{return !location.isBlank() && Files.isRegularFile(Path.of(location)) && Files.isReadable(Path.of(location));}
        catch(RuntimeException e){return false;}
    }
    @Override public List<PlaylistDuplicates.Local> scan(String directory,boolean recursive){
        Map<String,Track> files=new LinkedHashMap<>();
        for(var track:database.loadTracks())if(readable(track.path().toString()))files.put(track.path().toAbsolutePath().normalize().toString(),track);
        if(!directory.isBlank()&&Files.exists(Path.of(directory)))try(var paths=Files.walk(Path.of(directory),recursive?Integer.MAX_VALUE:1)){
            paths.filter(Files::isRegularFile).filter(library::isSupportedAudio).forEach(path -> {
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                files.putIfAbsent(path.toAbsolutePath().normalize().toString(),new Track(path));
            });
        }catch(java.io.IOException e){throw new IllegalStateException("无法扫描下载目录："+e.getMessage(),e);}
        List<PlaylistDuplicates.Local> result=new ArrayList<>();
        files.forEach((location,track) -> result.add(new PlaylistDuplicates.Local(location,track.title(),track.artist(),"")));
        for(var playlist:database.loadPlaylists())for(var e:playlist.entries())if(readable(e.location()))
            result.addAll(PlaylistDuplicates.associations(e));
        return result;
    }
    @Override public String publish(Path downloaded,NamedPlaylist.Entry entry,String destination) throws Exception{
        Path folder=Path.of(destination);Files.createDirectories(folder);String name=downloaded.getFileName().toString();int dot=name.lastIndexOf('.');
        Path target=DownloadFiles.publish(downloaded,folder,dot>0?name.substring(0,dot):name,dot>0?name.substring(dot):".mp3");
        var metadata=entry.downloadTrack()==null?entry.track():entry.downloadTrack();
        Track track=new Track(target);track.updateMetadata(metadata.title(),metadata.artist());
        database.saveTracks(List.of(track));return target.toAbsolutePath().normalize().toString();
    }
    public List<Track> register(List<String> locations){
        Map<String,Track> saved=new HashMap<>();for(var t:database.loadTracks())saved.put(t.path().toAbsolutePath().normalize().toString(),t);
        List<Track> result=new ArrayList<>();for(String location:locations)if(readable(location))result.add(saved.getOrDefault(location,new Track(Path.of(location))));
        database.saveTracks(result);return List.copyOf(result);
    }
}
