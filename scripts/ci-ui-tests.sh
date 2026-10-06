#!/usr/bin/env bash
# Runs the instrumented tests on the emulator and collects text logs (the only output readable remotely).
set +e
adb logcat -c
./gradlew --console=plain :app:connectedDebugAndroidTest 2>&1 | tee gradle-ui.log
status=${PIPESTATUS[0]}
adb logcat -d -s ASCII:I TALKS_TEST:I TALKS_TREE_HOME:D TALKS_TREE_TABLET:D TALKS_TREE_PREPARE:D TALKS_TREE_LIVE:D TALKS_TREE_FAIL:D AndroidRuntime:E > ui-log.txt
echo "$status" > ui-status.txt
exit 0
