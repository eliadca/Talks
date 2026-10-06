#!/usr/bin/env bash
# Runs the instrumented tests on the emulator and collects text logs (the only output readable remotely).
set +e

# A slow CI emulator can pop up "isn't responding" dialogs that steal the window focus from the app under test.
adb shell settings put global hide_error_dialogs 1
# The one-time "Viewing full screen" hint of immersive mode would take the focus from the app.
adb shell settings put secure immersive_mode_confirmations confirmed
# The "copied" preview that Android 13+ shows after a copy would sit over the app during the tests.
adb shell device_config put systemui clipboard_overlay_enabled false || true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null
for _ in $(seq 1 30); do
  if adb shell dumpsys window | grep -q "mCurrentFocus=Window"; then break; fi
  sleep 2
done
sleep 10
adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | head -3 > ui-system.txt

# Screenshots travel through the log, so it needs room.
adb logcat -G 32M
adb logcat -c
./gradlew --console=plain :app:connectedDebugAndroidTest 2>&1 | tee gradle-ui.log
status=${PIPESTATUS[0]}
adb logcat -d -s ASCII:I TALKS_TEST:I TALKS_TREE_HOME:D TALKS_TREE_TABLET:D TALKS_TREE_PREPARE:D TALKS_TREE_LIVE:D TALKS_TREE_FAIL:D AndroidRuntime:E > ui-log.txt
adb logcat -d | grep -E "ANR in|isn't responding|FATAL EXCEPTION|has died|am_crash|am_anr|Force finishing" | head -20 >> ui-system.txt
adb logcat -d -s SHOT:I | sed -E 's/^.* I SHOT +: //' > shots.txt
echo "$status" > ui-status.txt
exit 0
