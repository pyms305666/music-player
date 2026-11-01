package app.musicplayer.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 数据目录实例锁。正常播放器共享读取锁，启动迁移独占锁；关闭只释放自己创建的句柄。 */
public final class StorageInstanceLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    /** 保存本实例获得的通道与锁。 */
    private StorageInstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /** 迁移不能与其他新版本播放器并行，获取失败时交给界面提示。 */
    public static StorageInstanceLock acquire(Path dataDirectory, boolean shared)
            throws IOException {
        Files.createDirectories(dataDirectory);
        FileChannel channel =
                FileChannel.open(
                        dataDirectory.resolve(".storage.lock"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock(0, Long.MAX_VALUE, shared);
            if (lock == null) throw new IOException("请关闭其他播放器实例后再执行迁移");
            return new StorageInstanceLock(channel, lock);
        } catch (IOException | OverlappingFileLockException failure) {
            channel.close();
            throw new IOException("数据目录正在被其他播放器使用，请关闭后重试", failure);
        }
    }

    /** 正常退出或切换锁模式时释放本实例资源。 */
    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
