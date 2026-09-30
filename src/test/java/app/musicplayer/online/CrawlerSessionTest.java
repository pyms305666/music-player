package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrawlerSessionTest {
    @Test void seedCookiesStayOnTheirOwnDomainAndPrimingNeedsNoNetwork() {
        try (var session = new CrawlerSession()) {
            session.ensurePrimed();
            assertTrue(session.cookieHeader("https://music.163.com/api/search").contains("__csrf"));
            assertFalse(session.cookieHeader("https://www.kugou.com/").contains("__csrf"));
            assertEquals("", session.cookieHeader("https://example.com/"));
        }
    }

    @Test void expiredDeadlinePreventsNetworkRequest() {
        try (var session = new CrawlerSession()) {
            session.setDeadline(System.nanoTime() - 1);
            assertThrows(InterruptedException.class, () -> session.fetch("http://127.0.0.1:1/", "http://localhost/"));
        }
    }

    @Test void scopedDeadlineRestoresPreviousBudgetOnFailure() {
        try (var session = new CrawlerSession()) {
            session.setDeadline(System.nanoTime() - 1);
            assertThrows(IllegalStateException.class, () -> session.withinTimeout(1000, () -> { throw new IllegalStateException("fixture"); }));
            assertThrows(InterruptedException.class, () -> session.fetch("http://127.0.0.1:1/", "http://localhost/"));
            session.clearDeadline();
            assertThrows(java.io.IOException.class, () -> session.fetch("http://127.0.0.1:1/", "http://localhost/"));
        }
    }
}
