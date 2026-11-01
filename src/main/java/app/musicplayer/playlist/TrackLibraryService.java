package app.musicplayer.playlist;

import app.musicplayer.model.Track;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** 负责扫描音频、去重、搜索匹配和排序，不依赖 JavaFX 控件。 UI 只负责选择文件并显示结果，文件规则集中在这里维护。 */
public final class TrackLibraryService {
    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("mp3", "m4a", "aac", "wav", "aif", "aiff");

    private final Map<Path, Long> creationTimeCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 递归扫描文件夹中的受支持音频，创建歌曲并预先读取创建时间。 */
    public List<Track> scanFolder(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(
                            path ->
                                    !app.musicplayer.config.DownloadDirectorySettings.internal(
                                            path))
                    .filter(this::isSupportedAudio)
                    .sorted()
                    .map(Track::new)
                    .peek(this::creationTime)
                    .toList();
        }
    }

    /** 将文件选择结果转为受支持的歌曲列表，忽略无效文件。 */
    public List<Track> fromFiles(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream()
                .filter(Files::isRegularFile)
                .filter(path -> !app.musicplayer.config.DownloadDirectorySettings.internal(path))
                .filter(this::isSupportedAudio)
                .map(Track::new)
                .peek(this::creationTime)
                .toList();
    }

    /** 预先缓存歌曲创建时间，避免界面排序时逐个读取文件属性。 */
    public void primeCreationTimes(List<Track> tracks) {
        tracks.forEach(this::creationTime);
    }

    /** 按规范化路径合并导入歌曲，返回新增歌曲及重复数量。 */
    public ImportResult mergeUnique(List<Track> existingTracks, List<Track> importedTracks) {
        List<Track> candidates = importedTracks == null ? List.of() : importedTracks;
        Set<Path> existingPaths = new HashSet<>();
        if (existingTracks != null) {
            existingTracks.stream()
                    .map(Track::path)
                    .map(this::normalize)
                    .forEach(existingPaths::add);
        }

        List<Track> added = new ArrayList<>();
        int duplicateCount = 0;
        for (Track candidate : candidates) {
            if (existingPaths.add(normalize(candidate.path()))) {
                added.add(candidate);
            } else {
                duplicateCount++;
            }
        }
        return new ImportResult(List.copyOf(added), duplicateCount, candidates.size());
    }

    /** 按扩展名及目录检查导入格式，内部暂存中的文件不属于可导入音频。 */
    public boolean isSupportedAudio(Path path) {
        if (path == null
                || path.getFileName() == null
                || app.musicplayer.config.DownloadDirectorySettings.internal(path)) {
            return false;
        }
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0
                && SUPPORTED_EXTENSIONS.contains(
                        fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** 应用本组件的名称、身份或搜索规则，检查候选是否匹配。 */
    public boolean matches(Track track, String query) {
        if (track == null) {
            return false;
        }
        String normalized = normalizeText(query);
        if (normalized.isBlank()) {
            return true;
        }
        String fileName =
                track.path().getFileName() == null
                        ? ""
                        : normalizeText(track.path().getFileName().toString());
        return normalizeText(track.title()).contains(normalized)
                || normalizeText(track.artist()).contains(normalized)
                || fileName.contains(normalized);
    }

    /** 根据排序项与方向构造歌曲比较器。 */
    public Comparator<Track> comparator(PlaylistSort sort, SortDirection direction) {
        PlaylistSort selectedSort = sort == null ? PlaylistSort.TITLE : sort;
        Comparator<Track> comparator =
                switch (selectedSort) {
                    case ARTIST ->
                            Comparator.comparing(this::artistKey).thenComparing(this::titleKey);
                    case FILE_NAME ->
                            Comparator.comparing(this::fileNameKey).thenComparing(this::titleKey);
                    case CREATED_AT ->
                            Comparator.comparingLong(this::creationTime)
                                    .thenComparing(this::titleKey);
                    case TITLE ->
                            Comparator.comparing(this::titleKey).thenComparing(this::artistKey);
                };
        return direction == SortDirection.DESCENDING ? comparator.reversed() : comparator;
    }

    /** 提取规范化的排序键，使用稳定文本比较歌曲。 */
    private String titleKey(Track track) {
        return normalizeText(track == null ? "" : track.title())
                + '\0'
                + normalizeText(track == null ? "" : track.artist());
    }

    /** 提取规范化的排序键，使用稳定文本比较歌曲。 */
    private String artistKey(Track track) {
        return normalizeText(track == null ? "" : track.artist()) + '\0' + titleKey(track);
    }

    /** 提取规范化的排序键，使用稳定文本比较歌曲。 */
    private String fileNameKey(Track track) {
        if (track == null || track.path().getFileName() == null) {
            return "";
        }
        return normalizeText(track.path().getFileName().toString());
    }

    /** 读取或复用歌曲创建时间，供创建日期排序使用。 */
    private long creationTime(Track track) {
        if (track == null) {
            return Long.MIN_VALUE;
        }
        Path path = normalize(track.path());
        return creationTimeCache.computeIfAbsent(path, ignored -> readCreationTime(path));
    }

    /** 从文件属性读取创建时间；无法读取时使用约定的备用值。 */
    private long readCreationTime(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class).creationTime().toMillis();
        } catch (IOException ignored) {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException ignoredAgain) {
                return Long.MIN_VALUE;
            }
        }
    }

    /** 按本组件的比较规则统一文本或路径，减少大小写与标点差异。 */
    private Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /** 按本组件的比较规则统一文本或路径，减少大小写与标点差异。 */
    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** 保存新增歌曲及重复数量。 */
    public record ImportResult(List<Track> addedTracks, int duplicateCount, int totalCount) {}
}
