package app.musicplayer.online;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/** 所有在线来源共享的 HTTP、Cookie 和请求头；仅使用桌面与 Android 都支持的标准 API。 */
final class CrawlerSession {
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
    };
    private static final int CONNECT_TIMEOUT_MILLIS = 12_000;
    private static final int REQUEST_TIMEOUT_MILLIS = 20_000;

    private final Random random = new Random();
    private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
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
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.putAll(extraHeaders);
        try (DownloadResponse response = execute("GET", url, referer, headers, null, REQUEST_TIMEOUT_MILLIS)) {
            ensureSuccess(response, url);
            return readUtf8(response.body());
        }
    }

    String postForm(String url, String referer, String form, Map<String, String> headers)
            throws IOException, InterruptedException {
        Map<String, String> requestHeaders = new java.util.LinkedHashMap<>();
        requestHeaders.put("Accept", "application/json, text/plain, */*");
        requestHeaders.put("Content-Type", "application/x-www-form-urlencoded");
        requestHeaders.putAll(headers);
        byte[] body = form.getBytes(StandardCharsets.UTF_8);
        try (DownloadResponse response = execute(
                "POST", url, referer, requestHeaders, body, REQUEST_TIMEOUT_MILLIS)) {
            ensureSuccess(response, url);
            return readUtf8(response.body());
        }
    }

    DownloadResponse download(String url, String referer) throws IOException, InterruptedException {
        return execute("GET", url, referer, Map.of(
                "Accept", "*/*",
                "Connection", "keep-alive"
        ), null, 180_000);
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

    private DownloadResponse execute(
            String method,
            String url,
            String referer,
            Map<String, String> headers,
            byte[] body,
            int readTimeout
    ) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        URI uri = URI.create(url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(readTimeout);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", userAgent());
        connection.setRequestProperty("Referer", referer);
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Cache-Control", "no-cache");
        for (Map.Entry<String, List<String>> entry : cookieManager.get(uri, Map.of()).entrySet()) {
            connection.setRequestProperty(entry.getKey(), String.join("; ", entry.getValue()));
        }
        headers.forEach(connection::setRequestProperty);

        if (body != null) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            try (var output = connection.getOutputStream()) {
                output.write(body);
            }
        }

        int statusCode = connection.getResponseCode();
        Map<String, List<String>> responseHeaders = connection.getHeaderFields();
        cookieManager.put(uri, responseHeaders);
        InputStream source = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (source == null) {
            source = new ByteArrayInputStream(new byte[0]);
        }
        InputStream bodyStream = new DisconnectingInputStream(source, connection);
        return new DownloadResponse(statusCode, responseHeaders, bodyStream);
    }

    private static void ensureSuccess(DownloadResponse response, String url) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " " + url);
        }
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void copy(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buffer = new byte[16_384];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            output.write(buffer, 0, length);
        }
    }

    private void tryFetchHome(String url) {
        try {
            fetch(url, url);
        } catch (Exception ignored) {
        }
    }

    record DownloadResponse(int statusCode, Map<String, List<String>> headers, InputStream body)
            implements AutoCloseable {
        String firstHeader(String name) {
            if (headers == null || name == null) {
                return "";
            }
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && name.equalsIgnoreCase(entry.getKey())
                        && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return "";
        }

        @Override
        public void close() throws IOException {
            body.close();
        }
    }

    private static final class DisconnectingInputStream extends FilterInputStream {
        private final HttpURLConnection connection;

        private DisconnectingInputStream(InputStream input, HttpURLConnection connection) {
            super(input);
            this.connection = connection;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                connection.disconnect();
            }
        }
    }
}
