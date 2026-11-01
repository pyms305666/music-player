package app.musicplayer.model;

/** 共享在线歌曲模型。保存来源身份、名称、歌手、可下载状态和来源特有字段，用于搜索、下载、预览及去重。 */
public record OnlineTrackInfo(
        String source,
        String title,
        String artist,
        String album,
        String artworkUrl,
        String primaryId,
        String secondaryId,
        Availability availability,
        String availabilityText) {
    /** 表示在线歌曲是否可直接获取、受限或未知，供下载按钮及任务提示使用。 */
    public enum Availability {
        PENDING,
        TENTATIVE,
        AVAILABLE,
        UNAVAILABLE
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public OnlineTrackInfo(
            String source,
            String title,
            String artist,
            String album,
            String artworkUrl,
            String primaryId,
            String secondaryId) {
        this(
                source,
                title,
                artist,
                album,
                artworkUrl,
                primaryId,
                secondaryId,
                Availability.PENDING,
                "待检测");
    }

    /** 保留歌曲身份与元数据，仅替换下载可用状态及说明。 */
    public OnlineTrackInfo withAvailability(Availability availability, String availabilityText) {
        return new OnlineTrackInfo(
                source,
                title,
                artist,
                album,
                artworkUrl,
                primaryId,
                secondaryId,
                availability,
                availabilityText);
    }

    /** 检查当前状态是否允许尝试下载；最终可用性由实际下载确认。 */
    public boolean canAttemptDownload() {
        return availability == Availability.AVAILABLE || availability == Availability.TENTATIVE;
    }

    /** 返回歌曲当前声明的下载可用性。 */
    public boolean downloadable() {
        return availability == Availability.AVAILABLE;
    }

    /** 组合来源及歌曲标识，生成跨列表稳定的歌曲身份。 */
    public String identity() {
        return (source == null ? "" : source) + "|" + (primaryId == null ? "" : primaryId);
    }

    /** 组合歌手、来源等副标题信息，供在线列表显示。 */
    public String subtitle() {
        String albumText = album == null || album.isBlank() ? "未知专辑" : album;
        String artistText = artist == null || artist.isBlank() ? "未知歌手" : artist;
        return artistText + " · " + albumText + " · " + source + " · " + availabilityText;
    }
}
