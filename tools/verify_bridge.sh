#!/usr/bin/env bash
#
# Expert Mode / System Bridge acceptance check.
#
# The point of the System Bridge is that key detection survives the app being killed, so the
# only meaningful verification is done on a real device with a real physical key press. Two of
# the steps need a human (swiping the app away, pressing the button); this script performs and
# reports everything around them.
#
# Usage:  tools/verify_bridge.sh [adb-serial]
# For side-by-side phone tests, set KEYSNAP_TEST_PACKAGE to the .dev package.
# KEYSNAP_KEY_WAIT_SECONDS extends the physical-key window (default: 20 seconds).
#
set -u

SERIAL="${1:-}"
if [ -n "$SERIAL" ]; then ADB=(adb -s "$SERIAL"); else ADB=(adb); fi

PKG="${KEYSNAP_TEST_PACKAGE:-io.github.nicholasarda.keysnap}"
KEY_WAIT_SECONDS="${KEYSNAP_KEY_WAIT_SECONDS:-20}"
case "$KEY_WAIT_SECONDS" in
  ''|*[!0-9]*) echo "KEYSNAP_KEY_WAIT_SECONDS must be a positive integer." >&2; exit 1 ;;
esac
if [ "$KEY_WAIT_SECONDS" -lt 1 ] || [ "$KEY_WAIT_SECONDS" -gt 180 ]; then
  echo "KEYSNAP_KEY_WAIT_SECONDS must be between 1 and 180." >&2
  exit 1
fi
BASE=/data/local/tmp/ardamapper
NICE=arda_bridge

pass() { printf '  [PASS] %s\n' "$1"; }
fail() { printf '  [FAIL] %s\n' "$1"; FAILED=1; }
info() { printf '  ....   %s\n' "$1"; }
FAILED=0

bridge_pid() { "${ADB[@]}" shell "pidof $NICE" 2>/dev/null | tr -d '\r\n'; }

echo "== 0. Device =="
MODEL=$("${ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n')
if [ -z "$MODEL" ]; then
  echo "  No device. Enable Wireless Debugging and 'adb connect <ip>:<port>' first." >&2
  exit 1
fi
info "model=$MODEL sdk=$("${ADB[@]}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r\n')"

echo "== 1. Expert Mode started (bridge process exists) =="
PID=$(bridge_pid)
if [ -n "$PID" ]; then
  pass "$NICE running, pid=$PID"
  PPID_VAL=$("${ADB[@]}" shell "ps -p $PID -o PPID=" 2>/dev/null | tr -d ' \r\n')
  UID_VAL=$("${ADB[@]}" shell "ps -p $PID -o USER=" 2>/dev/null | tr -d ' \r\n')
  info "ppid=$PPID_VAL user=$UID_VAL (ppid should be 1: detached from the ADB session)"
  [ "$PPID_VAL" = "1" ] && pass "reparented to init" || fail "not reparented (ppid=$PPID_VAL)"
  "${ADB[@]}" shell "ps -A -o PID,PPID,NAME | grep -w $PID" 2>/dev/null | sed 's/^/         /'
else
  fail "$NICE is not running. Start Expert Mode in the app first."
  echo "  Bridge log:"; "${ADB[@]}" shell "tail -20 $BASE/bridge.log" 2>/dev/null | sed 's/^/         /'
  exit 1
fi

echo "== 2. Swipe the app away from recents now =="
read -r -p "  Press Enter once you have swiped it away... " _

echo "== 3. App process killed (this is expected, do not fight it) =="
APP_PID=$("${ADB[@]}" shell "pidof $PKG" 2>/dev/null | tr -d '\r\n')
if [ -z "$APP_PID" ]; then
  pass "app process is gone"
else
  info "app process still alive (pid=$APP_PID); the bridge check below is what matters"
fi
"${ADB[@]}" logcat -d -t 400 2>/dev/null \
  | grep -iE "Killing .*$PKG|am_kill.*$PKG|$PKG.*(died|signal 9)" | tail -3 | sed 's/^/         /'

echo "== 4. Bridge still alive after the app died =="
sleep 2
PID_AFTER=$(bridge_pid)
if [ -n "$PID_AFTER" ] && [ "$PID_AFTER" = "$PID" ]; then
  pass "$NICE still running with the same pid=$PID_AFTER"
elif [ -n "$PID_AFTER" ]; then
  fail "pid changed ($PID -> $PID_AFTER): the bridge restarted instead of surviving"
else
  fail "$NICE died with the app - the bridge is NOT independent"
  "${ADB[@]}" shell "tail -20 $BASE/bridge.log" 2>/dev/null | sed 's/^/         /'
  exit 1
fi

echo "== 5. Press your mapped physical key now =="
echo "  Watching the bridge log for a trigger (${KEY_WAIT_SECONDS}s)..."
BEFORE=$("${ADB[@]}" shell "grep -c Triggered $BASE/bridge.log" 2>/dev/null | tr -d ' \r\n')
BEFORE=${BEFORE:-0}
for _ in $(seq 1 "$KEY_WAIT_SECONDS"); do
  sleep 1
  NOW=$("${ADB[@]}" shell "grep -c Triggered $BASE/bridge.log" 2>/dev/null | tr -d ' \r\n')
  NOW=${NOW:-0}
  if [ "$NOW" -gt "$BEFORE" ] 2>/dev/null; then
    pass "bridge matched and executed a script while the app was dead"
    "${ADB[@]}" shell "grep Triggered $BASE/bridge.log | tail -3" 2>/dev/null | sed 's/^/         /'
    break
  fi
done
if [ "${NOW:-0}" -le "$BEFORE" ] 2>/dev/null; then
  fail "no trigger recorded - check the mapping and the log below"
  "${ADB[@]}" shell "tail -25 $BASE/bridge.log" 2>/dev/null | sed 's/^/         /'
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "RESULT: all checks passed."
else
  echo "RESULT: at least one check failed (see [FAIL] above)."
fi
exit "$FAILED"
