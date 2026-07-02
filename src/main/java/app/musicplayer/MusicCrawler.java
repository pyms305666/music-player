// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import org.jsoup.Jsoup;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import org.jsoup.nodes.Document;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import org.jsoup.nodes.Element;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.io.*;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.*;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpClient;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpRequest;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpResponse;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.charset.StandardCharsets;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.file.*;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.time.Duration;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.*;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.concurrent.TimeUnit;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.regex.Matcher;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.regex.Pattern;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.stream.Collectors;

/**
 * Music crawler with anti-crawling bypass.
 *
 * Anti-crawling: shared CookieManager, rotating Chrome UAs,
 * Sec-Fetch headers, curl fallback with cookie injection,
 * randomised delays, audio magic-byte validation.
 *
 * Platforms: NetEase (weapi encryption), QQ (smartbox + vkey), Kugou.
 */
// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public final class MusicCrawler {

    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String[] UA_POOL = {
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    };
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Random RNG = new Random();

    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    final HttpClient http;
    // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
    private static Path curlPath;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private static boolean curlChecked;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final Set<String> failedDownloadKeys = Collections.synchronizedSet(new HashSet<>());

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public MusicCrawler() {
        // 说明：访问当前对象自己的字段或方法，避免和局部变量混淆。
        this.http = HttpClient.newBuilder()
            // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
            .followRedirects(HttpClient.Redirect.ALWAYS)
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .cookieHandler(cookieManager)
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .connectTimeout(Duration.ofSeconds(12))
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            .build();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        primeSessions();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void primeSessions() {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try { fetch("https://music.163.com/", "https://music.163.com/"); } catch (Exception ignored) {}
        // Set a random __csrf cookie (normally set by JS, needed for weapi)
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            HttpCookie csrf = new HttpCookie("__csrf", randomHex(32));
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            csrf.setDomain(".music.163.com"); csrf.setPath("/");
            // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
            cookieManager.getCookieStore().add(URI.create("https://music.163.com/"), csrf);
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) {}
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        snooze(400);
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try { fetch("https://y.qq.com/", "https://y.qq.com/"); } catch (Exception ignored) {}
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        snooze(400);
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try { fetch("https://www.kugou.com/", "https://www.kugou.com/"); } catch (Exception ignored) {}
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ================================================================
    //  Public API
    // ================================================================

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public List<OnlineTrackInfo> search(String query) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String q = (query == null ? "" : query).trim();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (q.isBlank()) return List.of();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<OnlineTrackInfo> all = new ArrayList<>();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        System.out.println("[crawler] search: " + q);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        all.addAll(scrapeQqmp3(q));     snooze(300);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        all.addAll(scrapeNetease(q));   snooze(500);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        all.addAll(scrapeQq(q));        snooze(500);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        all.addAll(scrapeKugou(q));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<OnlineTrackInfo> unique = new ArrayList<>();
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (OnlineTrackInfo r : all)
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (unique.stream().noneMatch(u -> u.title().equals(r.title()) && Objects.equals(u.artist(), r.artist())))
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                unique.add(r);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<OnlineTrackInfo> annotated = new ArrayList<>();
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (OnlineTrackInfo info : unique) annotated.add(annotateAvailability(info));
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        annotated.sort(Comparator
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .comparingInt(MusicCrawler::availabilityPriority)
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                .thenComparingInt(MusicCrawler::sourcePriority));
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        System.out.println("[crawler] total: " + annotated.size());
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return annotated;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public String resolveDownloadUrl(OnlineTrackInfo info) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info == null || info.source() == null) return null;
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return switch (info.source()) {
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "QQMP3"    -> resolveQqmp3Url(info.primaryId());
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "网易云音乐" -> resolveNeteaseUrl(info);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "QQ音乐"      -> resolveQqUrl(info.primaryId());
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "酷狗音乐"    -> resolveKugouUrl(info.primaryId(), info.secondaryId());
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            default           -> null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        };
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
    public Path download(OnlineTrackInfo info, Path targetDir)
            // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
            throws IOException, InterruptedException {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info == null || info.source() == null) throw new IOException("invalid track info");
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!info.canAttemptDownload()) throw new IOException(info.source() + ": " + info.availabilityText());
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Files.createDirectories(targetDir);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        IOException lastError = null;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<OnlineTrackInfo> candidates = new ArrayList<>();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        candidates.add(info);

        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (int round = 0; round < 2; round++) {
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (OnlineTrackInfo candidate : candidates) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String key = downloadKey(candidate);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (failedDownloadKeys.contains(key)) continue;
                // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
                try {
                    // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                    Path downloaded = tryDownloadCandidate(candidate, targetDir);
                    // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                    if (candidate != info) {
                        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                        System.out.println("[crawler] fallback source OK: " + candidate.source() + " - " + candidate.title());
                    // 说明：代码块结束，表示前面的大括号范围到这里为止。
                    }
                    // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                    return downloaded;
                // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
                } catch (IOException e) {
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    failedDownloadKeys.add(key);
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    lastError = e;
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    System.out.println("[crawler] candidate failed: " + candidate.source() + " - " + candidate.title() + " : " + e.getMessage());
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }

            // Round 0 failed: search same track across all sources and try strong alternatives only.
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (round == 0) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                candidates = fallbackCandidates(info);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (candidates.isEmpty()) break;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
        throw lastError != null ? lastError : new IOException(info.source() + ": cannot download track");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
    private Path tryDownloadCandidate(OnlineTrackInfo info, Path targetDir)
            // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
            throws IOException, InterruptedException {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!info.canAttemptDownload()) throw new IOException(info.source() + ": " + info.availabilityText());
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String url = resolveDownloadUrl(info);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (url == null || url.isBlank())
            // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
            throw new IOException(info.source() + ": cannot resolve URL");

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String ext = guessExtension(info, url);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String name = sanitize((info.artist() != null ? info.artist() : "Unknown") + " - " + info.title());
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Path target = uniqueTarget(targetDir, name, ext);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        System.out.println("[crawler] download: " + truncate(url, 120));

        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (curlAvailable()) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            boolean ok = downloadViaCurl(url, target, info.source());
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (ok && validateFile(target)) { System.out.println("[crawler] curl OK -> " + target.getFileName()); return target; }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            safeDelete(target);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        downloadViaJava(url, target, info.source());
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!validateFile(target)) { safeDelete(target); throw new IOException(info.source() + ": unusable file"); }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        System.out.println("[crawler] java OK -> " + target.getFileName());
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return target;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private List<OnlineTrackInfo> fallbackCandidates(OnlineTrackInfo original) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String query = ((original.artist() == null ? "" : original.artist() + " ")
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                + (original.title() == null ? "" : original.title())).trim();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (query.isBlank()) return List.of();
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] trying fallback search: " + query);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            List<OnlineTrackInfo> results = search(query);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            List<OnlineTrackInfo> filtered = new ArrayList<>();
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Set<String> seen = new HashSet<>();
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (OnlineTrackInfo r : results) {
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (sameOnlineTrack(original, r)) continue;
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (failedDownloadKeys.contains(downloadKey(r))) continue;
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (!seen.add(downloadKey(r))) continue;
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (!r.canAttemptDownload()) continue;
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (!strongFallbackMatch(original, r)) continue;
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                filtered.add(r);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (filtered.size() >= 6) break;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            filtered.sort(Comparator.comparingInt(MusicCrawler::sourcePriority));
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return filtered;
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] fallback search failed: " + e.getMessage());
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return List.of();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String downloadKey(OnlineTrackInfo info) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info == null) return "";
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return (info.source() == null ? "" : info.source()) + "|" + (info.primaryId() == null ? "" : info.primaryId());
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static boolean sameOnlineTrack(OnlineTrackInfo a, OnlineTrackInfo b) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Objects.equals(a.source(), b.source()) && Objects.equals(a.primaryId(), b.primaryId());
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static boolean strongFallbackMatch(OnlineTrackInfo original, OnlineTrackInfo candidate) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (original == null || candidate == null) return false;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (isBadFallbackText(candidate.title()) || isBadFallbackText(candidate.artist())) return false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String expectedTitle = normalizeTitle(original.title());
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String actualTitle = normalizeTitle(candidate.title());
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (expectedTitle.isBlank() || actualTitle.isBlank()) return false;

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        boolean titleExact = expectedTitle.equals(actualTitle);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        boolean titleContained = expectedTitle.contains(actualTitle) || actualTitle.contains(expectedTitle);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!titleExact && !titleContained) return false;

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String expectedArtist = normalizeArtist(original.artist());
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String actualArtist = normalizeArtist(candidate.artist());
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        boolean artistKnown = !expectedArtist.isBlank() && !actualArtist.isBlank();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        boolean artistMatches = artistKnown && (expectedArtist.equals(actualArtist)
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                || expectedArtist.contains(actualArtist) || actualArtist.contains(expectedArtist));

        // If title is only a partial/contained match, require matching artist.
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!titleExact && !artistMatches) return false;
        // If title is exact, allow unknown artist, otherwise prefer same artist.
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return titleExact || artistMatches;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static boolean titleLooksRelated(String expected, String actual) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String e = normalizeTitle(expected);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String a = normalizeTitle(actual);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return !e.isBlank() && !a.isBlank() && (e.equals(a) || e.contains(a) || a.contains(e));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static boolean isBadFallbackText(String value) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (value == null) return false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String lower = value.toLowerCase(Locale.ROOT);
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (String term : List.of("MusicPart", "儿歌", "童谣", "预告", "片段", "串烧", "铃声", "故事", "伴奏", "广播剧")) {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (lower.contains(term.toLowerCase(Locale.ROOT))) return true;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return false;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String normalizeArtist(String value) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String n = normalizeTitle(value);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String unknown = normalizeTitle("未知歌手");
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return n.equals(unknown) ? "" : n;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String normalizeTitle(String value) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (value == null) return "";
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        StringBuilder out = new StringBuilder();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int nestedAscii = 0;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int nestedFullWidth = 0;
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (int i = 0; i < value.length(); i++) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            char ch = Character.toLowerCase(value.charAt(i));
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (ch == '(') { nestedAscii++; continue; }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (ch == ')' && nestedAscii > 0) { nestedAscii--; continue; }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (ch == 0xff08) { nestedFullWidth++; continue; }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (ch == 0xff09 && nestedFullWidth > 0) { nestedFullWidth--; continue; }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (nestedAscii > 0 || nestedFullWidth > 0) continue;
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (isChinese(ch) || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) out.append(ch);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return out.toString().trim();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static boolean isChinese(char ch) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return ch >= 0x4e00 && ch <= 0x9fff;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static int sourcePriority(OnlineTrackInfo info) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info == null || info.source() == null) return 9;
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return switch (info.source()) {
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "QQMP3" -> 0;
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "QQ音乐" -> 1;
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "网易云音乐" -> 2;
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "酷狗音乐" -> 3;
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            default -> 9;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        };
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private OnlineTrackInfo annotateAvailability(OnlineTrackInfo info) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info == null || info.source() == null) return info;
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return switch (info.source()) {
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "QQMP3" -> annotateQqmp3Availability(info);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "QQ音乐" -> annotateQqAvailability(info);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "酷狗音乐" -> annotateKugouAvailability(info);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "网易云音乐" -> annotateNeteaseAvailability(info);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            default -> info.withAvailability(false, "未知来源");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        };
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private OnlineTrackInfo annotateQqmp3Availability(OnlineTrackInfo info) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String url = resolveQqmp3Url(info.primaryId());
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return (url == null || url.isBlank())
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    ? info.withAvailability(false, "不可下载")
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    : info.withAvailability(true, "可下载");
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return info.withAvailability(false, "不可下载");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private OnlineTrackInfo annotateQqAvailability(OnlineTrackInfo info) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String url = resolveQqUrl(info.primaryId());
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return (url == null || url.isBlank())
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    ? info.withAvailability(false, "VIP/不可下载")
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    : info.withAvailability(true, "可下载");
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return info.withAvailability(false, "不可下载");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private OnlineTrackInfo annotateKugouAvailability(OnlineTrackInfo info) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String url = resolveKugouUrl(info.primaryId(), info.secondaryId());
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return (url == null || url.isBlank())
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    ? info.withAvailability(false, "VIP/不可下载")
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    : info.withAvailability(true, "可下载");
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return info.withAvailability(false, "不可下载");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private OnlineTrackInfo annotateNeteaseAvailability(OnlineTrackInfo info) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String url = resolveNeteaseUrl(info);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (url == null || url.isBlank()) return info.withAvailability(false, "不可下载");
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (url.contains("/song/media/outer/url")) return info.withAvailability(false, "可尝试下载");
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return info.withAvailability(true, "可下载");
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return info.withAvailability(false, "不可下载");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static int availabilityPriority(OnlineTrackInfo info) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info == null) return 9;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info.downloadable()) return 0;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if ("可尝试下载".equals(info.availabilityText())) return 1;
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return 2;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static Path uniqueTarget(Path targetDir, String name, String ext) {
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Path target = targetDir.resolve(name + ext);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int c = 1;
        // 说明：while 循环：只要条件继续成立，就反复执行代码块。
        while (Files.exists(target)) target = targetDir.resolve(name + " (" + (++c) + ")" + ext);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return target;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ================================================================
    //  QQMP3 (search + direct audio URL)
    // ================================================================

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private List<OnlineTrackInfo> scrapeQqmp3(String query) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<OnlineTrackInfo> list = new ArrayList<>();
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = fetch(
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    "https://www.qqmp3.vip/api/songs.php?type=search&keyword=" + encode(query),
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    "https://www.qqmp3.vip/",
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    Map.of(
                            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                            "Accept", "application/json, text/plain, */*",
                            // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
                            "X-Requested-With", "XMLHttpRequest"
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    ));
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String data = JsonSupport.arrayValue(json, "data");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Set<String> seen = new HashSet<>();
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (String item : JsonSupport.splitTopLevelObjects(data)) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String rid = JsonSupport.stringValue(item, "rid");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (rid == null || rid.isBlank()) rid = JsonSupport.numberValue(item, "rid");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String title = JsonSupport.stringValue(item, "name");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (rid == null || rid.isBlank() || title == null || title.isBlank() || !seen.add(rid)) continue;
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String artist = JsonSupport.stringValue(item, "artist");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (artist == null || artist.isBlank()) artist = "未知歌手";
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String cover = JsonSupport.stringValue(item, "pic");
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                list.add(new OnlineTrackInfo("QQMP3", title, artist, "", cover, rid, ""));
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                System.out.println("[crawler] qqmp3: " + title + " - " + artist);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (list.size() >= 12) break;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (list.isEmpty()) System.out.println("[crawler] qqmp3: no songs");
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] qqmp3 search err: " + e.getMessage());
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return list;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String resolveQqmp3Url(String rid) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = fetchQqmp3SongData(rid);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String data = JsonSupport.objectValue(json, "data");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String url = value(data, "url", "play_url", "playUrl");
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (url == null || url.isBlank()) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                url = value(json, "url", "play_url", "playUrl");
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (url != null && !url.isBlank()) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                System.out.println("[crawler] qqmp3 url: " + truncate(url, 80));
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return url;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] qqmp3: no direct url");
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] qqmp3 resolve err: " + e.getMessage());
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    String fetchQqmp3SongData(String rid) throws Exception {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return fetch(
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                "https://www.qqmp3.vip/api/kw.php?rid=" + encode(rid) + "&type=json&level=exhigh&lrc=true",
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                "https://www.qqmp3.vip/",
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                Map.of(
                        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                        "Accept", "application/json, text/plain, */*",
                        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
                        "X-Requested-With", "XMLHttpRequest"
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                ));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ================================================================
    //  NetEase (weapi + fallback)
    // ================================================================

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private List<OnlineTrackInfo> scrapeNetease(String query) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<OnlineTrackInfo> list = new ArrayList<>();
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String csrf = getCookieValue("music.163.com", "__csrf");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = fetch("https://music.163.com/api/search/get/web?csrf_token="
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + (csrf != null ? csrf : "")
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "&type=1&offset=0&limit=8&s=" + encode(query),
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    "https://music.163.com/");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String songs = JsonSupport.arrayValue(json, "songs");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Set<String> seen = new HashSet<>();
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (String song : JsonSupport.splitTopLevelObjects(songs)) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String id = JsonSupport.numberValue(song, "id");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String title = JsonSupport.stringValue(song, "name");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (id == null || id.equals("0") || title == null || title.isBlank() || !seen.add(id)) continue;

                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String artist = "未知歌手";
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String artists = JsonSupport.arrayValue(song, "artists");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String firstArtist = JsonSupport.firstObject(artists);
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String parsedArtist = JsonSupport.stringValue(firstArtist, "name");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (parsedArtist != null && !parsedArtist.isBlank()) artist = parsedArtist;

                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String album = "";
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String cover = null;
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String albumObject = JsonSupport.objectValue(song, "album");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (albumObject != null) {
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    String parsedAlbum = JsonSupport.stringValue(albumObject, "name");
                    // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                    if (parsedAlbum != null) album = parsedAlbum;
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    cover = JsonSupport.stringValue(albumObject, "picUrl");
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }

                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                list.add(new OnlineTrackInfo("网易云音乐", title, artist, album, cover, id, null));
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                System.out.println("[crawler] netease: " + title + " - " + artist);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (list.size() >= 8) break;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (list.isEmpty()) addNeteaseRegexFallback(json, list);
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) { System.out.println("[crawler] netease search err: " + e.getMessage()); }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return list;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static void addNeteaseRegexFallback(String json, List<OnlineTrackInfo> list) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (json == null || json.isBlank()) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Pattern songP = Pattern.compile("\"id\"\\s*:\\s*(\\d+).*?\"name\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Matcher m = songP.matcher(json);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Set<String> seen = new HashSet<>();
        // 说明：while 循环：只要条件继续成立，就反复执行代码块。
        while (m.find() && list.size() < 8) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String id = m.group(1);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String title = unescape(m.group(2));
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (id.equals("0") || title == null || title.isBlank() || !seen.add(id)) continue;
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            list.add(new OnlineTrackInfo("网易云音乐", title, "未知歌手", "", null, id, null));
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] netease: " + title + " - 未知歌手");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String resolveNeteaseUrl(OnlineTrackInfo info) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String csrf = getCookieValue("music.163.com", "__csrf");
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (csrf == null || csrf.isBlank()) csrf = "";
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String body = "{\"ids\":\"[" + info.primaryId() + "]\",\"level\":\"standard\",\"encodeType\":\"mp3\",\"csrf_token\":\"" + csrf + "\"}";
            
            // Try eapi first (newer protocol)
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            String pagePath = "/api/song/enhance/player/url/v1";
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            String eapiText = "nobody" + pagePath + "use" + body + "md5forencrypt";
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String[] eapiEnc = NeteaseCrypto.eapiEncrypt(eapiText);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String eapiJson = postWeapi("https://music.163.com/eapi/song/enhance/player/url/v1?csrf_token=" + csrf, eapiEnc[0], eapiEnc[1]);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (eapiJson != null && !eapiJson.isBlank()) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                java.util.regex.Pattern pu = java.util.regex.Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                Matcher m = pu.matcher(eapiJson);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (m.find()) { String u = unescape(m.group(1)); if (!u.isBlank()) { System.out.println("[crawler] netease eapi OK"); return u; } }
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] netease eapi: no url");
            
            // Fallback to weapi
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String[] enc = NeteaseCrypto.encrypt(body);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = postWeapi("https://music.163.com/weapi/song/enhance/player/url/v1?csrf_token=" + csrf, enc[0], enc[1]);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (json != null) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                java.util.regex.Pattern pu = java.util.regex.Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                Matcher m = pu.matcher(json);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (m.find()) { String u = unescape(m.group(1)); if (!u.isBlank()) { System.out.println("[crawler] netease weapi OK"); return u; } }
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                System.out.println("[crawler] netease weapi: no url in response");
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) { System.out.println("[crawler] netease weapi err: " + e.getMessage()); }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return "https://music.163.com/song/media/outer/url?id=" + info.primaryId() + ".mp3";
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String postWeapi(String url, String params, String encSecKey) throws Exception {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String form = "params=" + URLEncoder.encode(params, StandardCharsets.UTF_8)
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            + "&encSecKey=" + URLEncoder.encode(encSecKey, StandardCharsets.UTF_8);
        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .timeout(REQUEST_TIMEOUT)
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .header("User-Agent", ua()).header("Referer", "https://music.163.com/")
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .header("Accept", "application/json, text/plain, */*")
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .header("Accept-Encoding", "identity")
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            .header("Content-Type", "application/x-www-form-urlencoded");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String ck = dumpCookiesForUrl("https://music.163.com/");
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (ck != null && !ck.isBlank()) rb.header("Cookie", ck);

        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpRequest req = rb.POST(HttpRequest.BodyPublishers.ofString(form)).build();
        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String body = resp.body();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (resp.statusCode() < 200 || resp.statusCode() >= 400) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] netease encrypted API HTTP " + resp.statusCode());
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (body == null || body.isBlank()) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] netease encrypted API returned empty body");
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return body;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ================================================================
    //  QQ Music (smartbox search + vkey download)
    // ================================================================

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private List<OnlineTrackInfo> scrapeQq(String query) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<OnlineTrackInfo> list = new ArrayList<>();
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = fetch("https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?format=json&key="
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    + encode(query)
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "&g_tk=5381&loginUin=0&hostUin=0&inCharset=utf8&outCharset=utf-8"
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "&notice=0&platform=yqq.json&needNewCode=0", "https://y.qq.com/");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String songObject = JsonSupport.objectValue(json, "song");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String itemList = JsonSupport.arrayValue(songObject, "itemlist");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Set<String> seen = new HashSet<>();
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (String item : JsonSupport.splitTopLevelObjects(itemList)) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String mid = JsonSupport.stringValue(item, "mid");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String title = JsonSupport.stringValue(item, "name");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (mid == null || mid.isBlank() || title == null || title.isBlank() || !seen.add(mid)) continue;
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String singer = JsonSupport.stringValue(item, "singer");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (singer == null || singer.isBlank()) singer = "未知歌手";
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                list.add(new OnlineTrackInfo("QQ音乐", title, singer, "", null, mid, ""));
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                System.out.println("[crawler] qq: " + title + " - " + singer);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (list.size() >= 10) break;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (list.isEmpty()) System.out.println("[crawler] qq smartbox: no songs");
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) { System.out.println("[crawler] qq search err: " + e.getMessage()); }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return list;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String resolveQqUrl(String songMid) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String body = "{\"req_0\":{\"module\":\"vkey.GetVkeyServer\",\"method\":\"CgiGetVkey\",\"param\":{\"guid\":\"" + randomGuid() + "\",\"songmid\":[\"" + songMid + "\"],\"songtype\":[0],\"uin\":\"0\",\"loginflag\":0,\"platform\":\"20\"}}}";
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = fetch("https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=" + encode(body), "https://y.qq.com/");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            java.util.regex.Pattern pp = java.util.regex.Pattern.compile("\"purl\"\\s*:\\s*\"([^\"]+)\"");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Matcher pm = pp.matcher(json);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (!pm.find() || pm.group(1).isBlank()) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                System.out.println("[crawler] qq vkey: empty purl (VIP)");
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return null;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String purl = unescapeUnicode(pm.group(1));
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String cdn = "http://aqqmusic.tc.qq.com/";
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            java.util.regex.Pattern sp = java.util.regex.Pattern.compile("\"sip\"\\s*:\\s*\\[\"([^\"]+)\"");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Matcher sm = sp.matcher(json);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (sm.find()) cdn = sm.group(1);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] qq purl: " + truncate(purl, 80));
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return cdn + purl;
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) { System.out.println("[crawler] qq vkey err: " + e.getMessage()); }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ================================================================
    //  Kugou (search works, but downloads mostly VIP)
    // ================================================================

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private List<OnlineTrackInfo> scrapeKugou(String query) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<OnlineTrackInfo> list = new ArrayList<>();
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = fetch("https://songsearch.kugou.com/song_search_v2?page=1&pagesize=10&userid=-1&clientver=&platform=WebFilter&tag=em&filter=2&iscorrection=1&privilege_filter=0&keyword=" + encode(query), "https://www.kugou.com/");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            java.util.regex.Pattern hp = java.util.regex.Pattern.compile("\"(?:FileHash|Hash)\"\\s*:\\s*\"([^\"]+)\"");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            java.util.regex.Pattern np = java.util.regex.Pattern.compile("\"(?:SongName|FileName)\"\\s*:\\s*\"([^\"]+)\"");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            java.util.regex.Pattern sp = java.util.regex.Pattern.compile("\"SingerName\"\\s*:\\s*\"([^\"]+)\"");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            java.util.regex.Pattern ip = java.util.regex.Pattern.compile("\"Image\"\\s*:\\s*\"([^\"]+)\"");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            java.util.regex.Pattern ap = java.util.regex.Pattern.compile("\"(?:AlbumID|AlbumId)\"\\s*:\\s*(\\d+)");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Matcher h = hp.matcher(json), n = np.matcher(json), s = sp.matcher(json), i = ip.matcher(json), a = ap.matcher(json);
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (int j = 0; j < 10 && h.find(); j++) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String hash = h.group(1), title = n.find() ? stripHtml(unescape(n.group(1))) : "?";
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String singer = s.find() ? stripHtml(unescape(s.group(1))) : "未知歌手";
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String cover = i.find() ? i.group(1) : null, aid = a.find() ? a.group(1) : "";
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                list.add(new OnlineTrackInfo("酷狗音乐", title, singer, "", cover, hash, aid));
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                System.out.println("[crawler] kugou: " + title + " - " + singer);
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) { System.out.println("[crawler] kugou search err: " + e.getMessage()); }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return list;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String resolveKugouUrl(String hash, String albumId) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String url = "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&platid=4&hash=" + encode(hash);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (albumId != null && !albumId.isBlank()) url += "&album_id=" + encode(albumId);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            url += "&mid=" + randomHex(32);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = fetch(url, "https://www.kugou.com/");
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (String field : new String[]{"play_url", "url", "play_backup_url"}) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                Matcher m = p.matcher(json);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (m.find() && !m.group(1).isBlank()) return unescape(m.group(1));
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            System.out.println("[crawler] kugou resolve: no play_url (VIP)");
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) { System.out.println("[crawler] kugou resolve err: " + e.getMessage()); }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ================================================================
    //  Download engines
    // ================================================================

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private boolean downloadViaCurl(String url, Path target, String source) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String cookieHeader = dumpCookiesForUrl(url);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            List<String> cmd = new ArrayList<>(List.of(
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                curlPath.toString(), "-L", "-f",
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                "-A", ua(), "-H", "Accept: */*",
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                "-H", "Accept-Language: zh-CN,zh;q=0.9",
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                "-H", "Referer: " + refererFor(source),
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                "-H", "Connection: keep-alive",
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                "--connect-timeout", "12", "--max-time", "60",
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                "--retry", "1", "--retry-delay", "2",
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                "-o", target.toString()
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            ));
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (cookieHeader != null && !cookieHeader.isBlank()) { cmd.add("-b"); cmd.add(cookieHeader); }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            cmd.add(url);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            ProcessBuilder pb = new ProcessBuilder(cmd);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Process p = pb.start();
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            boolean done = p.waitFor(65, TimeUnit.SECONDS);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (!done) { p.destroyForcibly(); return false; }
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return p.exitValue() == 0;
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) { System.out.println("[crawler] curl err: " + e.getMessage()); return false; }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void downloadViaJava(String url, Path target, String source) throws IOException, InterruptedException {
        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .timeout(Duration.ofMinutes(3))
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .header("User-Agent", ua()).header("Accept", "*/*")
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .header("Referer", refererFor(source))
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            .header("Connection", "keep-alive").GET().build();
        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int sc = resp.statusCode();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        System.out.println("[crawler] java download HTTP " + sc);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (sc < 200 || sc >= 400) throw new IOException("HTTP " + sc);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String ct = resp.headers().firstValue("Content-Type").orElse("").toLowerCase();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (ct.contains("text/html")) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            byte[] peek = resp.body().readNBytes(512);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (new String(peek, StandardCharsets.UTF_8).toLowerCase().contains("<!doctype"))
                // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
                throw new IOException("server returned HTML");
            // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
            try (OutputStream os = Files.newOutputStream(target)) { os.write(peek); resp.body().transferTo(os); }
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (OutputStream os = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            resp.body().transferTo(os);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ================================================================
    //  HTTP helper
    // ================================================================

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    String fetch(String url, String referer) throws Exception {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return fetch(url, referer, Map.of());
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    String fetch(String url, String referer, Map<String, String> extraHeaders) throws Exception {
        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .timeout(REQUEST_TIMEOUT)
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .header("User-Agent", ua()).header("Referer", referer)
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .header("Accept-Encoding", "identity")
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .header("Cache-Control", "no-cache")
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            .header("Sec-Fetch-Dest", "document").header("Sec-Fetch-Mode", "navigate").header("Sec-Fetch-Site", "same-origin");
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (extraHeaders != null) {
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (entry.getKey() != null && entry.getValue() != null) {
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    req.header(entry.getKey(), entry.getValue());
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpRequest request = req.GET().build();
        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (resp.statusCode() < 200 || resp.statusCode() >= 400)
            // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
            throw new IOException("HTTP " + resp.statusCode() + " " + url);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return resp.body();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ================================================================
    //  Cookie helpers
    // ================================================================

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String getCookieValue(String domain, String name) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try { for (HttpCookie ck : cookieManager.getCookieStore().getCookies()) if (ck.getDomain() != null && ck.getDomain().contains(domain) && name.equals(ck.getName())) return ck.getValue(); } catch (Exception ignored) {}
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String dumpCookiesForUrl(String url) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
            URI uri = URI.create(url);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            List<HttpCookie> all = cookieManager.getCookieStore().get(uri);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (all.isEmpty()) all = cookieManager.getCookieStore().getCookies();
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return all.stream().map(ck -> ck.getName() + "=" + ck.getValue()).collect(Collectors.joining("; "));
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) { return ""; }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ================================================================
    //  Utilities
    // ================================================================

    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    private void snooze(long ms) { try { Thread.sleep(ms + RNG.nextInt((int)(ms / 2))); } catch (InterruptedException ignored) {} }
    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    private String ua() { return UA_POOL[RNG.nextInt(UA_POOL.length)]; }

    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    private static String refererFor(String s) { return switch (s) { case "QQMP3" -> "https://www.qqmp3.vip/"; case "网易云音乐" -> "https://music.163.com/"; case "QQ音乐" -> "https://y.qq.com/"; case "酷狗音乐" -> "https://www.kugou.com/"; default -> "https://music.163.com/"; }; }
    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    private static String randomGuid() { return String.format("%010d", RNG.nextInt(1_000_000_000)); }
    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private static String randomHex(int len) { StringBuilder sb = new StringBuilder(len); for (int i = 0; i < len; i++) sb.append(Integer.toHexString(RNG.nextInt(16))); return sb.toString(); }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String value(String json, String... names) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (json == null) return null;
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (String name : names) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String text = JsonSupport.stringValue(json, name);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (text != null && !text.isBlank()) return text;
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            text = JsonSupport.numberValue(json, name);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (text != null && !text.isBlank()) return text;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static synchronized boolean curlAvailable() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (curlChecked) return curlPath != null;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        curlChecked = true;
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (String loc : new String[]{"C:\\Windows\\System32\\curl.exe", "curl.exe", "curl"}) {
            // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
            try { Process p = new ProcessBuilder(loc, "--version").redirectErrorStream(true).start();
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (p.waitFor(4, TimeUnit.SECONDS) && p.exitValue() == 0) { curlPath = Path.of(loc); System.out.println("[crawler] curl: " + curlPath); return true; } } catch (Exception ignored) {}
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return false;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String extractMeta(String html, String attr, String key) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (html == null) return null;
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try { Document doc = Jsoup.parse(html); for (Element meta : doc.select("meta")) if (attr.equals(meta.attr(key))) { String c = meta.attr("content"); if (c != null && !c.isBlank()) return c; } } catch (Exception ignored) {}
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<meta\\s+[^>]*" + key + "\\s*=\\s*[\"']" + java.util.regex.Pattern.quote(attr) + "[\"'][^>]*content\\s*=\\s*[\"']([^\"']+)[\"']");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Matcher m = p.matcher(html); return m.find() ? m.group(1) : null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private static String stripHtml(String s) { return s == null ? null : s.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim(); }
    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private static String unescape(String s) { if (s == null) return null; return s.replace("\\u0026", "&").replace("\\u003c", "<").replace("\\u003e", ">").replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\").replace("&#10;", "\n").replace("&#13;", "\r").replace("&#39;", "'").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">"); }

    /** Decode \\uXXXX in QQ purl. Avoids literal \\u in source to prevent Java preprocessor issues. */
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String unescapeUnicode(String s) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (s == null) return null;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        char bs = 0x5c;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(bs + "" + bs + "u([0-9a-fA-F]{4})");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Matcher m = p.matcher(s);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        StringBuilder sb = new StringBuilder();
        // 说明：while 循环：只要条件继续成立，就反复执行代码块。
        while (m.find()) m.appendReplacement(sb, String.valueOf((char) Integer.parseInt(m.group(1), 16)));
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        m.appendTail(sb);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return sb.toString();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    private static String sanitize(String n) { return n.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim(); }
    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private static String encode(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private static String truncate(String s, int max) { return s.length() <= max ? s : s.substring(0, max) + "..."; }
    // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
    private static void safeDelete(Path p) { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static boolean validateFile(Path p) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try { long sz = Files.size(p); if (sz < 32768) { System.out.println("[crawler] file too small: " + sz); return false; }
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            byte[] head = new byte[256]; try (InputStream is = Files.newInputStream(p)) { is.read(head); if (!isAudioContent(head, 256)) { System.out.println("[crawler] not valid audio"); return false; } }
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            System.out.println("[crawler] validated: " + sz + " bytes"); return true; } catch (Exception e) { return false; }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String guessExtension(OnlineTrackInfo info, String url) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lower.contains(".flac")) return ".flac";
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lower.contains(".m4a") || lower.contains(".mp4")) return ".m4a";
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lower.contains(".aac")) return ".aac";
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lower.contains(".wav")) return ".wav";
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return (info.source() != null && info.source().contains("QQ")) ? ".m4a" : ".mp3";
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static boolean isAudioContent(byte[] d, int len) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (len < 4) return false;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if ((d[0] == 'I' && d[1] == 'D' && d[2] == '3') || ((d[0] & 0xFF) == 0xFF && (d[1] & 0xE0) == 0xE0)) return true;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (len > 8 && d[4] == 'f' && d[5] == 't' && d[6] == 'y' && d[7] == 'p') return true;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F') return true;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (d[0] == 'f' && d[1] == 'L' && d[2] == 'a' && d[3] == 'C') return true;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (d[0] == 'O' && d[1] == 'g' && d[2] == 'g' && d[3] == 'S') return true;
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return (d[0] & 0xFF) == 0xFF && (d[1] & 0xF0) == 0xF0;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
