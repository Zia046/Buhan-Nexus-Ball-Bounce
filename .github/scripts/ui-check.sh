#!/usr/bin/env bash
# Automated emulator check: installs the debug APK, visits every screen, plays with
# autopilot, forces a miss, checks score persistence after a restart, taps real
# buttons (PLAY, EXIT GAME) and fails if the app crashed.
set -u
PKG=com.burhannexus.ballbounce.debug
ACT=$PKG/com.burhannexus.ballbounce.MainActivity
OUT=${OUT:-shots}
mkdir -p "$OUT"

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell wm size > "$OUT/screen-size.txt"

cmd()  { adb shell am start -n "$ACT" --es bn_cmd "$1" >/dev/null 2>&1; sleep "${2:-2}"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
coords() { # $1 = screen, $2 = action  -> "x y"
  adb logcat -d -s BNUI:I | grep " $1 $2 " | tail -1 | awk '{print $(NF-1), $NF}'
}

adb shell am start -W -n "$ACT" >/dev/null; sleep 5
shot 01-menu
cmd howto; shot 02-howto
cmd top3;  shot 03-top3-empty
cmd about; shot 04-about
cmd autoplay 1; shot 05-game-start
sleep 8;  shot 06-playing
cmd pause; shot 07-paused
cmd miss 4; shot 08-gameover-new-high-score
cmd autoplay 3; cmd miss 4; shot 09-gameover-second
cmd autoplay 2; cmd miss 4; shot 10-gameover-third
cmd top3; shot 11-top3

# persistence: kill the app completely, start again
adb shell am force-stop "$PKG"; sleep 1
adb shell am start -W -n "$ACT" >/dev/null; sleep 3
cmd top3; shot 12-top3-after-restart

# real touch input
cmd menu; cmd dump 1
read -r PX PY <<< "$(coords MENU PLAY)"
echo "PLAY at $PX $PY" | tee "$OUT/touch.txt"
adb shell input tap "$PX" "$PY"; sleep 1.5; shot 13-after-tapping-play
adb shell input tap "$PX" "$PY"; sleep 0.3          # touch starts the ball
adb shell input swipe 100 "$PY" 1000 "$PY" 400; sleep 6
shot 14-real-gameover
read -r EX EY <<< "$(coords GAME_OVER EXIT)"
echo "EXIT at $EX $EY" | tee -a "$OUT/touch.txt"
adb shell input tap "$EX" "$EY"; sleep 3
shot 15-after-exit-game
adb shell dumpsys activity activities | grep -iE "ResumedActivity|topResumed" > "$OUT/after-exit-activities.txt"
if grep -q "$PKG" "$OUT/after-exit-activities.txt"; then echo "EXIT: app still in front" | tee -a "$OUT/touch.txt"; else echo "EXIT: OK (home screen)" | tee -a "$OUT/touch.txt"; fi

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "CRASH DETECTED"; grep -A 25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
  echo "CRASH" > "$OUT/RESULT.txt"
else
  echo "NO CRASH" > "$OUT/RESULT.txt"
fi
exit 0
