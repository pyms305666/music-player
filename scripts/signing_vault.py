"""Local current-user signing vault and disposable CI key generation."""
import ctypes
import json
import os
from pathlib import Path
import secrets
import shutil
import sys
import zipfile
import android_signing_loader

s = android_signing_loader.module
LEGACY_SHA = "1cd53ceaeef7ce7772d274b450982deeb34424d896612a66a95d9ee00dd2e3ff"


def dpapi(data, decrypt=False):
    class Blob(ctypes.Structure):
        _fields_ = [("size", ctypes.c_uint32), ("data", ctypes.POINTER(ctypes.c_ubyte))]
    buffer = (ctypes.c_ubyte * len(data)).from_buffer_copy(data)
    source, output = Blob(len(data), buffer), Blob()
    crypto = ctypes.WinDLL("crypt32", use_last_error=True)
    if decrypt:
        ok = crypto.CryptUnprotectData(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(output))
    else:
        ok = crypto.CryptProtectData(ctypes.byref(source), "ZA Music signing", None, None, None, 1, ctypes.byref(output))
    if not ok:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return ctypes.string_at(output.data, output.size)
    finally:
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.LocalFree.argtypes = [ctypes.c_void_p]
        kernel.LocalFree(output.data)


def vault():
    return Path(os.environ["USERPROFILE"]) / ".za-music-signing"


def protect_directory(path):
    path.mkdir(parents=True, exist_ok=True)
    user = s.run(["whoami"], capture=True).strip()
    s.run(["icacls", path, "/inheritance:r", "/grant:r", user + ":(OI)(CI)F", "SYSTEM:(OI)(CI)F"], capture=True)


def load():
    return json.loads(dpapi((vault() / "credentials.dpapi").read_bytes(), decrypt=True))


def create(directory, original=None):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    java, _, jar = s.tools()
    keytool = java.with_name("keytool.exe" if os.name == "nt" else "keytool")
    credentials = dict(ZA_KEYSTORE=str((directory / "formal.p12").resolve()), ZA_STORE_PASSWORD=secrets.token_urlsafe(36),
                       ZA_KEY_ALIAS="za-music-release", ZA_OLD_KEYSTORE=str((directory / "legacy.p12").resolve()),
                       ZA_OLD_STORE_PASSWORD=secrets.token_urlsafe(36), ZA_OLD_KEY_ALIAS="legacy")
    credentials["ZA_KEY_PASSWORD"] = credentials["ZA_STORE_PASSWORD"]
    credentials["ZA_OLD_KEY_PASSWORD"] = credentials["ZA_OLD_STORE_PASSWORD"]
    env = dict(os.environ, **credentials, ZA_ORIGINAL_DEBUG_PASSWORD="android")
    if original:
        s.run([keytool, "-importkeystore", "-noprompt", "-srckeystore", original, "-srcalias", "androiddebugkey",
               "-srcstorepass:env", "ZA_ORIGINAL_DEBUG_PASSWORD", "-srckeypass:env", "ZA_ORIGINAL_DEBUG_PASSWORD",
               "-destkeystore", credentials["ZA_OLD_KEYSTORE"], "-deststoretype", "PKCS12", "-destalias", "legacy",
               "-deststorepass:env", "ZA_OLD_STORE_PASSWORD", "-destkeypass:env", "ZA_OLD_KEY_PASSWORD"], env=env, capture=True)
    else:
        s.run([keytool, "-genkeypair", "-noprompt", "-keystore", credentials["ZA_OLD_KEYSTORE"], "-storetype", "PKCS12",
               "-storepass:env", "ZA_OLD_STORE_PASSWORD", "-keypass:env", "ZA_OLD_KEY_PASSWORD", "-alias", "legacy",
               "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650", "-dname", "CN=Disposable ZA legacy QA"], env=env, capture=True)
    s.run([keytool, "-genkeypair", "-noprompt", "-keystore", credentials["ZA_KEYSTORE"], "-storetype", "PKCS12",
           "-storepass:env", "ZA_STORE_PASSWORD", "-keypass:env", "ZA_KEY_PASSWORD", "-alias", "za-music-release",
           "-keyalg", "RSA", "-keysize", "3072", "-sigalg", "SHA256withRSA", "-validity", "10958",
           "-dname", "CN=ZA Music Application Signing"], env=env, capture=True)
    hashes = []
    for prefix, filename in [("ZA_OLD", "legacy-cert.der"), ("ZA", "formal-cert.der")]:
        cert = directory / filename
        s.run([keytool, "-exportcert", "-keystore", credentials[prefix + "_KEYSTORE"], "-alias", credentials[prefix + "_KEY_ALIAS"],
               "-storepass:env", prefix + "_STORE_PASSWORD", "-file", cert], env=env, capture=True)
        hashes.append(s.sha(cert))
    if original and hashes[0] != LEGACY_SHA:
        raise RuntimeError("Original key does not match published APKs")
    lineage = directory / "lineage.bin"
    s.run([java, "-jar", jar, "rotate", "--out", lineage,
           "--old-signer", "--ks", credentials["ZA_OLD_KEYSTORE"], "--ks-key-alias", "legacy",
           "--ks-pass", "env:ZA_OLD_STORE_PASSWORD", "--key-pass", "env:ZA_OLD_KEY_PASSWORD",
           "--set-installed-data", "true", "--set-shared-uid", "false", "--set-permission", "true",
           "--set-rollback", "false", "--set-auth", "false",
           "--new-signer", "--ks", credentials["ZA_KEYSTORE"], "--ks-key-alias", "za-music-release",
           "--ks-pass", "env:ZA_STORE_PASSWORD", "--key-pass", "env:ZA_KEY_PASSWORD"], env=env, capture=True)
    policy = dict(applicationId="app.musicplayer.android", minSdk=28, rotationMinSdk=33,
                  oldSignerSha256=hashes[0], newSignerSha256=hashes[1], lineageSha256=s.sha(lineage))
    (directory / "policy.json").write_text(json.dumps(policy, indent=2) + "\n")
    return credentials, policy


