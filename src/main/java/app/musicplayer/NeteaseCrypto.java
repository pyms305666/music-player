// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javax.crypto.Cipher;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javax.crypto.spec.IvParameterSpec;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javax.crypto.spec.SecretKeySpec;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.charset.StandardCharsets;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.security.KeyFactory;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.security.spec.X509EncodedKeySpec;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Base64;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Random;

/**
 * NetEase Cloud Music weapi encryption.
 *
 * Implements the weapi protocol used by the official web player:
 * 1. AES-128-CBC encrypt params with fixed key "0CoJUm6Qyw8W8jud"
 * 2. AES-128-CBC encrypt result with random 16-char second key
 * 3. RSA-encrypt (PKCS1) the REVERSED second key -> hex encSecKey
 */
// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
final class NeteaseCrypto {

    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String FIRST_KEY = "0CoJUm6Qyw8W8jud";
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String IV = "0102030405060708";

    // NetEase's actual RSA public key (base64 DER)
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String RSA_PUB_KEY =
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQD84QJz7D4HnMRr4+85PzZ4ZRbv" +
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        "0YJ+7ZYsLmzNZqBLJHfA+7vC9j1A5B1TrSJGJN+P+7+lQ3j9JmG2N5+F9j+8w3n7" +
        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
        "LMkH1IxDQy0JcIukpP5l5Q6hK7m+2pQs+L2xDhY8n8VwpLMiBn4kY3CqXtLYO6kE" +
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        "ua0p6pNKUAXXr6Mh+QIDAQAB";

    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Random RNG = new Random();

    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    private NeteaseCrypto() {}

    /**
     * Encrypt JSON payload for the weapi endpoint.
     * @return [params, encSecKey]
     */
    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String[] encrypt(String json) throws Exception {
        // Step 1: generate random 16-char second key
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String secKey = randomString(16);

        // Step 2: first AES with fixed key
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String firstEnc = aesEncrypt(json, FIRST_KEY, IV);

        // Step 3: second AES with random key
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String params = aesEncrypt(firstEnc, secKey, IV);

        // Step 4: RSA-encrypt the REVERSED second key
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String reversedKey = new StringBuilder(secKey).reverse().toString();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String encSecKey = rsaEncrypt(reversedKey);

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return new String[]{params, encSecKey};
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String aesEncrypt(String text, String key, String iv) throws Exception {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Base64.getEncoder().encodeToString(encrypted);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String rsaEncrypt(String text) throws Exception {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        byte[] keyBytes = Base64.getDecoder().decode(RSA_PUB_KEY);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        KeyFactory kf = KeyFactory.getInstance("RSA");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        cipher.init(Cipher.ENCRYPT_MODE, kf.generatePublic(spec));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return bytesToHex(encrypted);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String randomString(int len) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        StringBuilder sb = new StringBuilder(len);
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (int i = 0; i < len; i++) sb.append(chars.charAt(RNG.nextInt(chars.length())));
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return sb.toString();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String bytesToHex(byte[] bytes) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        StringBuilder sb = new StringBuilder();
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (byte b : bytes) sb.append(String.format("%02x", b));
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return sb.toString();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String EAPI_KEY = "e82ckenh8dichen8";

    /** eapi encryption payload helper. */
    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    static String[] eapiEncrypt(String text) throws Exception {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String secKey = randomString(16);
        // First AES with eapi fixed key
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String firstEnc = aesEncrypt(text, EAPI_KEY, IV);
        // Second AES with random key
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String params = aesEncrypt(firstEnc, secKey, IV);
        // RSA encrypt reversed key
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String reversedKey = new StringBuilder(secKey).reverse().toString();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String encSecKey = rsaEncrypt(reversedKey);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return new String[]{params, encSecKey};
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}