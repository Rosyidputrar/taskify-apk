#!/usr/bin/env bash
# Uji asap APK (terutama build RILIS ber-R8): pasang ke emulator/HP yang terhubung, buka lewat
# beberapa pintu masuk, lalu pastikan prosesnya masih hidup dan tidak ada FATAL EXCEPTION.
#
#   tools/smoke-test.sh app/build/outputs/apk/release/app-release.apk [com.rosyidputrar.taskify]
#
# Log lengkap disimpan di build/smoke/logcat.txt.
set -euo pipefail

APK="${1:?path APK}"
PKG="${2:-com.rosyidputrar.taskify}"
ADB="${ADB:-adb}"
OUT="build/smoke"
mkdir -p "$OUT"

fail() {
  echo "SMOKE GAGAL: $*" >&2
  "$ADB" logcat -d > "$OUT/logcat.txt" || true
  grep -n -A30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 >&2 || true
  exit 1
}

alive() { "$ADB" shell pidof "$PKG" > /dev/null 2>&1; }

crashed() {
  "$ADB" logcat -d -b crash 2>/dev/null | grep -q "Process: $PKG" && return 0
  "$ADB" logcat -d 2>/dev/null | grep -A3 "FATAL EXCEPTION" | grep -q "Process: $PKG"
}

# Tunggu sampai proses sempat jalan, lalu pastikan tidak crash selama [detik].
settle() {
  local seconds="$1" what="$2"
  sleep "$seconds"
  crashed && fail "crash setelah $what"
  alive || fail "proses mati setelah $what"
  echo "ok: $what"
}

"$ADB" wait-for-device
"$ADB" uninstall "$PKG" > /dev/null 2>&1 || true
"$ADB" install -r "$APK" > /dev/null || fail "pasang APK"
"$ADB" logcat -c || true
# Tanpa dialog izin notifikasi yang menutupi layar.
"$ADB" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true

"$ADB" shell am start -W -n "$PKG/com.rosyidputrar.taskify.MainActivity" > /dev/null || fail "membuka MainActivity"
settle 20 "cold start"

# Shortcut launcher (aksi intent sungguhan dari shortcuts.xml).
for action in CAPTURE SEARCH CALENDAR; do
  "$ADB" shell am start -W -a "com.rosyidputrar.taskify.action.$action" -n "$PKG/com.rosyidputrar.taskify.MainActivity" > /dev/null || fail "shortcut $action"
  settle 4 "shortcut $action"
done

# Bagikan teks ke Taskify (share target).
"$ADB" shell am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "uji asap" -n "$PKG/com.rosyidputrar.taskify.MainActivity" > /dev/null || fail "bagikan teks"
settle 4 "bagikan teks"

# Ke latar lalu kembali (kunci PIN, sesi Sandi, layanan latar).
"$ADB" shell input keyevent KEYCODE_HOME
sleep 3
"$ADB" shell am start -W -n "$PKG/com.rosyidputrar.taskify.MainActivity" > /dev/null || fail "kembali dari latar"
settle 4 "kembali dari latar"

# Putar layar (Activity dibuat ulang).
"$ADB" shell settings put system accelerometer_rotation 0 > /dev/null 2>&1 || true
"$ADB" shell settings put system user_rotation 1 > /dev/null 2>&1 || true
settle 5 "putar layar"
"$ADB" shell settings put system user_rotation 0 > /dev/null 2>&1 || true

"$ADB" logcat -d > "$OUT/logcat.txt" || true
"$ADB" exec-out screencap -p > "$OUT/akhir.png" 2>/dev/null || true
echo "SMOKE LULUS: $PKG tetap berjalan tanpa crash."
