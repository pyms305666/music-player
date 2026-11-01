package app.musicplayer.android.ui;

import app.musicplayer.android.data.TrackEntry;

/**
 * Android 歌曲列表的不可变显示快照。捕获身份、名称、歌手和当前播放标记，使可变 Track 的元数据更新可被 DiffUtil 检测。 Immutable display fields
 * let DiffUtil detect changes to mutable Track metadata.
 */
public record TrackRow(TrackEntry entry, String key, String title, String artist, String fileName) {
    /** 根据输入构造当前数据类型的简明实例。 */
    public static TrackRow of(TrackEntry entry) {
        return new TrackRow(
                entry,
                entry.key(),
                entry.track().title(),
                entry.track().artist(),
                entry.fileName());
    }
}
