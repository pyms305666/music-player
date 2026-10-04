package app.musicplayer.online;

import java.util.OptionalLong;

/** Byte totals describe the current transfer, never a fabricated completion percentage. */
public record DownloadEvent(Stage stage, long transferredBytes, OptionalLong totalBytes) {
    public enum Stage { QUEUED, RESOLVING, TRANSFERRING, VALIDATING, PUBLISHING, COMPLETE, FAILED, CANCELLED }
    public DownloadEvent {
        transferredBytes = Math.max(0, transferredBytes);
        if (totalBytes.isPresent() && totalBytes.getAsLong() <= 0) totalBytes = OptionalLong.empty();
    }
    public static DownloadEvent of(Stage stage) { return new DownloadEvent(stage, 0, OptionalLong.empty()); }
}
