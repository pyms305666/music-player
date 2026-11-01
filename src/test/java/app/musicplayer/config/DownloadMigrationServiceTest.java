package app.musicplayer.config;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.data.MusicDatabase;
import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.model.Track;
import app.musicplayer.playlist.NamedPlaylist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

/** 使用真实文件和 SQLite 验证迁移、碰撞、取消、事务回滚及两阶段启动恢复。 */
class DownloadMigrationServiceTest {
    @TempDir Path root;

    /** 重启后完整迁移歌曲及 LRC，并保留外部歌曲、缓存、歌单状态和顺序。 */
    @Test
    void migratesFilesLyricsAndAllPlaylistReferences() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path audio = fixture.audio("专辑/中文歌曲.wav", 1);
            Path lyrics = Files.writeString(audio.resolveSibling("中文歌曲.lrc"), "[00:00.00]旁车歌词");
            Path unregistered = fixture.audio("已移出曲库.wav", 2);
            Path external = writeAudio(root.resolve("外部音频.wav"), 3);
            Path cache = fixture.audio("cache/playback/缓存.wav", 4);
            Path backup = fixture.audio("backups/保留.wav", 5);
            Track original = new Track(audio);
            original.updateMetadata("原始标题", "原始歌手");
            fixture.database.saveTracks(List.of(original, new Track(external)));
            fixture.database.saveTrack(original, java.time.Duration.ofSeconds(42));
            fixture.database.saveLyrics(original, LrcParser.parse("本地缓存", "[00:00.00]缓存歌词"));
            var entry = entry("first", audio, NamedPlaylist.State.FAILED, "保留状态");
            fixture.database.savePlaylist(
                    playlist(
                            "p",
                            fixture.source.resolve("专辑"),
                            List.of(
                                    entry,
                                    entry("duplicate", audio, NamedPlaylist.State.READY, ""))));
            fixture.database.savePlaylist(
                    playlist(
                            "external",
                            root.resolve("外部目录"),
                            List.of(
                                    entry(
                                            "external-item",
                                            external,
                                            NamedPlaylist.State.READY,
                                            ""))));
            var preview = fixture.service.preview(fixture.source, fixture.target);
            assertEquals(2, preview.songs());
            assertTrue(preview.paths().contains(lyrics.toString()));
            fixture.service.schedule(fixture.source, fixture.target);
            fixture.reload();
            var result = fixture.execute(new AtomicBoolean());
            assertTrue(result.committed());
            assertTrue(result.remaining().isEmpty());
            Path moved = fixture.target.resolve("专辑/中文歌曲.wav");
            assertTrue(Files.exists(moved));
            assertTrue(Files.exists(moved.resolveSibling("中文歌曲.lrc")));
            assertFalse(Files.exists(audio));
            assertFalse(Files.exists(lyrics));
            assertFalse(Files.exists(unregistered));
            assertTrue(Files.exists(external));
            assertTrue(Files.exists(cache));
            assertTrue(Files.exists(backup));
            assertEquals(fixture.target.toRealPath(), fixture.settings.configured().orElseThrow());
            Track saved =
                    fixture.database.loadTracks().stream()
                            .filter(track -> track.path().equals(moved))
                            .findFirst()
                            .orElseThrow();
            assertEquals("原始标题", saved.title());
            assertTrue(fixture.database.loadLyrics(saved).isPresent());
            var playlists = fixture.database.loadPlaylists();
            var migrated =
                    playlists.stream().filter(p -> p.id().equals("p")).findFirst().orElseThrow();
            assertEquals(fixture.target.resolve("专辑").toString(), migrated.directory());
            assertEquals(
                    List.of("first", "duplicate"),
                    migrated.entries().stream().map(NamedPlaylist.Entry::id).toList());
            assertEquals(NamedPlaylist.State.FAILED, migrated.entries().getFirst().state());
            assertEquals("保留状态", migrated.entries().getFirst().message());
            assertEquals(moved.toString(), migrated.entries().getFirst().location());
            assertEquals(
                    root.resolve("外部目录").toString(),
                    playlists.stream()
                            .filter(p -> p.id().equals("external"))
                            .findFirst()
                            .orElseThrow()
                            .directory());
            assertTrue(fixture.database.pendingDownloadMigration().isEmpty());
        }
    }

    /** 不同内容同名时音频与歌词一起另名，既有目标内容保持原样。 */
    @Test
    void conflictingAudioAndSidecarAreRenamedTogether() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("song.wav", 1);
            Files.writeString(fixture.source.resolve("song.lrc"), "old lyrics");
            Path existing = writeAudio(fixture.target.resolve("song.wav"), 2);
            byte[] previous = Files.readAllBytes(existing);
            Files.writeString(fixture.target.resolve("song.lrc"), "existing lyrics");
            fixture.database.saveTracks(List.of(new Track(old), new Track(existing)));
            fixture.service.schedule(fixture.source, fixture.target);
            fixture.execute(new AtomicBoolean());
            assertArrayEquals(previous, Files.readAllBytes(existing));
            assertEquals("existing lyrics", Files.readString(fixture.target.resolve("song.lrc")));
            assertTrue(Files.exists(fixture.target.resolve("song (2).wav")));
            assertEquals("old lyrics", Files.readString(fixture.target.resolve("song (2).lrc")));
            assertEquals(2, fixture.database.loadTracks().size());
        }
    }

    /** 同内容目标复用时合并数据库主键，已有目标歌词不被级联删除。 */
    @Test
    void identicalTargetPreservesExistingMetadataAndLyrics() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("song.wav", 1), target = fixture.target.resolve("song.wav");
            Files.copy(old, target);
            Track original = new Track(old), existing = new Track(target);
            original.updateMetadata("源标题", "源歌手");
            existing.updateMetadata("目标标题", "目标歌手");
            fixture.database.saveTracks(List.of(original, existing));
            fixture.database.saveLyrics(
                    original, LrcParser.parse("源", "[00:00.00]源歌词"), "source-cover");
            fixture.database.saveLyrics(existing, LrcParser.parse("目标", "[00:00.00]目标歌词"));
            fixture.service.schedule(fixture.source, fixture.target);
            fixture.execute(new AtomicBoolean());
            assertEquals(1, fixture.database.loadTracks().size());
            assertEquals("目标标题", fixture.database.loadTracks().getFirst().title());
            assertTrue(
                    fixture.database.loadLyrics(existing).orElseThrow().rawText().contains("目标歌词"));
            assertFalse(Files.exists(old));
            assertFalse(Files.exists(fixture.target.resolve("song (2).wav")));
        }
    }

    /** 提交前取消撤销本次目标副本，源音频、歌词与默认设置仍然有效。 */
    @Test
    void cancellingBeforeCommitKeepsOldLibrary() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("song.wav", 1);
            fixture.database.saveTracks(List.of(new Track(old)));
            fixture.settings.save(fixture.source);
            fixture.service.schedule(fixture.source, fixture.target);
            AtomicBoolean cancel = new AtomicBoolean();
            var result =
                    fixture.service.execute(
                            fixture.database.pendingDownloadMigration().orElseThrow(),
                            cancel::get,
                            progress -> {
                                if (progress.phase().equals("复制并校验")) cancel.set(true);
                            });
            assertTrue(result.cancelled());
            assertFalse(result.committed());
            assertTrue(Files.exists(old));
            assertFalse(Files.exists(fixture.target.resolve("song.wav")));
            assertEquals(old, fixture.database.loadTracks().getFirst().path());
            assertEquals(fixture.source, fixture.settings.configured().orElseThrow());
            assertTrue(fixture.database.pendingDownloadMigration().isEmpty());
        }
    }

    /** 提交后取消只停止旧文件清理，重启不会再次复制或重写已提交路径。 */
    @Test
    void resumesCommittedCleanupAfterRestart() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("song.wav", 1);
            fixture.database.saveTracks(List.of(new Track(old)));
            fixture.service.schedule(fixture.source, fixture.target);
            AtomicBoolean cancel = new AtomicBoolean();
            var first =
                    fixture.service.execute(
                            fixture.database.pendingDownloadMigration().orElseThrow(),
                            cancel::get,
                            progress -> {
                                if (progress.phase().equals("保存新下载位置")) cancel.set(true);
                            });
            assertTrue(first.committed());
            assertTrue(Files.exists(old));
            fixture.reload();
            List<String> phases = new java.util.ArrayList<>();
            var second =
                    fixture.service.execute(
                            fixture.database.pendingDownloadMigration().orElseThrow(),
                            () -> false,
                            progress -> phases.add(progress.phase()));
            assertTrue(second.committed());
            assertFalse(phases.contains("复制并校验"));
            assertFalse(Files.exists(old));
            assertEquals(1, fixture.database.loadTracks().size());
        }
    }

    /** 数据库已提交而偏好存储不可用时保留源文件，下次启动从持久化标记恢复。 */
    @Test
    void preferenceFailureAfterCommitRecoversWithoutSourceLoss() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("song.wav", 1);
            fixture.database.saveTracks(List.of(new Track(old)));
            fixture.service.schedule(fixture.source, fixture.target);
            assertThrows(
                    java.io.IOException.class,
                    () ->
                            fixture.service.execute(
                                    fixture.database.pendingDownloadMigration().orElseThrow(),
                                    () -> false,
                                    progress -> {
                                        if (progress.phase().equals("保存新下载位置"))
                                            try {
                                                fixture.preferences.removeNode();
                                            } catch (Exception failure) {
                                                throw new IllegalStateException(failure);
                                            }
                                    }));
            assertTrue(Files.exists(old));
            assertTrue(Files.exists(fixture.target.resolve("song.wav")));
            assertEquals(
                    "COMMITTED",
                    fixture.database.pendingDownloadMigration().orElseThrow().status());
            fixture.preferences = Preferences.userRoot().node(fixture.preferenceNode);
            fixture.reload();
            fixture.execute(new AtomicBoolean());
            assertFalse(Files.exists(old));
            assertEquals(fixture.target, fixture.settings.configured().orElseThrow());
        }
    }

    /** 歌单 JSON 损坏引起整个路径事务回滚；修复数据后可继续原任务。 */
    @Test
    void invalidPlaylistRollsBackEveryPathAndCanRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("song.wav", 1);
            Track track = new Track(old);
            fixture.database.saveTracks(List.of(track));
            fixture.database.saveLyrics(track, LrcParser.parse("cache", "[00:00.00]歌词"));
            var playlist =
                    playlist(
                            "p",
                            fixture.source,
                            List.of(entry("i", old, NamedPlaylist.State.READY, "")));
            fixture.database.savePlaylist(playlist);
            try (var connection =
                            DriverManager.getConnection("jdbc:sqlite:" + fixture.databasePath);
                    var statement = connection.createStatement()) {
                statement.executeUpdate("update named_playlists set metadata='invalid json'");
            }
            fixture.service.schedule(fixture.source, fixture.target);
            assertThrows(java.io.IOException.class, () -> fixture.execute(new AtomicBoolean()));
            assertEquals(old, fixture.database.loadTracks().getFirst().path());
            assertTrue(fixture.database.loadLyrics(track).isPresent());
            assertTrue(Files.exists(old));
            assertFalse(Files.exists(fixture.target.resolve("song.wav")));
            fixture.database.savePlaylist(playlist);
            fixture.execute(new AtomicBoolean());
            assertFalse(Files.exists(old));
            assertEquals(
                    fixture.target.resolve("song.wav"),
                    fixture.database.loadTracks().getFirst().path());
        }
    }

    /** 清源前外部程序修改旧文件时保留该文件，并允许用户明确结束清理。 */
    @Test
    void modifiedSourceIsRetainedAfterCommit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("song.wav", 1);
            fixture.database.saveTracks(List.of(new Track(old)));
            fixture.service.schedule(fixture.source, fixture.target);
            var result =
                    fixture.service.execute(
                            fixture.database.pendingDownloadMigration().orElseThrow(),
                            () -> false,
                            progress -> {
                                if (progress.phase().equals("清理旧文件"))
                                    try {
                                        Files.writeString(old, "user edit");
                                    } catch (Exception failure) {
                                        throw new IllegalStateException(failure);
                                    }
                            });
            assertTrue(result.committed());
            assertFalse(result.remaining().isEmpty());
            assertEquals("user edit", Files.readString(old));
            fixture.service.dismiss(fixture.database.pendingDownloadMigration().orElseThrow());
            assertTrue(fixture.database.pendingDownloadMigration().isEmpty());
            assertTrue(Files.exists(old));
        }
    }

    /** 音频内容相同但旁车冲突时仍成组另名，防止加载错误版本的歌词。 */
    @Test
    void identicalAudioWithDifferentSidecarIsNotMerged() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("song.wav", 1);
            Files.copy(old, fixture.target.resolve("song.wav"));
            Files.writeString(fixture.source.resolve("song.lrc"), "source");
            Files.writeString(fixture.target.resolve("song.lrc"), "target");
            fixture.service.schedule(fixture.source, fixture.target);
            fixture.execute(new AtomicBoolean());
            assertTrue(Files.exists(fixture.target.resolve("song (2).wav")));
            assertEquals("source", Files.readString(fixture.target.resolve("song (2).lrc")));
            assertEquals("target", Files.readString(fixture.target.resolve("song.lrc")));
        }
    }

    /** 目录相同或互相包含时拒绝计划，避免递归复制及清理歧义。 */
    @Test
    void overlappingRootsDoNotCreateMigration() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertThrows(
                    java.io.IOException.class,
                    () -> fixture.service.schedule(fixture.source, fixture.source));
            assertThrows(
                    java.io.IOException.class,
                    () ->
                            fixture.service.schedule(
                                    fixture.source, fixture.source.resolve("child")));
            assertTrue(fixture.database.pendingDownloadMigration().isEmpty());
        }
    }

    /** Windows junction 不会把外部目录歌曲带进旧根的迁移清单。 */
    @Test
    void directoryLinksDoNotExpandMigrationScope() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.audio("own.wav", 1);
            Path outside = Files.createDirectories(root.resolve("outside"));
            writeAudio(outside.resolve("external.wav"), 2);
            Path link = fixture.source.resolve("linked");
            if (System.getProperty("os.name").startsWith("Windows")) {
                var process =
                        new ProcessBuilder(
                                        "cmd.exe",
                                        "/c",
                                        "mklink",
                                        "/J",
                                        link.toString(),
                                        outside.toString())
                                .redirectErrorStream(true)
                                .start();
                process.getInputStream().readAllBytes();
                assertEquals(0, process.waitFor(), "test junction creation failed");
            } else Files.createSymbolicLink(link, outside);
            try {
                Path linkedAudio = link.resolve("external.wav");
                fixture.database.savePlaylist(
                        playlist(
                                "linked",
                                link,
                                List.of(
                                        entry(
                                                "external",
                                                linkedAudio,
                                                NamedPlaylist.State.READY,
                                                ""))));
                var preview = fixture.service.preview(fixture.source, fixture.target);
                assertEquals(1, preview.songs());
                assertEquals(List.of(linkedAudio.toString()), preview.skipped());
                fixture.service.schedule(fixture.source, fixture.target);
                fixture.execute(new AtomicBoolean());
                assertTrue(Files.exists(outside.resolve("external.wav")));
                assertTrue(Files.exists(link));
                assertEquals(
                        link.toString(), fixture.database.loadPlaylists().getFirst().directory());
                assertEquals(
                        linkedAudio.toString(),
                        fixture.database
                                .loadPlaylists()
                                .getFirst()
                                .entries()
                                .getFirst()
                                .location());
            } finally {
                Files.delete(link);
            }
        }
    }

    /** 缺失歌曲明确出现在确认提示中，曲库与歌单保留原缺失路径。 */
    @Test
    void missingRegisteredFilesAreListedAndNotRewritten() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path missing = fixture.source.resolve("缺失.wav");
            fixture.database.saveTracks(List.of(new Track(missing)));
            fixture.database.savePlaylist(
                    playlist(
                            "missing",
                            fixture.source,
                            List.of(entry("missing", missing, NamedPlaylist.State.MISSING, "缺失"))));
            var preview = fixture.service.preview(fixture.source, fixture.target);
            assertEquals(0, preview.songs());
            assertEquals(List.of(missing.toString()), preview.skipped());
            fixture.service.schedule(fixture.source, fixture.target);
            fixture.execute(new AtomicBoolean());
            assertEquals(missing, fixture.database.loadTracks().getFirst().path());
            assertEquals(
                    missing.toString(),
                    fixture.database.loadPlaylists().getFirst().entries().getFirst().location());
        }
    }

    /** 数据库索引拒绝并发安排第二份有效计划，而不是仅依赖界面布尔状态。 */
    @Test
    void onlyOnePendingPlanCanBeStored() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.service.schedule(fixture.source, fixture.target);
            try (var other = new MusicDatabase(fixture.databasePath)) {
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                other.saveDownloadMigration(
                                        UUID.randomUUID().toString(), "PLANNED", "{}"));
            }
            assertThrows(
                    java.io.IOException.class,
                    () -> fixture.service.schedule(fixture.source, fixture.target));
        }
    }

    /** 不识别实例锁的旧进程也不能在迁移独占期写入 SQLite。 */
    @Test
    void legacyDatabaseWriterIsBlockedDuringMigration() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.audio("song.wav", 3);
            fixture.service.schedule(fixture.source, fixture.target);
            try (var oldConnection =
                            DriverManager.getConnection("jdbc:sqlite:" + fixture.databasePath);
                    var query = oldConnection.createStatement()) {
                query.execute("pragma busy_timeout=50");
                AtomicBoolean checked = new AtomicBoolean();
                fixture.service.execute(
                        fixture.database.pendingDownloadMigration().orElseThrow(),
                        () -> false,
                        progress -> {
                            if (progress.phase().equals("正在复制")
                                    && checked.compareAndSet(false, true)) {
                                assertThrows(
                                        java.sql.SQLException.class,
                                        () ->
                                                query.executeUpdate(
                                                        "update download_migrations set"
                                                            + " status=status"));
                            }
                        });
                assertTrue(checked.get());
                assertDoesNotThrow(
                        () -> query.executeUpdate("update download_migrations set status=status"));
            }
        }
    }

    /** 子进程在复制发布后直接终止，重新打开 SQLite 后继续完成原任务。 */
    @Test
    void processCrashBeforeCommitResumesFromJournal() throws Exception {
        assertCrashRecovery("复制并校验", "COPYING");
    }

    /** 子进程在数据库提交后、设置保存前终止，恢复不回滚已提交的新路径。 */
    @Test
    void processCrashAfterCommitRestoresSettingsAndCleanup() throws Exception {
        assertCrashRecovery("保存新下载位置", "COMMITTED");
    }

    /** 真实进程中断只发生在本用例数据库与偏好节点，父进程校验磁盘状态。 */
    private void assertCrashRecovery(String phase, String expectedState) throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("crash.wav", 6);
            fixture.database.saveTracks(List.of(new Track(old)));
            fixture.settings.save(fixture.source);
            fixture.service.schedule(fixture.source, fixture.target);
            fixture.database.close();
            fixture.database = null;
            var classes =
                    List.of(
                            DownloadMigrationCrashProbe.class,
                            MusicDatabase.class,
                            Track.class,
                            com.google.gson.Gson.class,
                            org.sqlite.JDBC.class);
            String classPath =
                    classes.stream()
                            .map(
                                    type -> {
                                        try {
                                            return Path.of(
                                                            type.getProtectionDomain()
                                                                    .getCodeSource()
                                                                    .getLocation()
                                                                    .toURI())
                                                    .toString();
                                        } catch (Exception failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .distinct()
                            .collect(
                                    java.util.stream.Collectors.joining(
                                            java.io.File.pathSeparator));
            var process =
                    new ProcessBuilder(
                                    Path.of(
                                                    System.getProperty("java.home"),
                                                    "bin",
                                                    System.getProperty("os.name")
                                                                    .startsWith("Windows")
                                                            ? "java.exe"
                                                            : "java")
                                            .toString(),
                                    "--enable-native-access=ALL-UNNAMED",
                                    "-cp",
                                    classPath,
                                    DownloadMigrationCrashProbe.class.getName(),
                                    fixture.data.toString(),
                                    fixture.source.toString(),
                                    fixture.preferenceNode,
                                    phase)
                            .redirectErrorStream(true)
                            .start();
            try {
                assertTrue(
                        process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS),
                        "fixture process did not finish");
                String output =
                        new String(
                                process.getInputStream().readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8);
                assertEquals(91, process.exitValue(), output);
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
            fixture.reload();
            assertEquals(
                    expectedState,
                    fixture.database.pendingDownloadMigration().orElseThrow().status());
            assertTrue(Files.exists(old));
            assertTrue(Files.exists(fixture.target.resolve("crash.wav")));
            assertEquals(
                    expectedState.equals("COPYING") ? old : fixture.target.resolve("crash.wav"),
                    fixture.database.loadTracks().getFirst().path());
            fixture.execute(new AtomicBoolean());
            assertFalse(Files.exists(old));
            assertEquals(fixture.target, fixture.settings.configured().orElseThrow());
            assertTrue(fixture.database.pendingDownloadMigration().isEmpty());
            assertEquals(1, fixture.database.loadTracks().size());
        }
    }

    /** 真实目标子目录变为外部链接后拒绝复制，不写入或清理确认范围以外的文件。 */
    @Test
    void targetDirectoryLinkIsRejectedBeforePublishing() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.audio("album/song.wav", 1);
            Path outside = Files.createDirectories(root.resolve("outside")),
                    link = fixture.target.resolve("album");
            if (System.getProperty("os.name").startsWith("Windows")) {
                var process =
                        new ProcessBuilder(
                                        "cmd.exe",
                                        "/c",
                                        "mklink",
                                        "/J",
                                        link.toString(),
                                        outside.toString())
                                .redirectErrorStream(true)
                                .start();
                process.getInputStream().readAllBytes();
                assertEquals(0, process.waitFor());
            } else Files.createSymbolicLink(link, outside);
            try {
                fixture.service.schedule(fixture.source, fixture.target);
                assertThrows(java.io.IOException.class, () -> fixture.execute(new AtomicBoolean()));
                assertFalse(Files.exists(outside.resolve("song.wav")));
                assertTrue(Files.exists(fixture.source.resolve("album/song.wav")));
            } finally {
                Files.delete(link);
            }
        }
    }

    /** 临时目录与项目输出位于不同卷时，实际跨卷复制后仍保留内容和曲库关联。 */
    @Test
    void copiesBetweenRealVolumesWhenAvailable() throws Exception {
        Path parent =
                Files.createDirectories(
                        Path.of("build/readability/cross-volume-tests").toAbsolutePath());
        Path selected = Files.createTempDirectory(parent, "music-").toRealPath();
        try (Fixture fixture = new Fixture()) {
            org.junit.jupiter.api.Assumptions.assumeFalse(
                    Files.getFileStore(fixture.source).equals(Files.getFileStore(selected)),
                    "two distinct file systems unavailable");
            Path old = fixture.audio("song.wav", 7);
            byte[] content = Files.readAllBytes(old);
            fixture.database.saveTracks(List.of(new Track(old)));
            fixture.service.schedule(fixture.source, selected);
            fixture.execute(new AtomicBoolean());
            assertArrayEquals(content, Files.readAllBytes(selected.resolve("song.wav")));
            assertEquals(
                    selected.resolve("song.wav"), fixture.database.loadTracks().getFirst().path());
            assertFalse(Files.exists(old));
        } finally {
            if (!selected.toRealPath().startsWith(parent.toRealPath()))
                throw new IllegalStateException("test cleanup escaped its output directory");
            try (var paths = Files.walk(selected)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    /** 提交后目标设备暂时离线时恢复新设置但保留所有源文件，用户可选择暂留旧副本。 */
    @Test
    void unavailableCommittedTargetNeverDeletesSource() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path old = fixture.audio("song.wav", 1);
            fixture.database.saveTracks(List.of(new Track(old)));
            fixture.service.schedule(fixture.source, fixture.target);
            AtomicBoolean cancel = new AtomicBoolean();
            fixture.service.execute(
                    fixture.database.pendingDownloadMigration().orElseThrow(),
                    cancel::get,
                    progress -> {
                        if (progress.phase().equals("保存新下载位置")) cancel.set(true);
                    });
            Path parked = fixture.target.resolveSibling("temporarily-offline");
            Files.move(fixture.target, parked);
            var result = fixture.execute(new AtomicBoolean());
            assertTrue(result.committed());
            assertFalse(result.remaining().isEmpty());
            assertTrue(Files.exists(old));
            assertEquals(fixture.target, fixture.settings.configured().orElseThrow());
            fixture.service.dismiss(fixture.database.pendingDownloadMigration().orElseThrow());
            assertTrue(fixture.database.pendingDownloadMigration().isEmpty());
            assertTrue(Files.exists(old));
        }
    }

    /** 构造包含可观察状态和版本选择的真实歌单条目。 */
    private static NamedPlaylist.Entry entry(
            String id, Path location, NamedPlaylist.State state, String message) {
        var track = new OnlineTrackInfo("kugou", "歌曲", "歌手", "", "", "id", "");
        return new NamedPlaylist.Entry(
                id, track, 42000, location.toString(), state, message, track, true);
    }

    /** 每份歌单保留独立目录，测试只要求实际映射范围发生变化。 */
    private static NamedPlaylist playlist(
            String id, Path directory, List<NamedPlaylist.Entry> entries) {
        return new NamedPlaylist(
                id,
                "测试歌单",
                "test",
                "",
                "",
                "",
                "",
                entries.size(),
                directory.toString(),
                true,
                entries);
    }

    /** 生成小型有效 PCM WAV，内容种子用于区别同名文件。 */
    private static Path writeAudio(Path path, int seed) throws Exception {
        Files.createDirectories(path.getParent());
        ByteBuffer buffer = ByteBuffer.allocate(44 + 512).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .putInt(548)
                .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(16)
                .putShort((short) 1)
                .putShort((short) 1)
                .putInt(8000)
                .putInt(16000)
                .putShort((short) 2)
                .putShort((short) 16);
        buffer.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(512);
        for (int index = 0; index < 256; index++) buffer.putShort((short) (seed * index));
        return Files.write(path, buffer.array());
    }

    /** 单个用例的独立数据库、偏好与文件根；重载模拟新的进程实例。 */
    private final class Fixture implements AutoCloseable {
        final Path source = root.resolve("旧目录"),
                target = root.resolve("新目录"),
                data = root.resolve("data"),
                databasePath = data.resolve("music-player.db");
        final String preferenceNode = "/app/musicplayer/storage-tests/" + UUID.randomUUID();
        Preferences preferences = Preferences.userRoot().node(preferenceNode);
        MusicDatabase database;
        DownloadDirectorySettings settings;
        DownloadMigrationService service;

        /** 准备独立文件夹，不访问用户曲库。 */
        Fixture() throws Exception {
            Files.createDirectories(source);
            Files.createDirectories(target);
            Files.createDirectories(data);
            reload();
        }

        /** 关闭旧连接后从磁盘恢复，验证不是依赖内存状态继续任务。 */
        void reload() throws Exception {
            if (database != null) database.close();
            database = new MusicDatabase(databasePath);
            settings = new DownloadDirectorySettings(preferences, source, null);
            service = new DownloadMigrationService(database, settings, data);
        }

        /** 在旧根生成有效音频。 */
        Path audio(String name, int seed) throws Exception {
            return writeAudio(source.resolve(name), seed);
        }

        /** 执行实际迁移逻辑，空进度回调不会改变时序。 */
        DownloadMigrationService.Result execute(AtomicBoolean cancelled) throws Exception {
            return service.execute(
                    database.pendingDownloadMigration().orElseThrow(),
                    cancelled::get,
                    ignored -> {});
        }

        /** 释放本用例连接和注册表节点。 */
        @Override
        public void close() throws Exception {
            if (database != null) database.close();
            preferences.removeNode();
        }
    }
}
