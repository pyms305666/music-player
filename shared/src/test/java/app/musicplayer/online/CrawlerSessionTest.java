package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * CrawlerSession 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：共享 HTTP 会话与取消作用域。复用 OkHttp 连接和
 * Cookie，支持线程局部截止时间、请求取消、文本读取及流式下载。
 */
class CrawlerSessionTest {
    /** 管理测试专用本地 HTTP 服务，提供可控响应并在测试结束时关闭。 */
    private static final class LocalServer implements AutoCloseable {
        final java.util.concurrent.ExecutorService handlers =
                java.util.concurrent.Executors.newCachedThreadPool();
        final com.sun.net.httpserver.HttpServer server;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        LocalServer() throws java.io.IOException {
            server =
                    com.sun.net.httpserver.HttpServer.create(
                            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(handlers);
        }

        /** 返回本测试 HTTP 服务的基础地址。 */
        String base() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /** 为本地 HTTP 夹具提供快速或受阻塞的响应。 */
        void fast() {
            server.createContext(
                    "/fast",
                    exchange -> {
                        try {
                            exchange.sendResponseHeaders(200, 2);
                            exchange.getResponseBody().write(new byte[] {'o', 'k'});
                        } finally {
                            exchange.close();
                        }
                    });
        }

        /** 为本地 HTTP 夹具提供快速或受阻塞的响应。 */
        void blocked(
                boolean headers,
                java.util.concurrent.CountDownLatch entered,
                java.util.concurrent.CountDownLatch release) {
            server.createContext(
                    "/slow",
                    exchange -> {
                        try {
                            if (!headers) {
                                exchange.sendResponseHeaders(200, 4096);
                                exchange.getResponseBody().write(new byte[128]);
                                exchange.getResponseBody().flush();
                            }
                            entered.countDown();
                            release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (InterruptedException stopped) {
                            Thread.currentThread().interrupt();
                        } finally {
                            exchange.close();
                        }
                    });
        }

        /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
        @Override
        public void close() {
            server.stop(0);
            handlers.shutdownNow();
        }
    }

    /** 验证响应头之前的取消无需等待服务器返回。 */
    @Test
    void cancellingBeforeResponseHeadersReturnsWithoutWaitingForTheServer() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var worker = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (var server = new LocalServer();
                var session = new CrawlerSession()) {
            server.blocked(true, entered, release);
            server.fast();
            server.server.start();
            var token = new RequestCancellation();
            var request =
                    worker.submit(
                            () -> {
                                try (var scope = session.cancellationScope(token)) {
                                    return session.fetch(server.base() + "/slow", server.base());
                                }
                            });
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
            worker.submit(token::close).get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> request.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("ok", session.fetch(server.base() + "/fast", server.base()));
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    /** 验证会话关闭取消阻塞读取，并拒绝新请求。 */
    @Test
    void sessionCloseCancelsABlockedResponseBodyAndRejectsNewRequests() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var worker = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (var server = new LocalServer();
                var session = new CrawlerSession()) {
            server.blocked(false, entered, release);
            server.fast();
            server.server.start();
            var request =
                    worker.submit(() -> session.fetch(server.base() + "/slow", server.base()));
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
            worker.submit(session::close).get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> request.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(
                    java.io.IOException.class,
                    () -> session.fetch(server.base() + "/fast", server.base()));
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    /** 验证截止时间中断响应体读取，并且不影响下次请求。 */
    @Test
    void absoluteDeadlineInterruptsBodyReadAndDoesNotAffectTheNextRequest() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var server = new LocalServer();
                var session = new CrawlerSession()) {
            server.blocked(false, entered, release);
            server.fast();
            server.server.start();
            var request =
                    worker.submit(
                            () -> {
                                session.setDeadline(
                                        System.nanoTime()
                                                + java.util.concurrent.TimeUnit.SECONDS.toNanos(1));
                                try {
                                    return session.fetch(server.base() + "/slow", server.base());
                                } finally {
                                    session.clearDeadline();
                                }
                            });
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
            var failed =
                    assertThrows(
                            java.util.concurrent.ExecutionException.class,
                            () -> request.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(java.io.IOException.class, failed.getCause());
            assertEquals(
                    "ok",
                    worker.submit(() -> session.fetch(server.base() + "/fast", server.base()))
                            .get(3, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    /** 验证已取消的作用域不再发出 HTTP 请求。 */
    @Test
    void alreadyCancelledScopeMakesNoHttpRequest() throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        try (var server = new LocalServer();
                var session = new CrawlerSession()) {
            server.server.createContext(
                    "/count",
                    exchange -> {
                        requests.incrementAndGet();
                        exchange.sendResponseHeaders(200, -1);
                        exchange.close();
                    });
            server.server.start();
            var token = new RequestCancellation();
            token.close();
            try (var scope = session.cancellationScope(token)) {
                assertThrows(
                        java.util.concurrent.CancellationException.class,
                        () -> session.fetch(server.base() + "/count", server.base()));
            }
            assertEquals(0, requests.get());
        }
    }

    /** 验证 POST、请求头、Cookie 与重定向的 HTTP 约定。 */
    @Test
    void postHeadersCookiesAndRedirectsKeepTheirHttpContracts() throws Exception {
        var method = new java.util.concurrent.atomic.AtomicReference<String>();
        var form = new java.util.concurrent.atomic.AtomicReference<String>();
        var postHeaders =
                new java.util.concurrent.atomic.AtomicReference<com.sun.net.httpserver.Headers>();
        var redirectCookie = new java.util.concurrent.atomic.AtomicReference<String>();
        var foreignCookie = new java.util.concurrent.atomic.AtomicReference<String>();
        try (var server = new LocalServer();
                var session = new CrawlerSession()) {
            server.server.createContext(
                    "/seed",
                    exchange -> {
                        exchange.getResponseHeaders().add("Set-Cookie", "session=owned; Path=/");
                        exchange.sendResponseHeaders(200, -1);
                        exchange.close();
                    });
            server.server.createContext(
                    "/post",
                    exchange -> {
                        try {
                            method.set(exchange.getRequestMethod());
                            postHeaders.set(exchange.getRequestHeaders());
                            form.set(
                                    new String(
                                            exchange.getRequestBody().readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8));
                            exchange.sendResponseHeaders(200, 2);
                            exchange.getResponseBody().write(new byte[] {'o', 'k'});
                        } finally {
                            exchange.close();
                        }
                    });
            server.server.createContext(
                    "/redirect",
                    exchange -> {
                        exchange.getResponseHeaders().add("Set-Cookie", "hop=seen; Path=/");
                        exchange.getResponseHeaders().add("Location", "/end");
                        exchange.sendResponseHeaders(302, -1);
                        exchange.close();
                    });
            server.server.createContext(
                    "/end",
                    exchange -> {
                        redirectCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
                        exchange.getResponseHeaders()
                                .add(
                                        "Location",
                                        "http://localhost:"
                                                + server.server.getAddress().getPort()
                                                + "/foreign");
                        exchange.sendResponseHeaders(302, -1);
                        exchange.close();
                    });
            server.server.createContext(
                    "/foreign",
                    exchange -> {
                        try {
                            foreignCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
                            exchange.sendResponseHeaders(200, 2);
                            exchange.getResponseBody().write(new byte[] {'o', 'k'});
                        } finally {
                            exchange.close();
                        }
                    });
            server.server.start();
            session.fetch(server.base() + "/seed", server.base());
            assertEquals(
                    "ok",
                    session.postForm(
                            server.base() + "/post",
                            server.base(),
                            "name=%E5%A4%9C%E6%9B%B2&n=1",
                            java.util.Map.of("X-Fixture", "post")));
            assertEquals("POST", method.get());
            assertEquals("name=%E5%A4%9C%E6%9B%B2&n=1", form.get());
            assertEquals(server.base(), postHeaders.get().getFirst("Referer"));
            assertEquals("identity", postHeaders.get().getFirst("Accept-Encoding"));
            assertEquals("post", postHeaders.get().getFirst("X-Fixture"));
            assertEquals(
                    "application/x-www-form-urlencoded",
                    postHeaders.get().getFirst("Content-Type"));
            assertNotNull(postHeaders.get().getFirst("User-Agent"));
            assertTrue(postHeaders.get().getFirst("Cookie").contains("session=owned"));
            assertEquals("ok", session.fetch(server.base() + "/redirect", server.base()));
            assertTrue(redirectCookie.get().contains("hop=seen"));
            assertTrue(redirectCookie.get().contains("session=owned"));
            assertTrue(
                    foreignCookie.get() == null || foreignCookie.get().isEmpty(),
                    "Cookies must not cross from 127.0.0.1 to localhost");
            assertTrue(session.cookieHeader(server.base()).contains("hop=seen"));
        }
    }

    /** 验证单次作用域取消断开响应体，同时会话仍可继续使用。 */
    @Test
    void cancellingOneScopedRequestDisconnectsItsBodyAndLeavesSessionUsable() throws Exception {
        var blocked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var handlers = java.util.concurrent.Executors.newCachedThreadPool();
        var worker = java.util.concurrent.Executors.newFixedThreadPool(2);
        var server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(handlers);
        server.createContext(
                "/slow",
                exchange -> {
                    try {
                        exchange.sendResponseHeaders(200, 4096);
                        exchange.getResponseBody().write(new byte[128]);
                        exchange.getResponseBody().flush();
                        blocked.countDown();
                        release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException cancelled) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.createContext(
                "/fast",
                exchange -> {
                    try {
                        exchange.sendResponseHeaders(200, 2);
                        exchange.getResponseBody().write(new byte[] {'o', 'k'});
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        try (var session = new CrawlerSession()) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var token = new RequestCancellation();
            var request =
                    worker.submit(
                            () -> {
                                try (var scope = session.cancellationScope(token)) {
                                    return session.fetch(base + "/slow", base);
                                }
                            });
            assertTrue(blocked.await(3, java.util.concurrent.TimeUnit.SECONDS));
            worker.submit(token::close).get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> request.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("ok", session.fetch(base + "/fast", base));
        } finally {
            release.countDown();
            server.stop(0);
            handlers.shutdownNow();
            worker.shutdownNow();
        }
    }

    /** 验证初始 Cookie 仅用于自身域名，准备会话无需联网。 */
    @Test
    void seedCookiesStayOnTheirOwnDomainAndPrimingNeedsNoNetwork() {
        try (var session = new CrawlerSession()) {
            session.ensurePrimed();
            assertTrue(session.cookieHeader("https://music.163.com/api/search").contains("__csrf"));
            assertFalse(session.cookieHeader("https://www.kugou.com/").contains("__csrf"));
            assertEquals("", session.cookieHeader("https://example.com/"));
        }
    }

    /** 验证已到期预算阻止新的网络请求。 */
    @Test
    void expiredDeadlinePreventsNetworkRequest() {
        try (var session = new CrawlerSession()) {
            session.setDeadline(System.nanoTime() - 1);
            assertThrows(
                    InterruptedException.class,
                    () -> session.fetch("http://127.0.0.1:1/", "http://localhost/"));
        }
    }

    /** 验证请求作用域失败后恢复原来的时间预算。 */
    @Test
    void scopedDeadlineRestoresPreviousBudgetOnFailure() {
        try (var session = new CrawlerSession()) {
            session.setDeadline(System.nanoTime() - 1);
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            session.withinTimeout(
                                    1000,
                                    () -> {
                                        throw new IllegalStateException("fixture");
                                    }));
            assertThrows(
                    InterruptedException.class,
                    () -> session.fetch("http://127.0.0.1:1/", "http://localhost/"));
            session.clearDeadline();
            assertThrows(
                    java.io.IOException.class,
                    () -> session.fetch("http://127.0.0.1:1/", "http://localhost/"));
        }
    }
}
