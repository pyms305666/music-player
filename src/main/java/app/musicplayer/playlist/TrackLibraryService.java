package app.musicplayer.playlist;

import app.musicplayer.model.Track;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 负责扫描音频、去重、搜索匹配和排序，不依赖 JavaFX 控件。
 * UI 只负责选择文件并显示结果，文件规则集中在这里维护。
 */
public final class TrackLibraryService {
    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("mp3", "m4a", "aac", "wav", "aif", "aiff");

    private final Map<Path, Long> creationTimeCache = new HashMap<>();

    public List<Track> scanFolder(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isSupportedAudio)
                    .sorted()
                    .map(Track::new)
                    .toList();
        }
    }

    public List<Track> fromFiles(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream()
                .filter(Files::isRegularFile)
                .filter(this::isSupportedAudio)
                .map(Track::new)
                .toList();
    }

    public ImportResult mergeUnique(List<Track> existingTracks, List<Track> importedTracks) {
        List<Track> candidates = importedTracks == null ? List.of() : importedTracks;
        Set<Path> existingPaths = new HashSet<>();
        if (existingTracks != null) {
            existingTracks.stream().map(Track::path).map(this::normalize).forEach(existingPaths::add);
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

    public boolean isSupportedAudio(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && SUPPORTED_EXTENSIONS.contains(
                fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public boolean matches(Track track, String query) {
        if (track == null) {
            return false;
        }
        String normalized = normalizeText(query);
        if (normalized.isBlank()) {
            return true;
        }
        String fileName = track.path().getFileName() == null
                ? ""
                : normalizeText(track.path().getFileName().toString());
        return normalizeText(track.title()).contains(normalized)
                || normalizeText(track.artist()).contains(normalized)
                || fileName.contains(normalized);
    }

    public Comparator<Track> comparator(PlaylistSort sort, SortDirection direction) {
        PlaylistSort selectedSort = sort == null ? PlaylistSort.TITLE : sort;
        Comparator<Track> comparator = switch (selectedSort) {
            case ARTIST -> Comparator.comparing(this::artistKey).thenComparing(this::titleKey);
            case FILE_NAME -> Comparator.comparing(this::fileNameKey).thenComparing(this::titleKey);
            case CREATED_AT -> Comparator.comparingLong(this::creationTime).thenComparing(this::titleKey);
            case TITLE -> Comparator.comparing(this::titleKey).thenComparing(this::artistKey);
        };
        return direction == SortDirection.DESCENDING ? comparator.reversed() : comparator;
    }

    private String titleKey(Track track) {
        return normalizeText(track == null ? "" : track.title()) + '\0'
                + normalizeText(track == null ? "" : track.artist());
    }

    private String artistKey(Track track) {
        return normalizeText(track == null ? "" : track.artist()) + '\0' + titleKey(track);
    }

    private String fileNameKey(Track track) {
        if (track == null || track.path().getFileName() == null) {
            return "";
        }
        return normalizeText(track.path().getFileName().toString());
    }

    private long creationTime(Track track) {
        if (track == null) {
            return Long.MIN_VALUE;
        }
        Path path = normalize(track.path());
        return creationTimeCache.computeIfAbsent(path, ignored -> readCreationTime(path));
    }

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

    private Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record ImportResult(List<Track> addedTracks, int duplicateCount, int totalCount) {
    }
}
