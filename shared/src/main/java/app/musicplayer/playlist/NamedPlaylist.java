package app.musicplayer.playlist;

import app.musicplayer.model.OnlineTrackInfo;
import java.util.List;
import java.util.UUID;

/** A saved playlist is independent of audio files and the current playback queue. */
public record NamedPlaylist(String id, String name, String source, String sourceId, String sourceUrl,
        String artworkUrl, String creator, int expectedCount, String directory, boolean recursive,
        List<Entry> entries) {
    public enum State {
        MISSING("未下载"), REDOWNLOAD("等待重新下载"), WAITING("等待下载"), DOWNLOADING("下载中"), READY("本地可用"),
        FAILED("下载失败"), CANCELLED("已取消");
        private final String label;
        State(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public record Entry(String id, OnlineTrackInfo track, long durationMillis, String location,
                        State state, String message,OnlineTrackInfo downloadTrack,boolean userSelectedVersion) {
        public Entry(String id,OnlineTrackInfo track,long durationMillis,String location,State state,String message,OnlineTrackInfo downloadTrack){this(id,track,durationMillis,location,state,message,downloadTrack,false);}
        public Entry(String id,OnlineTrackInfo track,long durationMillis,String location,State state,String message){this(id,track,durationMillis,location,state,message,null);}
        public static Entry create(OnlineTrackInfo track, long durationMillis) {
            return new Entry(UUID.randomUUID().toString(), track, durationMillis, "", State.MISSING, "");
        }
        public Entry status(State next, String detail) { return new Entry(id, track, durationMillis, location, next, detail,downloadTrack,userSelectedVersion); }
        public Entry local(String path) { return new Entry(id, track, durationMillis, path, State.READY, "",downloadTrack,userSelectedVersion); }
        public Entry downloadFrom(OnlineTrackInfo info){return new Entry(id,track,durationMillis,location,state,message,info,userSelectedVersion&&info!=null);}
        public Entry selectVersion(OnlineTrackInfo info){return new Entry(id,track,durationMillis,location,state,message,java.util.Objects.requireNonNull(info),true);}
        public Entry prepareDownload(){return userSelectedVersion?this:downloadFrom(null);}
        public Entry reuseLocal(String path){return (userSelectedVersion?this:downloadFrom(null)).local(path);}
        public Entry pendingCopy(State next){return new Entry(id,track,durationMillis,"",next,"",downloadTrack,userSelectedVersion);}
        public String label() { return track.artist() + " - " + track.title(); }
        @Override public String toString() { return label() + " · " + state +(downloadTrack==null?"":" · 下载渠道："+downloadTrack.source()
                +(PlaylistSongMatcher.matches(track,downloadTrack)?"":" · 已选版本："+downloadTrack.artist()+" - "+downloadTrack.title()))+ (message.isBlank() ? "" : " · " + message); }
    }
    public NamedPlaylist { entries = List.copyOf(entries); }
    public NamedPlaylist withEntries(List<Entry> values) {
        return new NamedPlaylist(id, name, source, sourceId, sourceUrl, artworkUrl, creator,
                expectedCount, directory, recursive, values);
    }
    public NamedPlaylist configured(String newId, String newName, String target, boolean subdirectories) {
        return new NamedPlaylist(newId, newName, source, sourceId, sourceUrl, artworkUrl, creator,
                expectedCount, target, subdirectories, entries);
    }
    public boolean complete() { return entries.size() == expectedCount; }
    public String progressLabel() {
        long ready=0,waiting=0,running=0,failed=0;
        for(var entry:entries)switch(entry.state()){
            case READY -> ready++;
            case WAITING -> waiting++;
            case DOWNLOADING -> running++;
            case FAILED -> failed++;
            default -> { }
        }
        return entries.size()+" 首 · 本地可用 "+ready+" · 等待 "+waiting+" · 下载中 "+running+" · 失败 "+failed;
    }
    @Override public String toString() { return name + " · " + source + " · " + entries.size() + " 首"; }
}
