package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import java.util.List;
import java.util.function.Supplier;

final class OnlineTestFixtures {
    private OnlineTestFixtures() { }
    static OnlineTrackInfo track(String source) {
        return new OnlineTrackInfo(source, "song", "artist", "", null, source, null);
    }
    static OnlineSourceProvider provider(String name, Supplier<List<OnlineTrackInfo>> search) {
        return new OnlineSourceProvider() {
            public String sourceName() { return name; }
            public String referer() { return "http://localhost/"; }
            public List<OnlineTrackInfo> search(String query) { return search.get(); }
            public String resolve(OnlineTrackInfo track) { return null; }
        };
    }
}
