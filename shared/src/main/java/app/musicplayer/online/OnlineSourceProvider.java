package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.util.List;

/** 一个在线音乐来源的搜索和可播放地址解析能力。 */
interface OnlineSourceProvider {
    /** 返回来源的稳定名称，用于结果标记、去重与渠道选择。 */
    String sourceName();

    /** 取得来源要求的 Referer 请求头，供 HTTP 请求使用。 */
    String referer();

    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
    List<OnlineTrackInfo> search(String query);

    /** 准备或解析可实际使用的地址、路径或运行配置。 */
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
