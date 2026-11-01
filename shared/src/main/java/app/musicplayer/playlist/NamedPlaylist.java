package app.musicplayer.playlist;

import app.musicplayer.model.OnlineTrackInfo;

import java.util.List;
import java.util.UUID;

/**
 * 共享独立歌单模型。保存歌单身份、来源及条目状态，表示缺失、本地复用、版本选择、下载配置与进度。 A saved playlist is independent of audio files
 * and the current playback queue.
 */
public record NamedPlaylist(
        String id,
        String name,
        String source,
        String sourceId,
        String sourceUrl,
        String artworkUrl,
        String creator,
        int expectedCount,
        String directory,
        boolean recursive,
        List<Entry> entries) {
    /** 表示任务状态。 */
    public enum State {
        MISSING("未下载"),
        REDOWNLOAD("等待重新下载"),
        WAITING("等待下载"),
        DOWNLOADING("下载中"),
        READY("本地可用"),
        FAILED("下载失败"),
        CANCELLED("已取消");
        private final String label;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        State(String label) {
            this.label = label;
        }

        /** 返回适合界面或诊断显示的文本表示。 */
        @Override
        public String toString() {
            return label;
        }
    }

    /** 表示一条歌单或歌曲记录。 */
    public record Entry(
            String id,
            OnlineTrackInfo track,
            long durationMillis,
            String location,
            State state,
            String message,
            OnlineTrackInfo downloadTrack,
            boolean userSelectedVersion) {
        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        public Entry(
                String id,
                OnlineTrackInfo track,
                long durationMillis,
                String location,
                State state,
                String message,
                OnlineTrackInfo downloadTrack) {
            this(id, track, durationMillis, location, state, message, downloadTrack, false);
        }

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        public Entry(
                String id,
                OnlineTrackInfo track,
                long durationMillis,
                String location,
                State state,
                String message) {
            this(id, track, durationMillis, location, state, message, null);
        }

        /** 用输入身份与条目创建新的歌单或本组件实例。 */
        public static Entry create(OnlineTrackInfo track, long durationMillis) {
            return new Entry(
                    UUID.randomUUID().toString(), track, durationMillis, "", State.MISSING, "");
        }

        /** 生成歌单条目或当前任务的可显示状态。 */
        public Entry status(State next, String detail) {
            return new Entry(
                    id,
                    track,
                    durationMillis,
                    location,
                    next,
                    detail,
                    downloadTrack,
                    userSelectedVersion);
        }

        /** 把条目关联到已存在的本地歌曲位置。 */
        public Entry local(String path) {
            return new Entry(
                    id,
                    track,
                    durationMillis,
                    path,
                    State.READY,
                    "",
                    downloadTrack,
                    userSelectedVersion);
        }

        /** 将选择的在线歌曲版本配置为该条目的下载来源。 */
        public Entry downloadFrom(OnlineTrackInfo info) {
            return new Entry(
                    id,
                    track,
                    durationMillis,
                    location,
                    state,
                    message,
                    info,
                    userSelectedVersion && info != null);
        }

        /** 保存用户选择的歌曲版本，以供后续下载。 */
        public Entry selectVersion(OnlineTrackInfo info) {
            return new Entry(
                    id,
                    track,
                    durationMillis,
                    location,
                    state,
                    message,
                    java.util.Objects.requireNonNull(info),
                    true);
        }

        /** 为条目设置下载目的地及重复处理策略。 */
        public Entry prepareDownload() {
            return userSelectedVersion ? this : downloadFrom(null);
        }

        /** 复用已找到的本地歌曲位置，避免重复下载。 */
        public Entry reuseLocal(String path) {
            return (userSelectedVersion ? this : downloadFrom(null)).local(path);
        }

        /** 将条目标记为需要保留独立副本的待下载状态。 */
        public Entry pendingCopy(State next) {
            return new Entry(
                    id, track, durationMillis, "", next, "", downloadTrack, userSelectedVersion);
        }

        /** 生成歌单条目或进度的简短界面标签。 */
        public String label() {
            return track.artist() + " - " + track.title();
        }

        /** 返回适合界面或诊断显示的文本表示。 */
        @Override
        public String toString() {
            return label()
                    + " · "
                    + state
                    + (downloadTrack == null
                            ? ""
                            : " · 下载渠道："
                                    + downloadTrack.source()
                                    + (PlaylistSongMatcher.matches(track, downloadTrack)
                                            ? ""
                                            : " · 已选版本："
                                                    + downloadTrack.artist()
                                                    + " - "
                                                    + downloadTrack.title()))
                    + (message.isBlank() ? "" : " · " + message);
        }
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public NamedPlaylist {
        entries = List.copyOf(entries);
    }

    /** 保留歌单头信息，替换为新的不可变条目列表。 */
    public NamedPlaylist withEntries(List<Entry> values) {
        return new NamedPlaylist(
                id,
                name,
                source,
                sourceId,
                sourceUrl,
                artworkUrl,
                creator,
                expectedCount,
                directory,
                recursive,
                values);
    }

    /** 检查条目是否已经具备下载所需的选择和目录配置。 */
    public NamedPlaylist configured(
            String newId, String newName, String target, boolean subdirectories) {
        return new NamedPlaylist(
                newId,
                newName,
                source,
                sourceId,
                sourceUrl,
                artworkUrl,
                creator,
                expectedCount,
                target,
                subdirectories,
                entries);
    }

    /** 检查或标记当前工作完成，保留最终结果状态。 */
    public boolean complete() {
        return entries.size() == expectedCount;
    }

    /** 生成歌单条目或进度的简短界面标签。 */
    public String progressLabel() {
        long ready = 0, waiting = 0, running = 0, failed = 0;
        for (var entry : entries)
            switch (entry.state()) {
                case READY -> ready++;
                case WAITING -> waiting++;
                case DOWNLOADING -> running++;
                case FAILED -> failed++;
                default -> {}
            }
        return entries.size()
                + " 首 · 本地可用 "
                + ready
                + " · 等待 "
                + waiting
                + " · 下载中 "
                + running
                + " · 失败 "
                + failed;
    }

    /** 返回适合界面或诊断显示的文本表示。 */
    @Override
    public String toString() {
        return name + " · " + source + " · " + entries.size() + " 首";
    }
}
