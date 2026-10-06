#!/usr/bin/env bash
# Runs the instrumented tests on the emulator and collects text logs (the only output readable remotely).
set +e
adb logcat -c
./gradlew --console=plain :app:connectedDebugAndroidTest 2>&1 | tee gradle-ui.log
status=${PIPESTATUS[0]}
adb logcat -d -s ASCII:I TALKS_TEST:I TALKS_TREE_HOME:I TALKS_TREE_TABLET:I TALKS_TREE_PREPARE:I TALKS_TREE_LIVE:I AndroidRuntime:E > ui-log.txt
echo "$status" > ui-status.txt
exit 0
