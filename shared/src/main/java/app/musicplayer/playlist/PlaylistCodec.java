package app.musicplayer.playlist;

import app.musicplayer.model.OnlineTrackInfo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

/**
 * 独立歌单持久化的 JSON 编解码器。分别编码歌单头与条目，保留来源、顺序、状态、配置及特殊文本。 Explicit encoding avoids reflective record
 * construction on older Android runtimes.
 */
public final class PlaylistCodec {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private PlaylistCodec() {}

    /** 编码歌单头或条目，或构造当前平台的歌曲记录。 */
    public static String header(NamedPlaylist p) {
        JsonObject j = new JsonObject();
        j.addProperty("id", p.id());
        j.addProperty("name", p.name());
        j.addProperty("source", p.source());
        j.addProperty("sourceId", p.sourceId());
        j.addProperty("sourceUrl", p.sourceUrl());
        j.addProperty("artworkUrl", p.artworkUrl());
        j.addProperty("creator", p.creator());
        j.addProperty("expectedCount", p.expectedCount());
        j.addProperty("directory", p.directory());
        j.addProperty("recursive", p.recursive());
        return j.toString();
    }

    /** 编码歌单头或条目，或构造当前平台的歌曲记录。 */
    public static NamedPlaylist header(String data, List<NamedPlaylist.Entry> entries) {
        JsonObject j = JsonParser.parseString(data).getAsJsonObject();
        return new NamedPlaylist(
                text(j, "id"),
                text(j, "name"),
                text(j, "source"),
                text(j, "sourceId"),
                text(j, "sourceUrl"),
                text(j, "artworkUrl"),
                text(j, "creator"),
                j.get("expectedCount").getAsInt(),
                text(j, "directory"),
                j.get("recursive").getAsBoolean(),
                entries);
    }

    /** 编码歌单头或条目，或构造当前平台的歌曲记录。 */
    public static String entry(NamedPlaylist.Entry e) {
        JsonObject j = new JsonObject();
        OnlineTrackInfo t = e.track();
        j.addProperty("id", e.id());
        j.addProperty("source", t.source());
        j.addProperty("title", t.title());
        j.addProperty("artist", t.artist());
        j.addProperty("album", t.album());
        j.addProperty("artworkUrl", t.artworkUrl());
        j.addProperty("primaryId", t.primaryId());
        j.addProperty("secondaryId", t.secondaryId());
        j.addProperty("durationMillis", e.durationMillis());
        j.addProperty("location", e.location());
        j.addProperty("state", e.state().name());
        j.addProperty("message", e.message());
        j.addProperty("userSelectedVersion", e.userSelectedVersion());
        if (e.downloadTrack() != null) {
            OnlineTrackInfo actual = e.downloadTrack();
            JsonObject channel = new JsonObject();
            channel.addProperty("source", actual.source());
            channel.addProperty("title", actual.title());
            channel.addProperty("artist", actual.artist());
            channel.addProperty("album", actual.album());
            channel.addProperty("artworkUrl", actual.artworkUrl());
            channel.addProperty("primaryId", actual.primaryId());
            channel.addProperty("secondaryId", actual.secondaryId());
            channel.addProperty("availability", actual.availability().name());
            channel.addProperty("availabilityText", actual.availabilityText());
            j.add("downloadTrack", channel);
        }
        return j.toString();
    }

    /** 编码歌单头或条目，或构造当前平台的歌曲记录。 */
    public static NamedPlaylist.Entry entry(String data) {
        JsonObject j = JsonParser.parseString(data).getAsJsonObject();
        var track =
                new OnlineTrackInfo(
                        text(j, "source"),
                        text(j, "title"),
                        text(j, "artist"),
                        text(j, "album"),
                        text(j, "artworkUrl"),
                        text(j, "primaryId"),
                        text(j, "secondaryId"));
        OnlineTrackInfo actual = null;
        if (j.has("downloadTrack") && j.get("downloadTrack").isJsonObject()) {
            var channel = j.getAsJsonObject("downloadTrack");
            actual =
                    new OnlineTrackInfo(
                            text(channel, "source"),
                            text(channel, "title"),
                            text(channel, "artist"),
                            text(channel, "album"),
                            text(channel, "artworkUrl"),
                            text(channel, "primaryId"),
                            text(channel, "secondaryId"),
                            OnlineTrackInfo.Availability.valueOf(text(channel, "availability")),
                            text(channel, "availabilityText"));
        }
        return new NamedPlaylist.Entry(
                text(j, "id"),
                track,
                j.get("durationMillis").getAsLong(),
                text(j, "location"),
                NamedPlaylist.State.valueOf(text(j, "state")),
                text(j, "message"),
                actual,
                actual != null
                        && j.has("userSelectedVersion")
                        && j.get("userSelectedVersion").getAsBoolean());
    }

    /** 按响应字段格式提取文本、数字、数量或名称集合。 */
    private static String text(JsonObject j, String key) {
        return j.has(key) && !j.get(key).isJsonNull() ? j.get(key).getAsString() : "";
    }
}
