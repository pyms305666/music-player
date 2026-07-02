package app.musicplayer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

final class JsonSupport {
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private JsonSupport() {
    }

    static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    static String queryText(Track track) {
        if (track.artist() == null || track.artist().isBlank() || "未知歌手".equals(track.artist())) {
            return track.title();
        }
        return track.artist() + " " + track.title();
    }

    static List<String> splitTopLevelObjects(String jsonArray) {
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

    static String stringValue(String objectJson, String fieldName) {
        int colon = findFieldColon(objectJson, fieldName);
        if (colon < 0) {
            return null;
        }

        int cursor = skipWhitespace(objectJson, colon + 1);
        if (objectJson == null || cursor >= objectJson.length() || objectJson.startsWith("null", cursor)
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
                    value.append(switch (c) {
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

    static String numberValue(String objectJson, String fieldName) {
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

    static String arrayValue(String objectJson, String fieldName) {
        int colon = findFieldColon(objectJson, fieldName);
        if (colon < 0) {
            return null;
        }
        int start = skipWhitespace(objectJson, colon + 1);
        return enclosedValue(objectJson, start, '[', ']');
    }

    static String objectValue(String objectJson, String fieldName) {
        int colon = findFieldColon(objectJson, fieldName);
        if (colon < 0) {
            return null;
        }
        int start = skipWhitespace(objectJson, colon + 1);
        return enclosedValue(objectJson, start, '{', '}');
    }

    static String firstObject(String arrayJson) {
        List<String> objects = splitTopLevelObjects(arrayJson);
        return objects.isEmpty() ? null : objects.get(0);
    }

    static String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        return htmlDecode(HTML_TAG.matcher(value).replaceAll("")).trim();
    }

    static String htmlDecode(String value) {
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

    static String decodeBase64Text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

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

    private static int skipWhitespace(String value, int cursor) {
        while (value != null && cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static String enclosedValue(String json, int start, char open, char close) {
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
            if (c == '"') {
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
