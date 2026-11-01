package app.musicplayer.online;

import java.util.OptionalLong;

/**
 * 下载进度事件。描述解析、传输、校验、入库等阶段，携带已传输字节及可选总长度。 Byte totals describe the current transfer, never a
 * fabricated completion percentage.
 */
public record DownloadEvent(Stage stage, long transferredBytes, OptionalLong totalBytes) {
    /** 表示下载阶段。 */
    public enum Stage {
        QUEUED,
        RESOLVING,
        TRANSFERRING,
        VALIDATING,
        PUBLISHING,
        COMPLETE,
        FAILED,
        CANCELLED
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public DownloadEvent {
        transferredBytes = Math.max(0, transferredBytes);
        if (totalBytes.isPresent() && totalBytes.getAsLong() <= 0)
            totalBytes = OptionalLong.empty();
    }

    /** 根据输入构造当前数据类型的简明实例。 */
    public static DownloadEvent of(Stage stage) {
        return new DownloadEvent(stage, 0, OptionalLong.empty());
    }
}
