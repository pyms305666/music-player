package app.musicplayer.online;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** 歌单分享链接识别与校验。识别酷狗、网易云、QQ 分享文本中的平台和歌单身份，限定允许请求的链接域名。 */
public final class PlaylistLinks {
    /** 组合已经识别的歌单平台及标识，供平台导入解析使用。 */
    public record Link(String source, String id, String url) {}

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private PlaylistLinks() {}

    /** 从分享文字中提取支持的平台歌单链接。 */
    public static String extract(String input) {
        var m = Pattern.compile("https?://[^\\s<>\"，。]+").matcher(input == null ? "" : input);
        if (!m.find()) throw new IllegalArgumentException("请粘贴酷狗、网易云或 QQ 音乐的歌单分享链接");
        String url = m.group().replaceAll("[)）\\]】]+$", "");
        validate(url);
        return url;
    }

    /** 检查输入结构或地址是否满足本组件的必要约束。 */
    public static void validate(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (host == null
                || uri.getUserInfo() != null
                || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))
                || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)
                || !(domain(host, "music.163.com")
                        || domain(host, "163cn.tv")
                        || domain(host, "qq.com")
                        || domain(host, "kugou.com")))
            throw new IllegalArgumentException("仅支持三家音乐平台的歌单分享链接");
    }

    /** 提取或检查链接域名，限定歌单导入允许访问的来源。 */
    private static boolean domain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    /** 解析输入文本或链接，生成本组件使用的结构化结果。 */
    public static Link parse(String url) {
        validate(url);
        URI uri = URI.create(url);
        String host = uri.getHost(), path = uri.getPath();
        if (domain(host, "music.163.com")
                && (path.contains("playlist") || value(uri, "id") != null && path.equals("/"))) {
            String id = capture(path, "/playlist/(\\d+)");
            if (id == null) id = value(uri, "id");
            if (id != null && id.matches("\\d+"))
                return new Link("网易云音乐", id, "https://music.163.com/playlist?id=" + id);
        }
        if (domain(host, "qq.com")) {
            String id = capture(path, "/(?:playlist|playsquare)/(\\d+)");
            if (id == null && (path.contains("taoge") || path.contains("playlist"))) {
                id = value(uri, "id");
                if (id == null) id = value(uri, "disstid");
            }
            if (id != null && id.matches("\\d+"))
                return new Link("QQ音乐", id, "https://y.qq.com/n/ryqq/playlist/" + id);
        }
        if (domain(host, "kugou.com")) {
            String id = capture(path, "/(?:single|songlist|list)/([A-Za-z0-9_]+)(?:\\.html|/|$)");
            if (id == null) id = value(uri, "global_collection_id");
            if (id == null) id = value(uri, "specialid");
            if (id != null && id.matches("[A-Za-z0-9_]+"))
                return new Link(
                        "酷狗音乐",
                        id,
                        id.startsWith("gcid_")
                                ? "https://m.kugou.com/songlist/"
                                        + id
                                        + "/?src_cid="
                                        + id.substring(5)
                                        + "&iszlist=1"
                                : "https://www.kugou.com/yy/special/single/" + id + ".html");
        }
        throw new IllegalArgumentException("未识别到歌单。请使用歌单的分享链接，而不是单曲或用户主页链接");
    }

    /** 取得正则表达式匹配的目标分组，没有匹配时返回空值。 */
    private static String capture(String input, String regex) {
        var m = Pattern.compile(regex).matcher(input);
        return m.find() ? m.group(1) : null;
    }

    /** 提取指定字段或当前控件的值，缺失值使用本组件约定的备用结果。 */
    private static String value(URI uri, String name) {
        String input =
                (uri.getRawQuery() == null ? "" : uri.getRawQuery())
                        + "&"
                        + (uri.getRawFragment() == null ? "" : uri.getRawFragment());
        var m = Pattern.compile("(?:^|[?&])" + name + "=([^&]+)").matcher(input);
        return m.find() ? URLDecoder.decode(m.group(1), StandardCharsets.UTF_8) : null;
    }
}
