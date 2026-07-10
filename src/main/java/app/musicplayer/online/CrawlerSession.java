package app.musicplayer.online;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/** 所有在线来源共享的 HTTP 客户端、Cookie 和请求头。 */
final class CrawlerSession {
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
    };
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final Random random = new Random();
    private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .cookieHandler(cookieManager)
            .connectTimeout(Duration.ofSeconds(12))
            .build();
    private boolean primed;

    synchronized void ensurePrimed() {
        if (primed) {
            return;
        }
        primed = true;
        tryFetchHome("https://music.163.com/");
        try {
            HttpCookie csrf = new HttpCookie("__csrf", randomHex(32));
            csrf.setDomain(".music.163.com");
            csrf.setPath("/");
            cookieManager.getCookieStore().add(URI.create("https://music.163.com/"), csrf);
        } catch (RuntimeException ignored) {
        }
        snooze(400);
        tryFetchHome("https://y.qq.com/");
        snooze(400);
        tryFetchHome("https://www.kugou.com/");
    }

    String fetch(String url, String referer) throws IOException, InterruptedException {
        return fetch(url, referer, Map.of());
    }

    String fetch(String url, String referer, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = baseRequest(url, referer)
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "same-origin");
        extraHeaders.forEach(builder::header);
        HttpResponse<String> response = httpClient.send(
                builder.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " " + url);
        }
        return response.body();
    }

    String postForm(String url, String referer, String form, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = baseRequest(url, referer)
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/x-www-form-urlencoded");
        headers.forEach(builder::header);
        HttpResponse<String> response = httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " " + url);
        }
        return response.body();
    }

    HttpResponse<java.io.InputStream> download(String url, String referer)
            throws IOException, InterruptedException {
        HttpRequest request = baseRequest(url, referer)
                .timeout(Duration.ofMinutes(3))
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    String cookieValue(String domain, String name) {
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            if (cookie.getDomain() != null
                    && cookie.getDomain().contains(domain)
                    && name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    String cookieHeader(String url) {
        try {
            List<HttpCookie> cookies = cookieManager.getCookieStore().get(URI.create(url));
            if (cookies.isEmpty()) {
                cookies = cookieManager.getCookieStore().getCookies();
            }
            return cookies.stream()
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; "));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    String userAgent() {
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }

    String randomGuid() {
        return String.format("%010d", random.nextInt(1_000_000_000));
    }

    String randomHex(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append(Integer.toHexString(random.nextInt(16)));
        }
        return result.toString();
    }

    void snooze(long millis) {
        try {
            Thread.sleep(millis + random.nextInt(Math.max(1, (int) (millis / 2))));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpRequest.Builder baseRequest(String url, String referer) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", userAgent())
                .header("Referer", referer)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("Cache-Control", "no-cache");
    }

    private void tryFetchHome(String url) {
        try {
            fetch(url, url);
        } catch (Exception ignored) {
        }
    }
}
