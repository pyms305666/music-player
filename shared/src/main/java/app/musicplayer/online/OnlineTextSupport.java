package app.musicplayer.online;

import app.musicplayer.util.JsonSupport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 在线来源解析时共用的文本清理工具。 */
final class OnlineTextSupport {
    private OnlineTextSupport() {
    }

    static String value(String json, String... names) {
        if (json == null) {
            return null;
        }
        for (String name : names) {
            String text = JsonSupport.stringValue(json, name);
            if (text != null && !text.isBlank()) {
                return text;
            }
            text = JsonSupport.numberValue(json, name);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    static String stripHtml(String value) {
        return value == null ? null : value.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
    }

    static String unescape(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\u0026", "&")
                .replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("&#10;", "\n")
                .replace("&#13;", "\r")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    static String unescapeUnicode(String value) {
        if (value == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, String.valueOf((char) Integer.parseInt(matcher.group(1), 16)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength) + "...";
    }
}
