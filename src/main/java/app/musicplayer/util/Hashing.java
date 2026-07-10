package app.musicplayer.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 为缓存文件生成稳定、文件名安全的键。 */
public final class Hashing {
    private Hashing() {
    }

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