def initialize():
    home = vault()
    if (home / "credentials.dpapi").exists() or (home / "formal.p12").exists() or s.POLICY.exists():
        raise RuntimeError("Signing identity already exists; never regenerate an existing release key")
    protect_directory(home)
    original = Path(os.environ["USERPROFILE"]) / ".android/debug.keystore"
    credentials, policy = create(home, original)
    (home / "credentials.dpapi").write_bytes(dpapi(json.dumps(credentials).encode()))
    destination = s.POLICY.parent
    destination.mkdir(parents=True, exist_ok=True)
    for name in ["policy.json", "lineage.bin", "formal-cert.der", "legacy-cert.der"]:
        shutil.copyfile(home / name, destination / name)
    backup = Path(os.environ["USERPROFILE"]) / "ZA-Music-Signing-Backup"
    recovery = Path(os.environ["USERPROFILE"]) / ".za-music-signing-recovery"
    protect_directory(backup)
    protect_directory(recovery)
    # Portable key passwords are deliberately separate from the encrypted-key archive.
    recovery_file = recovery / "passwords.json"
    recovery_file.write_text(json.dumps(credentials, indent=2) + "\n")
    readme = "Encrypted PKCS12 key backup. Keep this ZIP offline. Password recovery file is separate; never publish it.\nRestore both keys and their passwords; adjust keystore paths on a new machine. Keep lineage.bin unchanged.\n"
    (backup / "README.txt").write_text(readme)
    with zipfile.ZipFile(backup / "ZA-Music-signing-keys.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for name in ["formal.p12", "legacy.p12", "lineage.bin", "policy.json", "formal-cert.der", "legacy-cert.der"]:
            archive.write(home / name, name)
        archive.writestr("README.txt", readme)
    # Verify both encrypted stores from the backup are byte-identical and DPAPI round-trips.
    with zipfile.ZipFile(backup / "ZA-Music-signing-keys.zip") as archive:
        for name in ["formal.p12", "legacy.p12"]:
            if archive.read(name) != (home / name).read_bytes():
                raise RuntimeError("Signing key backup mismatch")
    if load() != credentials:
        raise RuntimeError("DPAPI credential verification failed")
    print("FORMAL CERTIFICATE SHA256:", policy["newSignerSha256"])
    print("Encrypted key backup:", backup / "ZA-Music-signing-keys.zip")
    print("Separate password recovery file:", recovery_file)


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2 or sys.argv[1] != "init":
            raise RuntimeError("Usage: python scripts/signing_vault.py init (once, locally)")
        initialize()
    except Exception as error:
        print("VAULT FAILED:", error, file=sys.stderr)
        sys.exit(1)
