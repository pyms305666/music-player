package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 使用本地 HTTP 和临时音频验证传输组件，测试不运行 curl 或访问真实网站。 */
class AudioDownloadTransportTest {
    @TempDir Path directory;

    /** 文件头检查与原来的 32768 字节下限保持一致。 */
    @Test
    void invalidHeadersAndSmallFilesAreRejected() throws Exception {
        byte[] audio = audio();
        Path file = Files.write(directory.resolve("song.part"), audio);
        assertTrue(AudioDownloadTransport.validateFile(file));
        Files.write(file, new byte[] {'R', 'I', 'F', 'F'});
        assertFalse(AudioDownloadTransport.validateFile(file));
        Files.write(file, new byte[32768]);
        assertFalse(AudioDownloadTransport.validateFile(file));
        assertFalse(AudioDownloadTransport.isAudioContent("<html>".getBytes(), 6));
    }

    /** Java 传输交付完整音频；取消作用域退出后，借用的会话仍可正常读取。 */
    @Test
    void transferReportsBytesAndCancellationLeavesBorrowedSessionUsable() throws Exception {
        byte[] payload = audio();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/audio",
                exchange -> {
                    exchange.sendResponseHeaders(200, payload.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(payload);
                    }
                });
        server.createContext(
                "/text",
                exchange -> {
                    byte[] text = "still-open".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, text.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(text);
                    }
                });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try (CrawlerSession session = new CrawlerSession()) {
            var transport = new AudioDownloadTransport(session, source -> base);
            Path target = directory.resolve("download.part");
            List<DownloadEvent> events = new ArrayList<>();
            transport.downloadViaJava(base + "/audio", target, "fixture", events::add);
            assertArrayEquals(payload, Files.readAllBytes(target));
            assertEquals(payload.length, events.get(events.size() - 1).transferredBytes());
            RequestCancellation cancelled = new RequestCancellation();
            cancelled.close();
            try (var scope = session.cancellationScope(cancelled)) {
                assertThrows(
                        java.util.concurrent.CancellationException.class,
                        () ->
                                transport.downloadViaJava(
                                        base + "/audio", target, "fixture", events::add));
            }
            assertEquals("still-open", session.fetch(base + "/text", base));
        } finally {
            server.stop(0);
        }
    }

    /** 提供超过校验下限且带 RIFF 文件头的固定音频夹具。 */
    private static byte[] audio() {
        byte[] data = new byte[32768];
        data[0] = 'R';
        data[1] = 'I';
        data[2] = 'F';
        data[3] = 'F';
        return data;
    }
}
