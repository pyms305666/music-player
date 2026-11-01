package app.musicplayer.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 为缓存文件生成稳定、文件名安全的键。 */
public final class Hashing {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private Hashing() {}

    /** 把文本转换为稳定 SHA-1 缓存键，返回文件名安全的十六进制形式。 */
    public static String sha1(String value) {
        String safeValue = value == null ? "" : value;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(safeValue.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte valueByte : hash) {
                result.append(String.format("%02x", valueByte));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(safeValue.hashCode());
        }
    }
}
