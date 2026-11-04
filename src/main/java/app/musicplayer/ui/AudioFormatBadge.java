package app.musicplayer.ui;

import javafx.scene.control.Label;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 音频格式胶囊徽章解析与样式绑定辅助工具。
 */
public final class AudioFormatBadge {

    public static final String STYLE_FLAC = "audio-badge-flac";
    public static final String STYLE_STANDARD = "audio-badge-standard";

    private AudioFormatBadge() {}

    /**
     * 音频格式徽章展示信息。
     *
     * @param label 展示在胶囊徽章中的文案，如 "FLAC", "MP3"
     * @param isLossless 是否属于无损 / 高解析度音质（用于应用青色高亮或微灰低调样式）
     */
    public record AudioFormatInfo(String label, boolean isLossless) {}

    /**
     * 根据音频文件 Path 解析格式徽章信息。
     *
     * @param path 音频文件 Path
     * @return 匹配的 AudioFormatInfo，若路径为空或不支持该格式则返回 null
     */
    public static AudioFormatInfo resolve(Path path) {
        return path == null ? null : resolve(path.toString());
    }

    /**
     * 根据音频文件路径字符串解析格式徽章信息。
     *
     * @param path 音频文件路径字符串
     * @return 匹配的 AudioFormatInfo，若路径为空、无有效扩展名或不支持则返回 null
     */
    public static AudioFormatInfo resolve(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.trim();
        int lastSeparator = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot <= lastSeparator || lastDot == trimmed.length() - 1) {
            return null;
        }

        String ext = trimmed.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "flac" -> new AudioFormatInfo("FLAC", true);
            case "wav" -> new AudioFormatInfo("WAV", true);
            case "ape" -> new AudioFormatInfo("APE", true);
            case "alac", "aiff", "aif" -> new AudioFormatInfo("HI-RES", true);
            case "mp3" -> new AudioFormatInfo("MP3", false);
            case "m4a", "aac" -> new AudioFormatInfo("AAC", false);
            case "ogg", "opus" -> new AudioFormatInfo("OGG", false);
            default -> null;
        };
    }

    /**
     * 将格式徽章信息应用到指定的 JavaFX Label 控件上。
     *
     * @param label 待设置样式的标签控件
     * @param info 音频格式信息，为 null 时清除文本、样式并隐藏
     */
    public static void applyStyle(Label label, AudioFormatInfo info) {
        if (label == null) {
            return;
        }
        if (info == null) {
            label.setText(null);
            label.getStyleClass().clear();
            label.setVisible(false);
            return;
        }
        label.setText(info.label());
        label.getStyleClass().setAll(info.isLossless() ? STYLE_FLAC : STYLE_STANDARD);
        label.setVisible(true);
    }
}
