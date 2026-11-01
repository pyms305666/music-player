package app.musicplayer.android.data;

import app.musicplayer.model.Track;

/** Android 持久化歌曲记录。把共享 Track 与创建时间、存储类型、文件或 content URI 位置及显示文件名关联起来。 */
public record TrackEntry(
        Track track, long createdAt, String location, StorageType storageType, String fileName) {

    /** 区分普通文件与 content URI。MEDIA_STORE 也用于歌单通过 SAF 保存的 URI，沿用原数据库分类。 */
    public enum StorageType {
        FILE,
        MEDIA_STORE
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public TrackEntry(Track track, long createdAt) {
        this(
                track,
                createdAt,
                track.path().toAbsolutePath().toString(),
                StorageType.FILE,
                track.path().getFileName().toString());
    }

    /** 构造以 content URI 为位置的 Android 歌曲记录。 */
    public static TrackEntry mediaStore(
            Track track, long createdAt, String contentUri, String fileName) {
        return new TrackEntry(track, createdAt, contentUri, StorageType.MEDIA_STORE, fileName);
    }

    /** 生成当前数据的稳定键，供去重、索引或缓存使用。 */
    public String key() {
        return location;
    }
}
