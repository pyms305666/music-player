package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import java.util.List;

/** Immutable results in arrival order, with source outcomes independent of UI wording. */
public record OnlineSearchSnapshot(String query, List<OnlineTrackInfo> tracks,
                                   List<Source> sources, State state, boolean cached) {
    public enum State { SEARCHING, COMPLETE, EMPTY, PARTIAL_FAILURE, FAILED, CANCELLED }
    public enum Outcome { PENDING, COMPLETE, FAILED, TIMED_OUT, SUSPENDED, CANCELLED }
    public record Source(String name, Outcome outcome) { }
    public OnlineSearchSnapshot {
        tracks = List.copyOf(tracks);
        sources = List.copyOf(sources);
    }
    public boolean finished() { return state != State.SEARCHING; }
    public long completedSources() { return sources.stream().filter(s -> s.outcome() != Outcome.PENDING).count(); }
    public long failedSources() {
        return sources.stream().filter(s -> s.outcome() == Outcome.FAILED
                || s.outcome() == Outcome.TIMED_OUT || s.outcome() == Outcome.SUSPENDED).count();
    }
    public boolean cacheable() {
        return finished() && state != State.CANCELLED && !sources.isEmpty()
                && sources.stream().allMatch(s -> s.outcome() == Outcome.COMPLETE);
    }
    public OnlineSearchSnapshot asCached() { return new OnlineSearchSnapshot(query, tracks, sources, state, true); }
}
