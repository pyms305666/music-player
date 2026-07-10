package app.musicplayer.playback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioFileInspectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsMp3Mp4AndRawAacHeaders() throws Exception {
        AudioFileInspector inspector = new AudioFileInspector();
        Path mp3 = Files.write(tempDir.resolve("audio.m4a"), new byte[]{(byte) 0xff, (byte) 0xfb, 0, 0});
        Path mp4 = Files.write(tempDir.resolve("audio.mp4"), new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'});
        Path aac = Files.write(tempDir.resolve("audio.aac"), new byte[]{(byte) 0xff, (byte) 0xf1, 0, 0});

        assertEquals(AudioFormat.MP3, inspector.detect(mp3));
        assertEquals(AudioFormat.MP4, inspector.detect(mp4));
        assertEquals(AudioFormat.RAW_AAC, inspector.detect(aac));
    }

    @Test
    void createsMp3PlaybackAliasForMislabeledDownload() throws Exception {
        Path source = Files.write(tempDir.resolve("download.m4a"), new byte[]{(byte) 0xff, (byte) 0xfb, 0, 0});
        AudioFileInspector inspector = new AudioFileInspector();
        PlaybackFileResolver resolver = new PlaybackFileResolver(tempDir.resolve("cache"), inspector);

        PlaybackFileResolver.Resolution resolution = resolver.resolve(source);

        assertTrue(resolution.correctedExtension());
        assertTrue(resolution.path().getFileName().toString().endsWith(".mp3"));
        assertTrue(Files.isRegularFile(resolution.path()));
    }
}
