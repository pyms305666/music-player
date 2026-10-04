"""Disposable CI rotation builds and upgrade checks; never resets the main app."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from types import SimpleNamespace
import android_signing_loader
import signing_vault

s = android_signing_loader.module
WORK = s.ROOT / ".tools/rotation-ci"


def prepare():
    WORK.mkdir(parents=True, exist_ok=True)
    credentials, _ = signing_vault.create(WORK / "keys")
    (WORK / "credentials.json").write_text(json.dumps(credentials))
    env = dict(os.environ, **credentials)
    final_code = int(s.version()["versionCode"])
    tasks = ["assembleDebug", "assembleDebugAndroidTest", "-PzaQaVersionCode=" + str(final_code - 2)]
    s.run([s.ROOT / "android-app/gradlew", "-p", s.ROOT / "android-app", *tasks, "--no-daemon", "--console=plain"], env=env)
    for source, name in [("debug/app-debug.apk", "baseline.apk"), ("androidTest/debug/app-debug-androidTest.apk", "baseline-tests.apk")]:
        shutil.copyfile(s.ROOT / "android-app/app/build/outputs/apk" / source, WORK / name)
    os.environ.update(credentials)
    for code, name in [(final_code - 1, "candidate.apk"), (final_code, "final.apk")]:
        s.package(SimpleNamespace(policy=str(WORK / "keys/policy.json"), output=str(WORK / name), qa=True, qa_code=code))
    # A mismatched policy must reject a valid APK; it cannot merely trust metadata.
    bad = json.loads((WORK / "keys/policy.json").read_text())
    bad["newSignerSha256"] = "0" * 64
    (WORK / "keys/bad-policy.json").write_text(json.dumps(bad))
    try:
        s.verify(WORK / "final.apk", WORK / "keys/bad-policy.json", qa=True)
    except RuntimeError:
        print("Wrong certificate policy correctly rejected")
    else:
        raise RuntimeError("Wrong certificate policy accepted")


def exercise(args):
    _, _, jar = s.tools()
    adb = jar.parents[3] / ("platform-tools/adb.exe" if os.name == "nt" else "platform-tools/adb")
    serial = args.serial
    if not serial:
        devices = s.run([adb, "devices"], capture=True)
        serials = [line.split()[0] for line in devices.splitlines() if line.endswith("\tdevice")]
        if len(serials) != 1:
            raise RuntimeError("Exactly one device, or --serial, required")
        serial = serials[0]
    base = [adb, "-s", serial]
    def command(*items):
        return s.run([*base, *items], capture=True)
    def install(path):
        command("shell", "input", "keyevent", "224")
        command("shell", "wm", "dismiss-keyguard")
        command("shell", "input", "keyevent", "82")
        result = command("install", "-r", Path(path).resolve())
        if "Success" not in result:
            raise RuntimeError("APK installation failed")
    policy = json.loads(Path(args.policy).read_text())
    def instrument(method, phase="", seed=False):
        options = ["-e", "class", "app.musicplayer.android." + method]
        if phase:
            options += ["-e", "migrationPhase", phase, "-e", "expectedOldSha", policy["oldSignerSha256"],
                        "-e", "expectedNewSha", policy["newSignerSha256"]]
        if seed:
            options += ["-e", "seedFixture", "true"]
        output = command("shell", "am", "instrument", "-w", *options,
                         "app.musicplayer.android.test/androidx.test.runner.AndroidJUnitRunner")
        print(output)
        if not re.search(r"OK \(\d+ tests?\)", output) or "FAILURES" in output:
            raise RuntimeError("Instrumentation regression failed")
    if args.baseline:
        install(args.baseline)
    command("shell", "am", "force-stop", "app.musicplayer.android")
    if not args.resume:
        install(args.baseline_tests)
        instrument("SigningMigrationTest#recordBeforeUpgrade", "record", bool(args.baseline))
        command("uninstall", "app.musicplayer.android.test")
    for app in [args.candidate, args.final]:
        if not app:
            continue
        s.verify(Path(app).resolve(), args.policy, qa=args.qa)
        install(app)
        test = Path(app).with_name(Path(app).stem + "-tests.apk")
        install(test)
        instrument("SigningMigrationTest#verifyAfterUpgrade", "verify")
        instrument("PlaybackServiceTest")
        command("shell", "am", "force-stop", "app.musicplayer.android")
        # Validate again after playing, then carry the original snapshot into the next upgrade.
        instrument("SigningMigrationTest#verifyAfterUpgrade", "verify")
        command("uninstall", "app.musicplayer.android.test")
    if not args.keep_snapshot:
        # Cleanup with the matching test certificate, while keeping the main app.
        last = Path(args.final or args.candidate)
        install(last.with_name(last.stem + "-tests.apk"))
        instrument("SigningMigrationTest#removeTemporarySnapshot", "cleanup")
        command("uninstall", "app.musicplayer.android.test")
    command("shell", "input", "keyevent", "224")
    command("shell", "wm", "dismiss-keyguard")
    command("shell", "input", "keyevent", "82")
    command("shell", "am", "start", "-n", "app.musicplayer.android/.MainActivity")
    print("ROTATION UPGRADE AND DATA/PERMISSION/PLAYBACK CHECKS PASSED:", serial)
    if args.clean_install:
        if not serial.startswith("emulator-"):
            raise RuntimeError("Clean install checks are restricted to disposable emulators")
        command("uninstall", "app.musicplayer.android")
        last = Path(args.final or args.candidate)
        install(last)
        install(last.with_name(last.stem + "-tests.apk"))
        instrument("SigningMigrationTest#recordBeforeUpgrade", "record", True)
        instrument("SigningMigrationTest#verifyAfterUpgrade", "verify")
        instrument("PlaybackServiceTest")
        instrument("SigningMigrationTest#removeTemporarySnapshot", "cleanup")
        command("uninstall", "app.musicplayer.android.test")
        print("CLEAN INSTALL CHECKS PASSED:", serial)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("prepare")
    p = commands.add_parser("run")
    p.add_argument("--serial")
    p.add_argument("--policy", default=str(WORK / "keys/policy.json"))
    p.add_argument("--baseline", default=str(WORK / "baseline.apk"))
    p.add_argument("--baseline-tests", default=str(WORK / "baseline-tests.apk"))
    p.add_argument("--candidate", default=str(WORK / "candidate.apk"))
    p.add_argument("--final", default=str(WORK / "final.apk"))
    p.add_argument("--qa", action="store_true")
    p.add_argument("--resume", action="store_true", help="Use the existing pre-upgrade snapshot")
    p.add_argument("--keep-snapshot", action="store_true", help="Keep the snapshot for the next upgrade")
    p.add_argument("--clean-install", action="store_true", help="Also check fresh install on a disposable emulator")
    args = parser.parse_args()
    try:
        if args.command == "prepare":
            prepare()
        else:
            exercise(args)
    except Exception as error:
        print("ROTATION QA FAILED:", error, file=sys.stderr)
        sys.exit(1)
