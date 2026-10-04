package app.musicplayer.online;

import java.util.Locale;

/** Shared presentation of typed task states; callers never infer state from these strings. */
public final class OnlineTaskMessages {
    private OnlineTaskMessages() { }
    public static String search(OnlineSearchSnapshot snapshot) {
        String count = "共 " + snapshot.tracks().size() + " 条结果";
        return switch (snapshot.state()) {
            case SEARCHING -> "正在搜索：来源 " + snapshot.completedSources() + "/" + snapshot.sources().size() + "，" + count;
            case COMPLETE -> "搜索完成，" + count + (snapshot.cached() ? "（缓存）" : "");
            case EMPTY -> "没有找到在线结果";
            case PARTIAL_FAILURE -> "部分来源失败（" + snapshot.failedSources() + " 个），" + count;
            case FAILED -> "网络或来源请求失败，请重试";
            case CANCELLED -> "已取消搜索，" + count;
        };
    }
    public static String download(DownloadController.Notice notice) {
        if (notice.error() instanceof DownloadQueueFullException) return "下载队列已满（2 项运行、8 项等待），请稍后重试";
        DownloadEvent event = notice.event();
        String name = notice.track().title();
        if (name != null && name.codePointCount(0, name.length()) > 24) name = name.substring(0, name.offsetByCodePoints(0, 24)) + "…";
        return switch (event.stage()) {
            case QUEUED -> "等待下载：" + name;
            case RESOLVING -> "正在解析下载地址：" + name;
            case TRANSFERRING -> "正在下载：" + name + " · " + bytes(event.transferredBytes())
                    + (event.totalBytes().isPresent() ? " / " + bytes(event.totalBytes().getAsLong())
                    + "（" + Math.min(100, (long) (100.0 * event.transferredBytes() / event.totalBytes().getAsLong())) + "%）" : "");
            case VALIDATING -> "正在校验：" + name;
            case PUBLISHING -> "正在加入曲库：" + name;
            case COMPLETE -> "下载完成：" + name;
            case CANCELLED -> "已取消下载：" + name;
            case FAILED -> "下载失败：" + name + "，可点击重试";
        };
    }
    private static String bytes(long value) {
        if (value < 1024) return value + " B";
        if (value < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KiB", value / 1024.0);
        return String.format(Locale.ROOT, "%.1f MiB", value / (1024.0 * 1024));
    }
}
