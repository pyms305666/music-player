package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.util.List;

/** 一个在线音乐来源的搜索和可播放地址解析能力。 */
interface OnlineSourceProvider {
    String sourceName();

    String referer();

    List<OnlineTrackInfo> search(String query);

    String resolve(OnlineTrackInfo track);

    /** 解析不到直链时展示的可用性文案。 */
    default String unavailableText() {
        return "VIP/不可下载";
    }

    /** 解析结果是兜底地址（服务器仍可能重定向到真实音频），按“可尝试下载”展示。 */
    default boolean isTentativeUrl(String url) {
        return false;
    }
}
