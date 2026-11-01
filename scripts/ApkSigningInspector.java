import com.android.apksig.ApkVerifier;
import com.android.apksig.SigningCertificateLineage;

import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.*;

/** 独立 APK 二进制签名检查入口。使用 Android apksig 读取证书、轮换证明与清单信息，供正式签名及发布门禁校验。 */
public final class ApkSigningInspector {
    /** 将散列或二进制身份转为可比较的文本。 */
    static String hash(X509Certificate c) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded()));
    }

    /** 命令行或应用启动入口，准备运行环境并启动本文件的主流程。 */
    public static void main(String[] args) throws Exception {
        File apk = new File(args[0]);
        List<String> checks = new ArrayList<>();
        for (int api : new int[] {28, 31, 32, 33, 35, 36}) {
            var result =
                    new ApkVerifier.Builder(apk)
                            .setMinCheckedPlatformVersion(api)
                            .setMaxCheckedPlatformVersion(api)
                            .build()
                            .verify();
            if (!result.isVerified() || result.getSignerCertificates().size() != 1)
                throw new IllegalStateException(
                        "APK verification failed for API " + api + ": " + result.getAllErrors());
            checks.add("\"" + api + "\":\"" + hash(result.getSignerCertificates().get(0)) + "\"");
        }
        var lineage = SigningCertificateLineage.readFromApkFile(apk);
        if (lineage == null || lineage.size() != 2)
            throw new IllegalStateException("Expected one old-to-new signing rotation");
        var certs = lineage.getCertificatesInLineage();
        var caps = lineage.getSignerCapabilities(certs.get(0));
        if (!caps.hasInstalledData()
                || caps.hasRollback()
                || caps.hasSharedUid()
                || !caps.hasPermission()
                || caps.hasAuth())
            throw new IllegalStateException("Unexpected old certificate capabilities");
        System.out.println(
                "{\"apiSigners\":{"
                        + String.join(",", checks)
                        + "},\"lineage\":[\""
                        + hash(certs.get(0))
                        + "\",\""
                        + hash(certs.get(1))
                        + "\"],\"oldCertificateRollback\":false}");
    }
}
