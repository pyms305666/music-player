package app.musicplayer.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/** 共享响应文本及轻量 JSON 辅助。处理 URL 编码、字段片段、HTML 实体和 Base64 文本，支持来源响应解析。 */
public final class JsonSupport {
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private JsonSupport() {}

    /** 按请求或持久化格式编码文本，避免特殊字符改变数据结构。 */
    public static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 提取歌曲名称与歌手形成查询文本，或解析查询参数中的文字。 */
    public static String queryText(app.musicplayer.model.Track track) {
        if (track.artist() == null || track.artist().isBlank() || "未知歌手".equals(track.artist())) {
            return track.title();
        }
        return track.artist() + " " + track.title();
    }

    /** 分离数组或响应中的顶层对象，忽略字符串内部的括号。 */
    public static List<String> splitTopLevelObjects(String jsonArray) {
        List<String> objects = new ArrayList<>();
        if (jsonArray == null || jsonArray.isBlank()) {
            return objects;
        }

        String json = jsonArray.trim();
        if (json.startsWith("[") && json.endsWith("]")) {
            json = json.substring(1, json.length() - 1);
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int objectStart = -1;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    objects.add(json.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }

        return objects;
    }

    /** 从响应文本提取对应类型的字段值。 */
    public static String stringValue(String objectJson, String fieldName) {
        int colon = findFieldColon(objectJson, fieldName);
        if (colon < 0) {
            return null;
        }

        int cursor = skipWhitespace(objectJson, colon + 1);
        if (objectJson == null
                || cursor >= objectJson.length()
                || objectJson.startsWith("null", cursor)
                || objectJson.charAt(cursor) != '"') {
            return null;
        }

        StringBuilder value = new StringBuilder();
        boolean escaped = false;

        for (int i = cursor + 1; i < objectJson.length(); i++) {
            char c = objectJson.charAt(i);
            if (escaped) {
                if (c == 'u' && i + 4 < objectJson.length()) {
                    String hex = objectJson.substring(i + 1, i + 5);
                    try {
                        value.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException ignored) {
                        // 忽略异常转义，继续解析后续内容。
                    }
                    i += 4;
                } else {
                    value.append(
                            switch (c) {
                                case '"' -> "\"";
                                case '\\' -> "\\";
                                case '/' -> "/";
                                case 'b' -> "\b";
                                case 'f' -> "\f";
                                case 'n' -> "\n";
                                case 'r' -> "\r";
                                case 't' -> "\t";
                                default -> String.valueOf(c);
                            });
                }
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                return value.toString();
            }
            value.append(c);
        }

        return null;
    }

    /** 从响应文本提取对应类型的字段值。 */
    public static String numberValue(String objectJson, String fieldName) {
        int colon = findFieldColon(objectJson, fieldName);
        if (colon < 0) {
            return null;
        }

        int cursor = skipWhitespace(objectJson, colon + 1);
        if (cursor >= objectJson.length()) {
            return null;
        }
        if (objectJson.charAt(cursor) == '"') {
            return stringValue(objectJson, fieldName);
        }

        int start = cursor;
        while (cursor < objectJson.length()) {
            char c = objectJson.charAt(cursor);
            if ((c >= '0' && c <= '9') || c == '-') {
                cursor++;
            } else {
                break;
            }
        }
        return cursor > start ? objectJson.substring(start, cursor) : null;
    }

    /** 从响应文本提取对应类型的字段值。 */
    public static String arrayValue(String objectJson, String fieldName) {
        int colon = findFieldColon(objectJson, fieldName);
        if (colon < 0) {
            return null;
        }
        int start = skipWhitespace(objectJson, colon + 1);
        return enclosedValue(objectJson, start, '[', ']');
    }

    /** 从响应文本提取对应类型的字段值。 */
    public static String objectValue(String objectJson, String fieldName) {
        int colon = findFieldColon(objectJson, fieldName);
        if (colon < 0) {
            return null;
        }
        int start = skipWhitespace(objectJson, colon + 1);
        return enclosedValue(objectJson, start, '{', '}');
    }

    /**
     * 检查对象，供本文件的主流程使用。 Checks a complete top-level object using JSON or a provider's single-quoted
     * variant.
     */
    public static boolean isCompleteObject(String json, char quotationMark) {
        if (json == null) return false;
        String text = json.strip();
        return !text.isEmpty() && text.equals(enclosedValue(text, 0, '{', '}', quotationMark));
    }

    /** 取得顶层对象序列中的首个对象。 */
    public static String firstObject(String arrayJson) {
        List<String> objects = splitTopLevelObjects(arrayJson);
        return objects.isEmpty() ? null : objects.get(0);
    }

    /** 移除响应文字中的 HTML 标签，保留适合显示和匹配的文本。 */
    public static String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        return htmlDecode(HTML_TAG.matcher(value).replaceAll("")).trim();
    }

    /** 解码常见 HTML 实体，恢复可显示的歌名或歌手文字。 */
    public static String htmlDecode(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("&#10;", "\n")
                .replace("&#13;", "\r")
                .replace("&#32;", " ")
                .replace("&#39;", "'")
                .replace("&#40;", "(")
                .replace("&#41;", ")")
                .replace("&#45;", "-")
                .replace("&#58;", ":")
                .replace("&#91;", "[")
                .replace("&#93;", "]")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    /** 将 Base64 编码内容还原为响应文本。 */
    public static String decodeBase64Text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** 定位目标字段后的分隔符，避免误匹配字符串内容。 */
    private static int findFieldColon(String objectJson, String fieldName) {
        if (objectJson == null || fieldName == null) {
            return -1;
        }

        String needle = "\"" + fieldName + "\"";
        int fieldIndex = objectJson.indexOf(needle);
        if (fieldIndex < 0) {
            return -1;
        }
        return objectJson.indexOf(':', fieldIndex + needle.length());
    }

    /** 从指定位置跳过空白，返回下一个有效字符位置。 */
    private static int skipWhitespace(String value, int cursor) {
        while (value != null
                && cursor < value.length()
                && Character.isWhitespace(value.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    /** 提取配对括号包围的值，处理字符串转义及嵌套结构。 */
    private static String enclosedValue(String json, int start, char open, char close) {
        return enclosedValue(json, start, open, close, '"');
    }

    /** 提取配对括号包围的值，处理字符串转义及嵌套结构。 */
    private static String enclosedValue(
            String json, int start, char open, char close, char quotationMark) {
        if (json == null || start >= json.length() || json.charAt(start) != open) {
            return null;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == quotationMark) {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}
