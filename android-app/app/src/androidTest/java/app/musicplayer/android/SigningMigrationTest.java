package app.musicplayer.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.musicplayer.android.data.AndroidMusicDatabase;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Records only local hashes; never exports personal tracks, lyrics or URI strings. */
@RunWith(AndroidJUnit4.class)
public class SigningMigrationTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private String argument(String name) { return InstrumentationRegistry.getArguments().getString(name, ""); }
    private File snapshot() { return new File(context().getCacheDir(), "signing-upgrade-snapshot.json"); }
    private String hash(byte[] data) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) text.append(String.format(Locale.ROOT, "%02x", b));
        return text.toString();
    }
    private byte[] prefix(InputStream input) throws IOException {
        if (input == null) throw new IOException("Media stream unavailable");
        byte[] data = new byte[64]; int total = 0, count;
        while (total < data.length && (count = input.read(data, total, data.length - total)) != -1) total += count;
        return Arrays.copyOf(data, total);
    }
    private JSONObject state() throws Exception {
        JSONArray tables = new JSONArray(), readable = new JSONArray();
        try (AndroidMusicDatabase helper = new AndroidMusicDatabase(context())) {
            var db = helper.getReadableDatabase();
            try (var cursor = db.rawQuery("pragma integrity_check", null)) { assertTrue(cursor.moveToFirst()); assertEquals("ok", cursor.getString(0)); }
            for (String table : List.of("tracks", "lyrics")) {
                JSONArray rows = new JSONArray();
                try (var cursor = db.rawQuery("select * from " + table + " order by path", null)) {
                    while (cursor.moveToNext()) {
                        JSONArray row = new JSONArray();
                        for (int i = 0; i < cursor.getColumnCount(); i++) row.put(cursor.isNull(i) ? JSONObject.NULL : cursor.getString(i));
                        rows.put(row);
                        if (table.equals("tracks")) {
                            try (InputStream input = cursor.getString(cursor.getColumnIndexOrThrow("storage_type")).equals("MEDIA_STORE")
                                    ? context().getContentResolver().openInputStream(android.net.Uri.parse(cursor.getString(0)))
                                    : new FileInputStream(cursor.getString(0))) {
                                readable.put(hash(prefix(input)));
                            } catch (IOException | SecurityException unavailable) { readable.put("unavailable"); }
                        }
                    }
                }
                tables.put(rows);
            }
        }
        List<String> prefs = new ArrayList<>();
        File[] preferenceFiles = new File(context().getApplicationInfo().dataDir, "shared_prefs").listFiles();
        if (preferenceFiles != null) for (File file : preferenceFiles) if (file.isFile()) prefs.add(file.getName() + ":" + hash(Files.readAllBytes(file.toPath())));
        Collections.sort(prefs);
        List<String> permissions = new ArrayList<>();
        for (var grant : context().getContentResolver().getPersistedUriPermissions()) permissions.add(grant.getUri() + ":" + grant.isReadPermission() + ":" + grant.isWritePermission());
        PackageInfo info = context().getPackageManager().getPackageInfo(context().getPackageName(), PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (int i = 0; i < info.requestedPermissions.length; i++) permissions.add(info.requestedPermissions[i] + ":" + ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0));
        if (Build.VERSION.SDK_INT >= 30) permissions.add("all-files:" + Environment.isExternalStorageManager());
        Collections.sort(permissions);
        return new JSONObject().put("uid", context().getApplicationInfo().uid)
                .put("libraryDigest", hash(tables.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .put("readableDigest", hash(readable.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .put("prefsDigest", hash(prefs.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .put("permissionDigest", hash(permissions.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private void seed() throws Exception {
        File file = new File(context().getFilesDir(), "signing-fixture.wav");
        int size = 8000 * 2 * 2;
        ByteBuffer wav = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36 + size).put(new byte[]{'W','A','V','E','f','m','t',' '});
        wav.putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16);
        wav.put(new byte[]{'d','a','t','a'}).putInt(size); Files.write(file.toPath(), wav.array());
        try (AndroidMusicDatabase helper = new AndroidMusicDatabase(context())) {
            helper.getWritableDatabase().execSQL("insert or replace into tracks values(?,?,?,?,?,?)", new Object[]{file.toString(), "Signing fixture", "QA", 123L, "FILE", file.getName()});
            helper.getWritableDatabase().execSQL("insert or replace into lyrics values(?,?,?,?)", new Object[]{file.toString(), "fixture", "[00:00]Signing fixture", ""});
        }
        assertTrue(context().getSharedPreferences("signing-fixture", Context.MODE_PRIVATE).edit().putString("setting", "keep-me").commit());
    }
    @Test public void recordBeforeUpgrade() throws Exception {
        Assume.assumeTrue(argument("migrationPhase").equals("record"));
        if (argument("seedFixture").equals("true")) seed();
        Files.write(snapshot().toPath(), state().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test public void verifyAfterUpgrade() throws Exception {
        Assume.assumeTrue(argument("migrationPhase").equals("verify"));
        assertTrue("Pre-upgrade snapshot missing", snapshot().exists());
        JSONObject before = new JSONObject(new String(Files.readAllBytes(snapshot().toPath()), java.nio.charset.StandardCharsets.UTF_8)), after = state();
        for (String key : List.of("uid", "libraryDigest", "readableDigest", "prefsDigest", "permissionDigest")) assertEquals("Upgrade changed " + key, before.get(key), after.get(key));
        assertEquals(0, context().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
        PackageInfo info = context().getPackageManager().getPackageInfo(context().getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        String expected = argument(Build.VERSION.SDK_INT >= 33 ? "expectedNewSha" : "expectedOldSha");
        assertFalse("Expected signer is required", expected.isBlank());
        assertEquals(expected, hash(info.signingInfo.getApkContentsSigners()[0].toByteArray()));
        if (Build.VERSION.SDK_INT >= 33) {
            List<String> history = new ArrayList<>();
            for (var cert : info.signingInfo.getSigningCertificateHistory()) history.add(hash(cert.toByteArray()));
            assertEquals(List.of(argument("expectedOldSha"), argument("expectedNewSha")), history);
        }
    }
    @Test public void removeTemporarySnapshot() {
        Assume.assumeTrue(argument("migrationPhase").equals("cleanup"));
        assertTrue(!snapshot().exists() || snapshot().delete());
    }
}
