package app.musicplayer.online;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 网易云来源请求的协议加密辅助。实现所需 AES、RSA 和随机参数生成，供 NeteaseSourceProvider 构造请求。
 *
 * <p>NetEase Cloud Music weapi encryption.
 *
 * <p>Implements the weapi protocol used by the official web player: 1. AES-128-CBC encrypt params
 * with fixed key "0CoJUm6Qyw8W8jud" 2. AES-128-CBC encrypt result with random 16-char second key 3.
 * RSA-encrypt (PKCS1) the REVERSED second key -> hex encSecKey
 */
final class NeteaseCrypto {

    private static final String FIRST_KEY = "0CoJUm6Qyw8W8jud";
    private static final String IV = "0102030405060708";

    // NetEase's actual RSA public key (base64 DER)
    private static final String RSA_PUB_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQD84QJz7D4HnMRr4+85PzZ4ZRbv"
                    + "0YJ+7ZYsLmzNZqBLJHfA+7vC9j1A5B1TrSJGJN+P+7+lQ3j9JmG2N5+F9j+8w3n7"
                    + "LMkH1IxDQy0JcIukpP5l5Q6hK7m+2pQs+L2xDhY8n8VwpLMiBn4kY3CqXtLYO6kE"
                    + "ua0p6pNKUAXXr6Mh+QIDAQAB";

    private static final Random RNG = new Random();

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private NeteaseCrypto() {}

    /**
     * 按网易云接口协议封装并加密请求参数。
     *
     * <p>Encrypt JSON payload for the weapi endpoint.
     *
     * @return [params, encSecKey]
     */
    static String[] encrypt(String json) throws Exception {
        // Step 1: generate random 16-char second key
        String secKey = randomString(16);

        // Step 2: first AES with fixed key
        String firstEnc = aesEncrypt(json, FIRST_KEY, IV);

        // Step 3: second AES with random key
        String params = aesEncrypt(firstEnc, secKey, IV);

        // Step 4: RSA-encrypt the REVERSED second key
        String reversedKey = new StringBuilder(secKey).reverse().toString();
        String encSecKey = rsaEncrypt(reversedKey);

        return new String[] {params, encSecKey};
    }

    /** 按来源协议执行对应加密步骤，供请求参数构造使用。 */
    private static String aesEncrypt(String text, String key, String iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /** 按来源协议执行对应加密步骤，供请求参数构造使用。 */
    private static String rsaEncrypt(String text) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(RSA_PUB_KEY);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, kf.generatePublic(spec));
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(encrypted);
    }

    /** 生成对应来源协议需要的随机标识或随机参数。 */
    private static String randomString(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(RNG.nextInt(chars.length())));
        return sb.toString();
    }

    /** 把二进制数据转换为协议要求的十六进制文本。 */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static final String EAPI_KEY = "e82ckenh8dichen8";

    /** 按网易云接口协议封装并加密请求参数。 eapi encryption payload helper. */
    static String[] eapiEncrypt(String text) throws Exception {
        String secKey = randomString(16);
        // First AES with eapi fixed key
        String firstEnc = aesEncrypt(text, EAPI_KEY, IV);
        // Second AES with random key
        String params = aesEncrypt(firstEnc, secKey, IV);
        // RSA encrypt reversed key
        String reversedKey = new StringBuilder(secKey).reverse().toString();
        String encSecKey = rsaEncrypt(reversedKey);
        return new String[] {params, encSecKey};
    }
}
