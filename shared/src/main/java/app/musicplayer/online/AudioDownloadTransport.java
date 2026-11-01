package app.musicplayer.online;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/** 将下载地址写入临时文件：优先尝试 curl，再使用共享 HTTP 会话。 借用爬虫的会话，不关闭它；每次下载自行关闭流并清理 curl 子进程。 */
final class AudioDownloadTransport {
    private final CrawlerSession session;
    private final java.util.function.Function<String, String> referer;
    private static Path curlPath;
    private static boolean curlChecked;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    AudioDownloadTransport(
            CrawlerSession session, java.util.function.Function<String, String> referer) {
        this.session = session;
        this.referer = referer;
    }

    /** 运行 curl 写入临时文件，持续检查取消与截止时间，结束时回收子进程。 */
    boolean downloadViaCurl(
            String url,
            Path target,
            String source,
            java.util.function.Consumer<DownloadEvent> progress)
            throws InterruptedException {
        try {
            List<String> command =
                    new ArrayList<>(
                            List.of(
                                    curlPath.toString(),
                                    "-L",
                                    "-f",
                                    "--silent",
                                    "--show-error",
                                    "-A",
                                    session.userAgent(),
                                    "-H",
                                    "Accept: */*",
                                    "-H",
                                    "Accept-Language: zh-CN,zh;q=0.9",
                                    "-H",
                                    "Referer: " + referer.apply(source),
                                    "-H",
                                    "Connection: keep-alive",
                                    "--connect-timeout",
                                    "12",
                                    "--max-time",
                                    "60",
                                    "--retry",
                                    "1",
                                    "--retry-delay",
                                    "2",
                                    "-o",
                                    target.toString()));
            String cookies = session.cookieHeader(url);
            if (!cookies.isBlank()) {
                command.add("-b");
                command.add(cookies);
            }
            command.add(url);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (var detach = session.onCancellation(process::destroyForcibly)) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(65);
                while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    session.checkCancellation();
                    progress.accept(
                            new DownloadEvent(
                                    DownloadEvent.Stage.TRANSFERRING,
                                    Files.size(target),
                                    java.util.OptionalLong.empty()));
                    if (System.nanoTime() >= deadline) return false;
                }
                progress.accept(
                        new DownloadEvent(
                                DownloadEvent.Stage.TRANSFERRING,
                                Files.size(target),
                                java.util.OptionalLong.empty()));
                return process.exitValue() == 0;
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            throw cancelled;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Exception exception) {
            System.out.println("[crawler] curl err: " + exception.getMessage());
            return false;
        }
    }

    /** 使用共享 HTTP 会话流式下载，拒绝明显的 HTML 响应并核对声明长度，自动关闭响应和文件流；音频文件头由外层校验。 */
    void downloadViaJava(
            String url,
            Path target,
            String source,
            java.util.function.Consumer<DownloadEvent> progress)
            throws IOException, InterruptedException {
        try (CrawlerSession.DownloadResponse response =
                        session.download(url, referer.apply(source));
                InputStream input = response.body();
                OutputStream output =
                        Files.newOutputStream(
                                target,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING)) {
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode());
            }
            String contentType = response.firstHeader("Content-Type").toLowerCase(Locale.ROOT);
            java.util.OptionalLong total = contentLength(response.firstHeader("Content-Length"));
            long prefixLength = 0;
            progress.accept(new DownloadEvent(DownloadEvent.Stage.TRANSFERRING, 0, total));
            if (contentType.contains("text/html")) {
                byte[] firstBytes = readPrefix(input, 512);
                String text =
                        new String(firstBytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (text.contains("<!doctype") || text.contains("<html")) {
                    throw new IOException("server returned HTML");
                }
                output.write(firstBytes);
                prefixLength = firstBytes.length;
            }
            copy(input, output, progress, total, prefixLength);
            output.flush();
            String expected = response.firstHeader("Content-Length");
            if (!expected.isBlank()) {
                try {
                    if (Files.size(target) != Long.parseLong(expected))
                        throw new IOException("下载不完整");
                } catch (NumberFormatException invalid) {
                    throw new IOException("无效文件长度", invalid);
                }
            }
        }
    }

    /** 解析声明的内容长度；缺失或非正数视为未知，非法数字报告读取错误。 */
    private static java.util.OptionalLong contentLength(String value) throws IOException {
        if (value.isBlank()) return java.util.OptionalLong.empty();
        try {
            long length = Long.parseLong(value);
            return length > 0 ? java.util.OptionalLong.of(length) : java.util.OptionalLong.empty();
        } catch (NumberFormatException invalid) {
            throw new IOException("无效文件长度", invalid);
        }
    }

    /** 检查下载文件大小及音频文件头，防止 HTML 或过小的错误响应被保存成歌曲。 */
    static boolean validateFile(Path path) {
        try {
            if (Files.size(path) < 32_768) {
                return false;
            }
            byte[] header = new byte[256];
            try (InputStream input = Files.newInputStream(path)) {
                int length = input.read(header);
                return length > 0 && isAudioContent(header, length);
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    /** 按常见音频文件头判断响应内容是否像音频，不依赖文件名后缀。 */
    public static boolean isAudioContent(byte[] data, int length) {
        if (data == null || length < 4) {
            return false;
        }
        if (data[0] == 'I' && data[1] == 'D' && data[2] == '3') return true;
        if ((data[0] & 0xff) == 0xff && (data[1] & 0xe0) == 0xe0) return true;
        if (length > 8 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p')
            return true;
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') return true;
        if (data[0] == 'f' && data[1] == 'L' && data[2] == 'a' && data[3] == 'C') return true;
        return data[0] == 'O' && data[1] == 'g' && data[2] == 'g' && data[3] == 'S';
    }

    /** 只探测一次可用 curl 命令，并缓存可执行文件位置。 */
    static synchronized boolean curlAvailable() {
        if (curlChecked) {
            return curlPath != null;
        }
        curlChecked = true;
        for (String location :
                new String[] {"C:\\Windows\\System32\\curl.exe", "curl.exe", "curl"}) {
            try {
                Process process =
                        new ProcessBuilder(location, "--version").redirectErrorStream(true).start();
                if (process.waitFor(4, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    curlPath = Paths.get(location);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** 读取不超过指定长度的流前缀，允许分段到达和提前结束。 */
    private static byte[] readPrefix(InputStream input, int maximumLength) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(maximumLength);
        byte[] buffer = new byte[Math.min(512, maximumLength)];
        while (output.size() < maximumLength) {
            int length =
                    input.read(buffer, 0, Math.min(buffer.length, maximumLength - output.size()));
            if (length < 0) {
                break;
            }
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    /** 按缓冲块复制响应，每轮检查取消并报告累计字节数；total 为空表示未知总大小。 */
    private static void copy(
            InputStream input,
            OutputStream output,
            java.util.function.Consumer<DownloadEvent> progress,
            java.util.OptionalLong total,
            long transferred)
            throws IOException {
        byte[] buffer = new byte[16_384];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            if (Thread.currentThread().isInterrupted())
                throw new java.io.InterruptedIOException("下载已取消");
            output.write(buffer, 0, length);
            transferred += length;
            progress.accept(
                    new DownloadEvent(DownloadEvent.Stage.TRANSFERRING, transferred, total));
        }
    }
}
