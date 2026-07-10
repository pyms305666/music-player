package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.util.List;

/** 一个在线音乐来源的搜索和可播放地址解析能力。 */
interface OnlineSourceProvider {
    String sourceName();

    String referer();

    List<OnlineTrackInfo> search(String query);

    String resolve(OnlineTrackInfo track);

    default OnlineTrackInfo annotateAvailability(OnlineTrackInfo track) {
        String url = resolve(track);
        return url == null || url.isBlank()
                ? track.withAvailability(false, "不可下载")
                : track.withAvailability(true, "可下载");
    }
}
