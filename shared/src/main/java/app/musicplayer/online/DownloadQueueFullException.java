package app.musicplayer.online;

import java.util.concurrent.RejectedExecutionException;

public final class DownloadQueueFullException extends RejectedExecutionException {
    public DownloadQueueFullException() { super("Download queue is full"); }
}
