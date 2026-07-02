// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.URLEncoder;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.charset.StandardCharsets;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.ArrayList;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Base64;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.List;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.regex.Pattern;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
final class JsonSupport {
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private JsonSupport() {
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String encode(String value) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String queryText(Track track) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (track.artist() == null || track.artist().isBlank() || "未知歌手".equals(track.artist())) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return track.title();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return track.artist() + " " + track.title();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static List<String> splitTopLevelObjects(String jsonArray) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<String> objects = new ArrayList<>();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (jsonArray == null || jsonArray.isBlank()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return objects;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String json = jsonArray.trim();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (json.startsWith("[") && json.endsWith("]")) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            json = json.substring(1, json.length() - 1);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        boolean inString = false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        boolean escaped = false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int depth = 0;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int objectStart = -1;

        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (int i = 0; i < json.length(); i++) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            char c = json.charAt(i);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (escaped) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                escaped = false;
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (c == '\\' && inString) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                escaped = true;
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (c == '"') {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                inString = !inString;
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (inString) {
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (c == '{') {
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (depth == 0) {
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    objectStart = i;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                depth++;
            // 说明：追加条件判断：前面的条件不成立时，再检查这个新条件。
            } else if (c == '}') {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                depth--;
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (depth == 0 && objectStart >= 0) {
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    objects.add(json.substring(objectStart, i + 1));
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    objectStart = -1;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return objects;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String stringValue(String objectJson, String fieldName) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int colon = findFieldColon(objectJson, fieldName);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (colon < 0) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int cursor = skipWhitespace(objectJson, colon + 1);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (objectJson == null || cursor >= objectJson.length() || objectJson.startsWith("null", cursor)
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                || objectJson.charAt(cursor) != '"') {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        StringBuilder value = new StringBuilder();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        boolean escaped = false;

        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (int i = cursor + 1; i < objectJson.length(); i++) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            char c = objectJson.charAt(i);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (escaped) {
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (c == 'u' && i + 4 < objectJson.length()) {
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    String hex = objectJson.substring(i + 1, i + 5);
                    // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
                    try {
                        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                        value.append((char) Integer.parseInt(hex, 16));
                    // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
                    } catch (NumberFormatException ignored) {
                        // 忽略异常转义，继续解析后续内容。
                    // 说明：代码块结束，表示前面的大括号范围到这里为止。
                    }
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    i += 4;
                // 说明：否则分支：前面的条件都不成立时，执行这里的代码。
                } else {
                    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
                    value.append(switch (c) {
                        // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
                        case '"' -> "\"";
                        // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
                        case '\\' -> "\\";
                        // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
                        case '/' -> "/";
                        // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
                        case 'b' -> "\b";
                        // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
                        case 'f' -> "\f";
                        // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
                        case 'n' -> "\n";
                        // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
                        case 'r' -> "\r";
                        // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
                        case 't' -> "\t";
                        // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
                        default -> String.valueOf(c);
                    // 说明：代码块结束，表示前面的大括号范围到这里为止。
                    });
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                escaped = false;
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (c == '\\') {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                escaped = true;
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (c == '"') {
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return value.toString();
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            value.append(c);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String numberValue(String objectJson, String fieldName) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int colon = findFieldColon(objectJson, fieldName);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (colon < 0) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int cursor = skipWhitespace(objectJson, colon + 1);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (cursor >= objectJson.length()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (objectJson.charAt(cursor) == '"') {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return stringValue(objectJson, fieldName);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int start = cursor;
        // 说明：while 循环：只要条件继续成立，就反复执行代码块。
        while (cursor < objectJson.length()) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            char c = objectJson.charAt(cursor);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if ((c >= '0' && c <= '9') || c == '-') {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                cursor++;
            // 说明：否则分支：前面的条件都不成立时，执行这里的代码。
            } else {
                // 说明：跳出当前循环或 switch 分支，不再继续执行后面的同级逻辑。
                break;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return cursor > start ? objectJson.substring(start, cursor) : null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String arrayValue(String objectJson, String fieldName) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int colon = findFieldColon(objectJson, fieldName);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (colon < 0) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int start = skipWhitespace(objectJson, colon + 1);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return enclosedValue(objectJson, start, '[', ']');
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String objectValue(String objectJson, String fieldName) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int colon = findFieldColon(objectJson, fieldName);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (colon < 0) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int start = skipWhitespace(objectJson, colon + 1);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return enclosedValue(objectJson, start, '{', '}');
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String firstObject(String arrayJson) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<String> objects = splitTopLevelObjects(arrayJson);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return objects.isEmpty() ? null : objects.get(0);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String stripHtml(String value) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (value == null) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return htmlDecode(HTML_TAG.matcher(value).replaceAll("")).trim();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String htmlDecode(String value) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (value == null) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return value.replace("&#10;", "\n")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&#13;", "\r")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&#32;", " ")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&#39;", "'")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&#40;", "(")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&#41;", ")")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&#45;", "-")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&#58;", ":")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&#91;", "[")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&#93;", "]")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&quot;", "\"")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&apos;", "'")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&amp;", "&")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .replace("&lt;", "<")
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                .replace("&gt;", ">");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String decodeBase64Text(String value) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (value == null || value.isBlank()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IllegalArgumentException ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static int findFieldColon(String objectJson, String fieldName) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (objectJson == null || fieldName == null) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return -1;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String needle = "\"" + fieldName + "\"";
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int fieldIndex = objectJson.indexOf(needle);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (fieldIndex < 0) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return -1;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return objectJson.indexOf(':', fieldIndex + needle.length());
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static int skipWhitespace(String value, int cursor) {
        // 说明：while 循环：只要条件继续成立，就反复执行代码块。
        while (value != null && cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            cursor++;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return cursor;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String enclosedValue(String json, int start, char open, char close) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (json == null || start >= json.length() || json.charAt(start) != open) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        boolean inString = false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        boolean escaped = false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int depth = 0;
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (int i = start; i < json.length(); i++) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            char c = json.charAt(i);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (escaped) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                escaped = false;
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (c == '\\' && inString) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                escaped = true;
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (c == '"') {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                inString = !inString;
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (inString) {
                // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                continue;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (c == open) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                depth++;
            // 说明：追加条件判断：前面的条件不成立时，再检查这个新条件。
            } else if (c == close) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                depth--;
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (depth == 0) {
                    // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                    return json.substring(start, i + 1);
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
