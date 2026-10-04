"""Android 13+ key rotation. Private material never belongs to this repository."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "android-app/signing/policy.json"


def run(args, *, env=None, capture=False):
    result = subprocess.run([str(x) for x in args], env=env, cwd=ROOT,
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.PIPE if capture else None)
    if result.returncode:
        if capture:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace")[:3000])
        raise RuntimeError("Build/signing command failed")
    return result.stdout.decode("utf-8", errors="replace") if capture else None


def tools():
    local = ROOT / ".tools/android-sdk"
    sdk = local if local.exists() else Path(os.environ["ANDROID_HOME"])
    jdk = ROOT / ".tools/jdk-17-android"
    if not jdk.exists():
        jdk = Path(os.environ["JAVA_HOME"])
    suffix = ".exe" if os.name == "nt" else ""
    return jdk / ("bin/java" + suffix), jdk / ("bin/javac" + suffix), sdk / "build-tools/35.0.0/lib/apksigner.jar"


def sha(path):
    with Path(path).open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256")
    return digest.hexdigest()


def version():
    return dict(line.strip().split("=", 1) for line in (ROOT / "version.properties").read_text().splitlines() if "=" in line)


def material():
    """Environment for disposable CI keys, or current-user DPAPI material locally."""
    names = ("ZA_KEYSTORE", "ZA_STORE_PASSWORD", "ZA_KEY_ALIAS", "ZA_KEY_PASSWORD",
             "ZA_OLD_KEYSTORE", "ZA_OLD_STORE_PASSWORD", "ZA_OLD_KEY_ALIAS", "ZA_OLD_KEY_PASSWORD")
    present = [bool(os.environ.get(n)) for n in names]
    if any(present):
        if not all(present):
            raise RuntimeError("All old/new signing variables must be supplied")
        return {n: os.environ[n] for n in names}
    if os.name != "nt":
        raise RuntimeError("Explicit disposable signing credentials required on CI")
    import signing_vault
    return signing_vault.load()


def inspect(apk):
    java, javac, jar = tools()
    output = ROOT / ".tools/signing-inspector"
    output.mkdir(parents=True, exist_ok=True)
    run([javac, "-cp", jar, "-d", output, ROOT / "scripts/ApkSigningInspector.java"], capture=True)
    return json.loads(run([java, "-cp", str(jar) + os.pathsep + str(output), "ApkSigningInspector", apk], capture=True))


def verify(apk, policy=POLICY, test=False, qa=False):
    expected = json.loads(Path(policy).read_text())
    if expected["rotationMinSdk"] != 33 or expected["minSdk"] != 28:
        raise RuntimeError("Unsupported rotation boundary")
    facts = inspect(apk)
    if facts["lineage"] != [expected["oldSignerSha256"], expected["newSignerSha256"]]:
        raise RuntimeError("Wrong signing lineage")
    for api, signer in facts["apiSigners"].items():
        required = expected["newSignerSha256"] if int(api) >= 33 else expected["oldSignerSha256"]
        if signer != required:
            raise RuntimeError("Wrong signer selected for API " + api)
    if sha(Path(policy).with_name("lineage.bin")) != expected["lineageSha256"]:
        raise RuntimeError("Public lineage file hash mismatch")
    # apkanalyzer reads the binary manifest, so stale Gradle metadata is insufficient.
    java, _, jar = tools()
    sdk = jar.parents[3]
    analyzer = sdk / ("cmdline-tools/latest/bin/apkanalyzer.bat" if os.name == "nt" else "cmdline-tools/latest/bin/apkanalyzer")
    if not analyzer.exists():
        candidates = sorted((sdk / "cmdline-tools").glob("*/bin/apkanalyzer*"))
        analyzer = next(p for p in candidates if p.suffix == (".bat" if os.name == "nt" else ""))
    import xml.etree.ElementTree as ET
    classpath = analyzer.parent.parent / "lib/apkanalyzer-classpath.jar"
    xml = run([java, "-Dfile.encoding=UTF-8", "-Dcom.android.sdklib.toolsdir=" + str(analyzer.parent.parent), "-cp", classpath, "com.android.tools.apk.analyzer.ApkAnalyzerCli", "manifest", "print", apk], capture=True)
    manifest = ET.fromstring(xml)
    android = "{http://schemas.android.com/apk/res/android}"
    package = manifest.attrib["package"]
    debug = manifest.find("application").get(android + "debuggable", "false")
    app_version = manifest.get(android + "versionName", "")
    code = int(manifest.get(android + "versionCode", "0"))
    if int(manifest.find("uses-sdk").get(android + "minSdkVersion", "0")) != 28:
        raise RuntimeError("APK minimum SDK differs from the signing policy")
    if package != expected["applicationId"] + (".test" if test else ""):
        raise RuntimeError("APK application ID mismatch")
    if not test and debug != "false":
        raise RuntimeError("Release APK must not be debuggable")
    if not test and not qa and (app_version != version()["versionName"] or code != int(version()["versionCode"])):
        raise RuntimeError("APK version mismatch")
    return dict(facts, applicationId=package, debuggable=debug == "true", versionName=app_version,
                versionCode=code, rotationMinSdk=33, lineageSha256=expected["lineageSha256"])


def sign(source, output, policy):
    java, _, jar = tools()
    env = dict(os.environ, **material())
    old = ["--ks", env["ZA_OLD_KEYSTORE"], "--ks-key-alias", env["ZA_OLD_KEY_ALIAS"],
           "--ks-pass", "env:ZA_OLD_STORE_PASSWORD", "--key-pass", "env:ZA_OLD_KEY_PASSWORD"]
    new = ["--ks", env["ZA_KEYSTORE"], "--ks-key-alias", env["ZA_KEY_ALIAS"],
           "--ks-pass", "env:ZA_STORE_PASSWORD", "--key-pass", "env:ZA_KEY_PASSWORD"]
    output.parent.mkdir(parents=True, exist_ok=True)
    run([java, "-jar", jar, "sign", "--out", output, "--lineage", Path(policy).with_name("lineage.bin"),
         "--rotation-min-sdk-version", "33", "--v1-signing-enabled", "false", "--v2-signing-enabled", "true",
         "--v3-signing-enabled", "true", "--v4-signing-enabled", "false", *old, "--next-signer", *new, source], env=env, capture=True)


def package(args):
    env = dict(os.environ, **material())
    vcs = lambda *x: run(["git", *x], capture=True).strip()
    before, dirty = vcs("rev-parse", "HEAD"), bool(vcs("status", "--porcelain"))
    policy = Path(args.policy).resolve()
    if policy != POLICY.resolve() and not args.qa:
        raise RuntimeError("Disposable signing policy requires --qa")
    tasks = [":shared:test", "assembleRelease", "assembleReleaseAndroidTest", "lintRelease", "-PandroidTestBuildType=release"]
    if args.qa_code is not None:
        if not args.qa:
            raise RuntimeError("Version code overrides are QA-only")
        tasks.append("-PzaQaVersionCode=" + str(args.qa_code))
    if os.name == "nt":
        # Existing PowerShell helper handles non-ASCII Gradle classpaths and restores environment.
        command = ". (Join-Path $PWD 'scripts/build-common.ps1'); Invoke-ZaGradle $PWD 'android-app' @(" + ",".join("'" + t + "'" for t in tasks) + ")"
        run(["pwsh", "-NoProfile", "-Command", command], env=env)
    else:
        run([ROOT / "android-app/gradlew", "-p", ROOT / "android-app", *tasks, "--no-daemon", "--console=plain"], env=env)
    app = ROOT / "android-app/app/build/outputs/apk/release/app-release.apk"
    tests = ROOT / "android-app/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk"
    output = Path(args.output).resolve() if args.output else ROOT / ("android-app/dist/ZA-Music-Android-" + version()["versionName"] + ".apk")
    if output == app.resolve():
        raise RuntimeError("Keep the Gradle intermediate separate from the distribution APK")
    sign(app, output, policy)
    facts = verify(output, policy, qa=args.qa)
    test_output = output.with_name(output.stem + "-tests.apk")
    sign(tests, test_output, policy)
    verify(test_output, policy, test=True, qa=True)
    if before != vcs("rev-parse", "HEAD") or (not dirty and vcs("status", "--porcelain")):
        raise RuntimeError("Source changed during build")
    record = dict(version=version()["versionName"], versionCode=facts["versionCode"], sourceCommit=before,
                  sourceDirty=dirty, platform="android-release", artifact=output.name, sha256=sha(output),
                  signing=facts, qa=args.qa, gradle="8.11.1", jdk="",
                  builtAtUtc=datetime.now(timezone.utc).isoformat())
    # java -version writes stderr; this field is supplied from the runtime release file instead.
    runtime = dict(line.split("=", 1) for line in (tools()[0].parents[1] / "release").read_text().splitlines() if "=" in line)
    record["jdk"] = runtime["JAVA_RUNTIME_VERSION"].strip('"')
    output.with_suffix(output.suffix + ".build.json").write_text(json.dumps(record, indent=2) + "\n")
    print("SIGNED AND VERIFIED:", output)
    print("SIGNERS: API 28-32 legacy; API 33+ formal. TEST APK:", test_output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    p = commands.add_parser("verify")
    p.add_argument("--apk", required=True)
    p.add_argument("--policy", default=str(POLICY))
    p.add_argument("--test", action="store_true")
    p.add_argument("--qa", action="store_true")
    p = commands.add_parser("package")
    p.add_argument("--policy", default=str(POLICY))
    p.add_argument("--output")
    p.add_argument("--qa", action="store_true")
    p.add_argument("--qa-code", type=int)
    args = parser.parse_args()
    try:
        if args.command == "verify":
            print(json.dumps(verify(Path(args.apk).resolve(), args.policy, args.test, args.qa)))
        else:
            package(args)
    except Exception as error:
        print("SIGNING FAILED:", error, file=sys.stderr)
        sys.exit(1)
