package app.musicplayer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.application.Platform;
import javafx.scene.control.Label;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

/**
 * AudioFormatBadge 音频格式胶囊徽章解析与样式绑定的单元测试。
 */
class AudioFormatBadgeTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @Test
    @DisplayName("应该为 .flac 扩展名解析 FLAC 无损徽章")
    void shouldResolveFlacBadgeForFlacExtension() {
        var badge = AudioFormatBadge.resolve("D:/Music/song.flac");
        assertNotNull(badge);
        assertEquals("FLAC", badge.label());
        assertTrue(badge.isLossless());
    }

    @Test
    @DisplayName("应该为 .wav 扩展名解析 WAV 无损徽章")
    void shouldResolveWavBadgeForWavExtension() {
        var badge = AudioFormatBadge.resolve("D:/Music/song.wav");
        assertNotNull(badge);
        assertEquals("WAV", badge.label());
        assertTrue(badge.isLossless());
    }

    @Test
    @DisplayName("应该为 .ape 扩展名解析 APE 无损徽章")
    void shouldResolveApeBadgeForApeExtension() {
        var badge = AudioFormatBadge.resolve("D:/Music/song.ape");
        assertNotNull(badge);
        assertEquals("APE", badge.label());
        assertTrue(badge.isLossless());
    }

    @ParameterizedTest
    @ValueSource(strings = {"song.alac", "song.aiff", "song.aif", "D:/Music/hires.AIFF"})
    @DisplayName("应该为 .alac / .aiff 扩展名解析 HI-RES 无损徽章")
    void shouldResolveHiResBadgeForAlacAndAiff(String path) {
        var badge = AudioFormatBadge.resolve(path);
        assertNotNull(badge);
        assertEquals("HI-RES", badge.label());
        assertTrue(badge.isLossless());
    }

    @Test
    @DisplayName("应该为 .mp3 扩展名解析 MP3 标准音质徽章")
    void shouldResolveMp3BadgeForStandardAudio() {
        var badge = AudioFormatBadge.resolve("D:/Music/song.mp3");
        assertNotNull(badge);
        assertEquals("MP3", badge.label());
        assertFalse(badge.isLossless());
    }

    @ParameterizedTest
    @ValueSource(strings = {"song.m4a", "song.aac", "D:/Music/audio.AAC", "D:/Music/audio.M4A"})
    @DisplayName("应该为 .m4a / .aac 扩展名解析 AAC 标准音质徽章")
    void shouldResolveAacBadgeForM4aAndAac(String path) {
        var badge = AudioFormatBadge.resolve(path);
        assertNotNull(badge);
        assertEquals("AAC", badge.label());
        assertFalse(badge.isLossless());
    }

    @ParameterizedTest
    @ValueSource(strings = {"song.ogg", "song.opus", "D:/Music/audio.OGG", "D:/Music/audio.OPUS"})
    @DisplayName("应该为 .ogg / .opus 扩展名解析 OGG 标准音质徽章")
    void shouldResolveOggBadgeForOggAndOpus(String path) {
        var badge = AudioFormatBadge.resolve(path);
        assertNotNull(badge);
        assertEquals("OGG", badge.label());
        assertFalse(badge.isLossless());
    }

    @Test
    @DisplayName("大小写混写 (.FlAc, .Mp3) 应该正确解析")
    void shouldResolveMixedCaseExtensions() {
        var flacBadge = AudioFormatBadge.resolve("D:/Music/Song.FlAc");
        assertNotNull(flacBadge);
        assertEquals("FLAC", flacBadge.label());
        assertTrue(flacBadge.isLossless());

        var mp3Badge = AudioFormatBadge.resolve("D:/Music/Song.Mp3");
        assertNotNull(mp3Badge);
        assertEquals("MP3", mp3Badge.label());
        assertFalse(mp3Badge.isLossless());
    }

    @Test
    @DisplayName("多重点号路径应提取最终文件扩展名")
    void shouldHandlePathsWithMultipleDots() {
        var badge = AudioFormatBadge.resolve("D:/Music/artist.name/01. my.song.final.flac");
        assertNotNull(badge);
        assertEquals("FLAC", badge.label());
        assertTrue(badge.isLossless());
    }

    @Test
    @DisplayName("目录含点但文件名无扩展名时应返回 null")
    void shouldReturnNullWhenDirectoryHasDotButFileHasNoExtension() {
        assertNull(AudioFormatBadge.resolve("C:/Music.Folder/song"));
        assertNull(AudioFormatBadge.resolve("C:\\Music.2024\\song"));
    }

    @Test
    @DisplayName("无扩展名或以点号结尾的文件应返回 null")
    void shouldReturnNullForNoExtensionOrTrailingDot() {
        assertNull(AudioFormatBadge.resolve("song"));
        assertNull(AudioFormatBadge.resolve("song."));
        assertNull(AudioFormatBadge.resolve("D:/Music/song."));
    }

    @Test
    @DisplayName("不支持的扩展名应返回 null")
    void shouldReturnNullForUnsupportedExtensions() {
        assertNull(AudioFormatBadge.resolve("song.txt"));
        assertNull(AudioFormatBadge.resolve("song.wma"));
        assertNull(AudioFormatBadge.resolve("song.exe"));
    }

    @Test
    @DisplayName("null 或空白路径应返回 null")
    void shouldReturnNullForNullOrBlankPath() {
        assertNull(AudioFormatBadge.resolve((String) null));
        assertNull(AudioFormatBadge.resolve(""));
        assertNull(AudioFormatBadge.resolve("   "));
        assertNull(AudioFormatBadge.resolve((Path) null));
    }

    @Test
    @DisplayName("通过 java.nio.file.Path 重载方法应正常解析")
    void shouldResolveViaPathOverload() {
        var badge = AudioFormatBadge.resolve(Path.of("D:/Music/test.flac"));
        assertNotNull(badge);
        assertEquals("FLAC", badge.label());
        assertTrue(badge.isLossless());
    }

    @Test
    @DisplayName("applyStyle 应该正确为无损格式配置 Label 文本与样式类")
    void shouldApplyStyleForLosslessFormat() {
        Label label = new Label();
        label.getStyleClass().add("old-class");

        AudioFormatBadge.AudioFormatInfo info = new AudioFormatBadge.AudioFormatInfo("FLAC", true);
        AudioFormatBadge.applyStyle(label, info);

        assertEquals("FLAC", label.getText());
        assertTrue(label.isVisible());
        assertTrue(label.getStyleClass().contains("audio-badge-flac"));
        assertFalse(label.getStyleClass().contains("old-class"));
        assertFalse(label.getStyleClass().contains("audio-badge-standard"));
    }

    @Test
    @DisplayName("applyStyle 应该正确为标准格式配置 Label 文本与样式类")
    void shouldApplyStyleForStandardFormat() {
        Label label = new Label();
        label.getStyleClass().add("audio-badge-flac");

        AudioFormatBadge.AudioFormatInfo info = new AudioFormatBadge.AudioFormatInfo("MP3", false);
        AudioFormatBadge.applyStyle(label, info);

        assertEquals("MP3", label.getText());
        assertTrue(label.isVisible());
        assertTrue(label.getStyleClass().contains("audio-badge-standard"));
        assertFalse(label.getStyleClass().contains("audio-badge-flac"));
    }

    @Test
    @DisplayName("applyStyle 传入 null info 时应该隐藏 Label 并清除样式")
    void shouldClearAndHideLabelWhenInfoIsNull() {
        Label label = new Label();
        AudioFormatBadge.applyStyle(label, new AudioFormatBadge.AudioFormatInfo("FLAC", true));

        AudioFormatBadge.applyStyle(label, null);

        assertNull(label.getText());
        assertFalse(label.isVisible());
        assertFalse(label.getStyleClass().contains("audio-badge-flac"));
    }

    @Test
    @DisplayName("applyStyle 传入 null label 时应安全处理不抛出异常")
    void shouldHandleNullLabelSafely() {
        AudioFormatBadge.applyStyle(null, new AudioFormatBadge.AudioFormatInfo("FLAC", true));
        AudioFormatBadge.applyStyle(null, null);
    }
}
