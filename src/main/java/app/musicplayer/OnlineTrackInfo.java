// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public record OnlineTrackInfo(
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        String source,
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        String title,
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        String artist,
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        String album,
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        String artworkUrl,
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        String primaryId,
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        String secondaryId,
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        boolean downloadable,
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        String availabilityText
// 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
) {
    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    public OnlineTrackInfo(
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            String source,
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            String title,
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            String artist,
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            String album,
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            String artworkUrl,
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            String primaryId,
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            String secondaryId
    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    ) {
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        this(source, title, artist, album, artworkUrl, primaryId, secondaryId, false, "待检测");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public OnlineTrackInfo withAvailability(boolean downloadable, String availabilityText) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return new OnlineTrackInfo(
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                source,
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                title,
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                artist,
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                album,
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                artworkUrl,
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                primaryId,
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                secondaryId,
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                downloadable,
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                availabilityText
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        );
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public boolean canAttemptDownload() {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return downloadable || "可尝试下载".equals(availabilityText);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public String subtitle() {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String albumText = album == null || album.isBlank() ? "未知专辑" : album;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String artistText = artist == null || artist.isBlank() ? "未知歌手" : artist;
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return artistText + " · " + albumText + " · " + source + " · " + availabilityText;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
