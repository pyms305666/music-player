package app.musicplayer.online;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 共享 HTTP 会话与取消作用域。复用 OkHttp 连接和 Cookie，支持线程局部截止时间、请求取消、文本读取及流式下载。
 *
 * <p>Shared HTTP session. Synchronous calls stay on task workers; cancellation closes only their
 * socket.
 */
public final class CrawlerSession implements AutoCloseable {
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                + " Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                + " Chrome/132.0.0.0 Safari/537.36"
    };
    private static final int CONNECT_TIMEOUT_MILLIS = 12_000;
    private static final int REQUEST_TIMEOUT_MILLIS = 20_000;

    private final Random random = new Random();
    private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private boolean primed;
    private final ThreadLocal<Long> deadline = new ThreadLocal<>();
    private final ThreadLocal<RequestCancellation> cancellation = new ThreadLocal<>();
    private final java.util.Set<Call> active = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private OkHttpClient client;
    private volatile boolean closed;

    /** 设置当前线程请求的截止时间，不影响其他并行请求。 */
    void setDeadline(long nanos) {
        deadline.set(nanos);
    }

    /** 清除当前线程的临时截止时间，避免影响同线程的下一次请求。 */
    void clearDeadline() {
        deadline.remove();
    }

    /** 将取消令牌绑定到当前请求作用域，关闭后恢复原来的令牌。 */
    RequestCancellation.Registration cancellationScope(RequestCancellation token) {
        RequestCancellation previous = cancellation.get();
        cancellation.set(token);
        return () -> {
            if (previous == null) cancellation.remove();
            else cancellation.set(previous);
        };
    }

    /** 取得当前请求作用域使用的取消令牌。 */
    RequestCancellation currentCancellation() {
        return cancellation.get();
    }

    /** 在阻塞操作前后检查取消或线程中断，必要时终止工作。 */
    void checkCancellation() {
        RequestCancellation token = cancellation.get();
        if (token != null) token.check();
        else if (Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Task cancelled");
    }

    /** 注册取消清理操作，返回可用于撤销注册的句柄。 */
    RequestCancellation.Registration onCancellation(Runnable listener) {
        RequestCancellation token = cancellation.get();
        return token == null ? () -> {} : token.onCancel(listener);
    }

    /** 在限定时间预算内执行工作，完成或失败后恢复原预算。 */
    public <T> T withinTimeout(long milliseconds, java.util.function.Supplier<T> work) {
        Long previous = deadline.get();
        long limit =
                System.nanoTime()
                        + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(milliseconds);
        deadline.set(previous == null ? limit : Math.min(previous, limit));
        try {
            return work.get();
        } finally {
            if (previous == null) deadline.remove();
            else deadline.set(previous);
        }
    }

    /** 根据剩余预算计算本次网络请求可使用的超时时间。 */
    private int timeout(int maximum) throws IOException, InterruptedException {
        Long limit = deadline.get();
        RequestCancellation token = cancellation.get();
        if (token != null) token.check();
        if (closed) throw new IOException("Session closed");
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException("Request cancelled");
        if (limit == null) return maximum;
        long remaining =
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(limit - System.nanoTime());
        if (remaining <= 0) throw new InterruptedException("Search deadline expired");
        return (int) Math.max(1, Math.min(maximum, remaining));
    }

    /** 准备会话所需的初始 Cookie，不必为准备工作额外访问网络。 */
    public synchronized void ensurePrimed() {
        if (primed) {
            return;
        }
        primed = true;

        try {
            HttpCookie csrf = new HttpCookie("__csrf", randomHex(32));
            csrf.setDomain(".music.163.com");
            csrf.setPath("/");
            cookieManager.getCookieStore().add(URI.create("https://music.163.com/"), csrf);
        } catch (RuntimeException ignored) {
        }
    }

    /** 通过共享 HTTP 会话读取指定地址的响应文本。 */
    public String fetch(String url, String referer) throws IOException, InterruptedException {
        return fetch(url, referer, Map.of());
    }

    /** 通过共享 HTTP 会话读取指定地址的响应文本。 */
    String fetch(String url, String referer, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.putAll(extraHeaders);
        try (DownloadResponse response =
                execute("GET", url, referer, headers, null, REQUEST_TIMEOUT_MILLIS)) {
            ensureSuccess(response, url);
            return readUtf8(response.body());
        }
    }

    /** 发送表单请求并读取响应文本，复用会话和当前取消作用域。 */
    String postForm(String url, String referer, String form, Map<String, String> headers)
            throws IOException, InterruptedException {
        Map<String, String> requestHeaders = new java.util.LinkedHashMap<>();
        requestHeaders.put("Accept", "application/json, text/plain, */*");
        requestHeaders.put("Content-Type", "application/x-www-form-urlencoded");
        requestHeaders.putAll(headers);
        byte[] body = form.getBytes(StandardCharsets.UTF_8);
        try (DownloadResponse response =
                execute("POST", url, referer, requestHeaders, body, REQUEST_TIMEOUT_MILLIS)) {
            ensureSuccess(response, url);
            return readUtf8(response.body());
        }
    }

    /** 启动或执行下载，接通传输进度、文件发布与完成处理。 */
    DownloadResponse download(String url, String referer) throws IOException, InterruptedException {
        return execute(
                "GET",
                url,
                referer,
                Map.of(
                        "Accept", "*/*",
                        "Connection", "keep-alive"),
                null,
                180_000);
    }

    /** 探测地址响应的前缀或类型，避免为判断可用性下载整个文件。 */
    DownloadResponse probe(String url, String referer, int bytes)
            throws IOException, InterruptedException {
        return execute("GET", url, referer, Map.of("Range", "bytes=0-" + (bytes - 1)), null, 8_000);
    }

    /** 提取适用于指定地址的 Cookie，遵守会话的域名隔离规则。 */
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

    /** 提取适用于指定地址的 Cookie，遵守会话的域名隔离规则。 */
    String cookieHeader(String url) {
        try {
            return String.join(
                    "; ",
                    cookieManager.get(URI.create(url), Map.of()).getOrDefault("Cookie", List.of()));
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }

    /** 返回会话统一使用的浏览器身份请求头。 */
    String userAgent() {
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }

    /** 生成对应来源协议需要的随机标识或随机参数。 */
    String randomGuid() {
        return String.format("%010d", random.nextInt(1_000_000_000));
    }

    /** 生成对应来源协议需要的随机标识或随机参数。 */
    String randomHex(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append(Integer.toHexString(random.nextInt(16)));
        }
        return result.toString();
    }

    /** 在仍允许继续的请求中短暂等待，并响应取消或中断。 */
    void snooze(long millis) {
        try {
            Thread.sleep(millis + random.nextInt(Math.max(1, (int) (millis / 2))));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** 执行 HTTP 请求，并把连接纳入当前截止时间和取消管理。 */
    private DownloadResponse execute(
            String method,
            String url,
            String referer,
            Map<String, String> headers,
            byte[] body,
            int readTimeout)
            throws IOException, InterruptedException {
        int connectTimeout = timeout(Math.min(CONNECT_TIMEOUT_MILLIS, readTimeout));
        int idleTimeout = timeout(readTimeout);
        Request.Builder request =
                new Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent())
                        .header("Referer", referer)
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .header("Accept-Encoding", "identity")
                        .header("Cache-Control", "no-cache");
        headers.forEach(request::header);
        request.method(
                method,
                body == null
                        ? null
                        : RequestBody.create(
                                body,
                                MediaType.parse(
                                        headers.getOrDefault(
                                                "Content-Type", "application/octet-stream"))));
        // Derived clients share the session pool. The absolute deadline also covers body reads.
        Call call =
                client().newBuilder()
                        .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                        .readTimeout(idleTimeout, TimeUnit.MILLISECONDS)
                        .build()
                        .newCall(request.build());
        Long limit = deadline.get();
        if (limit != null) call.timeout().deadlineNanoTime(limit);
        active.add(call);
        RequestCancellation token = cancellation.get();
        RequestCancellation.Registration detach =
                token == null ? () -> {} : token.onCancel(call::cancel);
        try {
            timeout(readTimeout);
            Response response = call.execute();
            InputStream source =
                    response.body() == null
                            ? new ByteArrayInputStream(new byte[0])
                            : response.body().byteStream();
            return new DownloadResponse(
                    response.code(),
                    response.headers().toMultimap(),
                    new ResponseInputStream(source, call, response, detach));
        } catch (IOException | RuntimeException | InterruptedException error) {
            active.remove(call);
            detach.close();
            call.cancel();
            throw error;
        }
    }

    /** 取得适用于本次请求预算的 HTTP 客户端。 */
    private synchronized OkHttpClient client() throws IOException {
        if (closed) throw new IOException("Session closed");
        if (client == null) {
            client =
                    new OkHttpClient.Builder()
                            .addNetworkInterceptor(
                                    chain -> {
                                        // Recalculate cookies for every redirect instead of
                                        // forwarding the original domain's cookies.
                                        URI uri = chain.request().url().uri();
                                        Request.Builder request =
                                                chain.request()
                                                        .newBuilder()
                                                        .removeHeader("Cookie")
                                                        .removeHeader("Cookie2");
                                        cookieManager
                                                .get(uri, Map.of())
                                                .forEach(
                                                        (name, values) ->
                                                                request.header(
                                                                        name,
                                                                        String.join("; ", values)));
                                        Response response = chain.proceed(request.build());
                                        try {
                                            cookieManager.put(uri, response.headers().toMultimap());
                                        } catch (IOException | RuntimeException error) {
                                            response.close();
                                            throw error;
                                        }
                                        return response;
                                    })
                            .build();
        }
        return client;
    }

    /** 检查接口或 HTTP 状态是否表示成功，错误响应交给调用方处理。 */
    private static void ensureSuccess(DownloadResponse response, String url) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " " + url);
        }
    }

    /** 读取并按 UTF-8 解码响应文本，关闭及取消规则由传入响应控制。 */
    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 把输入内容复制到目标；读取或写入失败按异常约定交由调用方处理。 */
    private static void copy(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buffer = new byte[16_384];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            output.write(buffer, 0, length);
        }
    }

    /** 关闭本会话或响应持有的连接资源，取消仍在读取的请求。 */
    @Override
    public void close() {
        OkHttpClient captured;
        synchronized (this) {
            closed = true;
            captured = client;
        }
        for (Call call : active) call.cancel();
        if (captured != null) {
            captured.connectionPool().evictAll();
            captured.dispatcher().executorService().shutdownNow();
        }
    }

    /** 持有 HTTP 下载响应和流。 */
    record DownloadResponse(int statusCode, Map<String, List<String>> headers, InputStream body)
            implements AutoCloseable {
        /** 读取响应头的首个值，缺失时使用空字符串。 */
        String firstHeader(String name) {
            if (headers == null || name == null) {
                return "";
            }
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null
                        && name.equalsIgnoreCase(entry.getKey())
                        && entry.getValue() != null
                        && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return "";
        }

        /** 关闭本会话或响应持有的连接资源，取消仍在读取的请求。 */
        @Override
        public void close() throws IOException {
            body.close();
        }
    }

    /** 包装下载流，在关闭时同时释放所属 HTTP 响应。 */
    private final class ResponseInputStream extends FilterInputStream {
        private final Call call;
        private final Response response;
        private final RequestCancellation.Registration detach;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        private ResponseInputStream(
                InputStream input,
                Call call,
                Response response,
                RequestCancellation.Registration detach) {
            super(input);
            this.call = call;
            this.response = response;
            this.detach = detach;
        }

        /** 关闭本会话或响应持有的连接资源，取消仍在读取的请求。 */
        @Override
        public void close() throws IOException {
            try {
                response.close();
            } finally {
                active.remove(call);
                detach.close();
            }
        }
    }
}
