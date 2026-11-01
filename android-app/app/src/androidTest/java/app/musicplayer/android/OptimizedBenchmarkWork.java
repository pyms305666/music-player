package app.musicplayer.android;

import android.widget.ScrollView;
import android.widget.TextView;

import app.musicplayer.android.data.*;
import app.musicplayer.android.ui.AndroidLyricsPresenter;
import app.musicplayer.model.Lyrics;

import java.util.*;

/**
 * Android 性能测试的工作负载辅助。构造优化后处理流程，供交互和大曲库基准对比。 Direct production API calls; kept separate so legacy
 * APK comparisons never load these types.
 */
final class OptimizedBenchmarkWork {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private OptimizedBenchmarkWork() {}

    /** 构造导入写入负载并收集相关测量。 */
    static void imports(AndroidMusicDatabase database, List<TrackEntry> entries) {
        for (int start = 0;
                start < entries.size();
                start += AndroidMusicDatabase.IMPORT_BATCH_SIZE) {
            List<AndroidMusicDatabase.ImportedTrack> items = new ArrayList<>();
            for (int i = start;
                    i < Math.min(entries.size(), start + AndroidMusicDatabase.IMPORT_BATCH_SIZE);
                    i++)
                items.add(
                        new AndroidMusicDatabase.ImportedTrack(
                                "content://qa-import/" + i, entries.get(i)));
            database.saveImportedTracks(items);
        }
    }

    /** 构造歌词呈现的对比工作负载。 */
    static Object presenter(TextView text, ScrollView scroll) {
        return new AndroidLyricsPresenter(text, scroll);
    }

    /** 展示窗口或当前歌单条目，并保留有效选择状态。 */
    static void show(Object presenter, Lyrics lyrics) {
        ((AndroidLyricsPresenter) presenter).show(lyrics);
    }

    /** 实现测试 ContentProvider 的写接口约定。 */
    static void update(Object presenter, long millis) {
        ((AndroidLyricsPresenter) presenter).update(millis);
    }
}
