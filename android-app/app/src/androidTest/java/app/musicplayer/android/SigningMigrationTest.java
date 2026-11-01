package app.musicplayer.android;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import app.musicplayer.android.data.AndroidMusicDatabase;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/**
 * 验证 Android 新旧签名版本覆盖升级后曲库、歌词、权限与播放状态保留。 Records only local hashes; never exports personal tracks,
 * lyrics or URI strings.
 */
@RunWith(AndroidJUnit4.class)
public class SigningMigrationTest {
    /** 取得本测试使用的应用或隔离上下文。 */
    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /** 读取 instrumentation 或命令行传入的测试参数。 */
    private String argument(String name) {
        return InstrumentationRegistry.getArguments().getString(name, "");
    }

    /** 捕获当前状态，供后续断言比较。 */
    private File snapshot() {
        return new File(context().getCacheDir(), "signing-upgrade-snapshot.json");
    }

    /** 将散列或二进制身份转为可比较的文本。 */
    private String hash(byte[] data) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data))
            text.append(String.format(Locale.ROOT, "%02x", b));
        return text.toString();
    }

    /** 取得验证数据使用的前缀或初始内容。 */
    private byte[] prefix(InputStream input) throws IOException {
        if (input == null) throw new IOException("Media stream unavailable");
        byte[] data = new byte[64];
        int total = 0, count;
        while (total < data.length && (count = input.read(data, total, data.length - total)) != -1)
            total += count;
        return Arrays.copyOf(data, total);
    }

    /** 读取当前服务、数据或夹具状态。 */
    private JSONObject state() throws Exception {
        JSONArray tables = new JSONArray(), readable = new JSONArray();
        try (AndroidMusicDatabase helper = new AndroidMusicDatabase(context())) {
            var db = helper.getReadableDatabase();
            try (var cursor = db.rawQuery("pragma integrity_check", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals("ok", cursor.getString(0));
            }
            for (String table : List.of("tracks", "lyrics")) {
                JSONArray rows = new JSONArray();
                try (var cursor = db.rawQuery("select * from " + table + " order by path", null)) {
                    while (cursor.moveToNext()) {
                        JSONArray row = new JSONArray();
                        for (int i = 0; i < cursor.getColumnCount(); i++)
                            row.put(cursor.isNull(i) ? JSONObject.NULL : cursor.getString(i));
                        rows.put(row);
                        if (table.equals("tracks")) {
                            try (InputStream input =
                                    cursor.getString(cursor.getColumnIndexOrThrow("storage_type"))
                                                    .equals("MEDIA_STORE")
                                            ? context()
                                                    .getContentResolver()
                                                    .openInputStream(
                                                            android.net.Uri.parse(
                                                                    cursor.getString(0)))
                                            : new FileInputStream(cursor.getString(0))) {
                                readable.put(hash(prefix(input)));
                            } catch (IOException | SecurityException unavailable) {
                                readable.put("unavailable");
                            }
                        }
                    }
                }
                tables.put(rows);
            }
        }
        List<String> prefs = new ArrayList<>();
        File[] preferenceFiles =
                new File(context().getApplicationInfo().dataDir, "shared_prefs").listFiles();
        if (preferenceFiles != null)
            for (File file : preferenceFiles)
                if (file.isFile())
                    prefs.add(file.getName() + ":" + hash(Files.readAllBytes(file.toPath())));
        Collections.sort(prefs);
        List<String> permissions = new ArrayList<>();
        for (var grant : context().getContentResolver().getPersistedUriPermissions())
            permissions.add(
                    grant.getUri()
                            + ":"
                            + grant.isReadPermission()
                            + ":"
                            + grant.isWritePermission());
        PackageInfo info =
                context()
                        .getPackageManager()
                        .getPackageInfo(context().getPackageName(), PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null)
            for (int i = 0; i < info.requestedPermissions.length; i++)
                permissions.add(
                        info.requestedPermissions[i]
                                + ":"
                                + ((info.requestedPermissionsFlags[i]
                                                & PackageInfo.REQUESTED_PERMISSION_GRANTED)
                                        != 0));
        if (Build.VERSION.SDK_INT >= 30)
            permissions.add("all-files:" + Environment.isExternalStorageManager());
        Collections.sort(permissions);
        return new JSONObject()
                .put("uid", context().getApplicationInfo().uid)
                .put(
                        "libraryDigest",
                        hash(tables.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .put(
                        "readableDigest",
                        hash(readable.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .put(
                        "prefsDigest",
                        hash(prefs.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .put(
                        "permissionDigest",
                        hash(
                                permissions
                                        .toString()
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** 按指定规模写入测试数据，供恢复或性能测量使用。 */
    private void seed() throws Exception {
        File file = new File(context().getFilesDir(), "signing-fixture.wav");
        int size = 8000 * 2 * 2;
        ByteBuffer wav = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[] {'R', 'I', 'F', 'F'})
                .putInt(36 + size)
                .put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        wav.putInt(16)
                .putShort((short) 1)
                .putShort((short) 1)
                .putInt(8000)
                .putInt(16000)
                .putShort((short) 2)
                .putShort((short) 16);
        wav.put(new byte[] {'d', 'a', 't', 'a'}).putInt(size);
        Files.write(file.toPath(), wav.array());
        try (AndroidMusicDatabase helper = new AndroidMusicDatabase(context())) {
            helper.getWritableDatabase()
                    .execSQL(
                            "insert or replace into tracks values(?,?,?,?,?,?)",
                            new Object[] {
                                file.toString(),
                                "Signing fixture",
                                "QA",
                                123L,
                                "FILE",
                                file.getName()
                            });
            helper.getWritableDatabase()
                    .execSQL(
                            "insert or replace into lyrics values(?,?,?,?)",
                            new Object[] {
                                file.toString(), "fixture", "[00:00]Signing fixture", ""
                            });
        }
        assertTrue(
                context()
                        .getSharedPreferences("signing-fixture", Context.MODE_PRIVATE)
                        .edit()
                        .putString("setting", "keep-me")
                        .commit());
    }

    /** 记录覆盖升级前的数据与权限证据。 */
    @Test
    public void recordBeforeUpgrade() throws Exception {
        Assume.assumeTrue(argument("migrationPhase").equals("record"));
        if (argument("seedFixture").equals("true")) seed();
        Files.write(
                snapshot().toPath(),
                state().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (argument("backupDatabase").equals("true")) {
            // Explicit local-phone opt-in; never export the backup as a CI artifact.
            assertTrue("VACUUM INTO backup requires Android 11+", Build.VERSION.SDK_INT >= 30);
            File backup =
                    new File(context().getExternalFilesDir(null), "qa-upgrade-database-backup.db");
            assertTrue(!backup.exists() || backup.delete());
            try (AndroidMusicDatabase helper = new AndroidMusicDatabase(context())) {
                helper.getReadableDatabase()
                        .execSQL("VACUUM INTO ?", new Object[] {backup.getAbsolutePath()});
            }
            assertTrue(backup.isFile());
        }
    }

    /** 把升级后数据与原快照比较，检查保留约定。 */
    @Test
    public void verifyAfterUpgrade() throws Exception {
        Assume.assumeTrue(argument("migrationPhase").equals("verify"));
        assertTrue("Pre-upgrade snapshot missing", snapshot().exists());
        JSONObject
                before =
                        new JSONObject(
                                new String(
                                        Files.readAllBytes(snapshot().toPath()),
                                        java.nio.charset.StandardCharsets.UTF_8)),
                after = state();
        for (String key :
                List.of(
                        "uid",
                        "libraryDigest",
                        "readableDigest",
                        "prefsDigest",
                        "permissionDigest"))
            assertEquals("Upgrade changed " + key, before.get(key), after.get(key));
        assertEquals(0, context().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
        PackageInfo info =
                context()
                        .getPackageManager()
                        .getPackageInfo(
                                context().getPackageName(),
                                PackageManager.GET_SIGNING_CERTIFICATES);
        String expected =
                argument(Build.VERSION.SDK_INT >= 33 ? "expectedNewSha" : "expectedOldSha");
        assertFalse("Expected signer is required", expected.isBlank());
        assertEquals(expected, hash(info.signingInfo.getApkContentsSigners()[0].toByteArray()));
        if (Build.VERSION.SDK_INT >= 33) {
            List<String> history = new ArrayList<>();
            for (var cert : info.signingInfo.getSigningCertificateHistory())
                history.add(hash(cert.toByteArray()));
            assertEquals(List.of(argument("expectedOldSha"), argument("expectedNewSha")), history);
        }
    }

    /** 移除当前用例创建的临时状态快照。 */
    @Test
    public void removeTemporarySnapshot() {
        Assume.assumeTrue(argument("migrationPhase").equals("cleanup"));
        assertTrue(!snapshot().exists() || snapshot().delete());
        File backup =
                new File(context().getExternalFilesDir(null), "qa-upgrade-database-backup.db");
        assertTrue(!backup.exists() || backup.delete());
    }
}
