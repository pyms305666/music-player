package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.util.List;
import java.util.function.Supplier;

/** 共享在线测试夹具。提供 HTTP 服务、模拟来源和可控响应，支持取消、超时、解析及下载回归。 */
final class OnlineTestFixtures {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private OnlineTestFixtures() {}

    /** 按测试参数创建歌曲模型或持久化记录。 */
    static OnlineTrackInfo track(String source) {
        return new OnlineTrackInfo(source, "song", "artist", "", null, source, null);
    }

    /** 创建可控来源或发布夹具，使回归不依赖真实网站和用户文件。 */
    static OnlineSourceProvider provider(String name, Supplier<List<OnlineTrackInfo>> search) {
        return new OnlineSourceProvider() {
            /** 返回来源的稳定名称，用于结果标记、去重与渠道选择。 */
            public String sourceName() {
                return name;
            }

            /** 取得来源要求的 Referer 请求头，供 HTTP 请求使用。 */
            public String referer() {
                return "http://localhost/";
            }

            /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
            public List<OnlineTrackInfo> search(String query) {
                return search.get();
            }

            /** 准备或解析可实际使用的地址、路径或运行配置。 */
            public String resolve(OnlineTrackInfo track) {
                return null;
            }
        };
    }
}
