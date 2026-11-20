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

# Ketuk simpul UI yang teks / content-desc-nya sama dengan $1 (uiautomator dump + python3).
# Dipakai untuk membuka layar lewat dok/daftar, tanpa koordinat hardcode.
tap_text() {
  local want="$1" what="$2"
  "$ADB" shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || fail "uiautomator dump gagal ($what)"
  "$ADB" pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1 || fail "ambil dump UI gagal ($what)"
  local xy
  xy=$(python3 - "$OUT/ui.xml" "$want" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[2]
root = ET.parse(sys.argv[1]).getroot()
parent = {c: p for p in root.iter() for c in p}
for node in root.iter("node"):
    if node.get("text") == want or node.get("content-desc") == want:
        # Naik sampai simpul yang bisa diketuk (label dok/baris daftar sendiri tidak klikabel).
        n = node
        while n is not None and n.get("clickable") != "true":
            n = parent.get(n)
        if n is None:
            continue
        m = re.findall(r"\d+", n.get("bounds", ""))
        if len(m) == 4:
            print((int(m[0]) + int(m[2])) // 2, (int(m[1]) + int(m[3])) // 2)
            break
PY
)
  [ -n "$xy" ] || fail "\"$want\" tidak ditemukan di layar ($what)"
  "$ADB" shell input tap $xy > /dev/null || fail "ketuk \"$want\" ($what)"
}

# Layar Asisten AI (R8): Ruang → Asisten AI. Instalasi baru belum punya percakapan, jadi
# yang dibuktikan di sini: layar daftar Asisten AI terbuka tanpa crash di APK R8
# (aturan keep markdown/highlights ikut teruji lewat pembukaan layar). Render satu jawaban
# markdown panjang di R8 masih butuh percakapan berisi — dicatat di status area E.
tap_text "Ruang" "buka Ruang"
settle 4 "Ruang"
tap_text "Asisten AI" "buka Asisten AI"
settle 5 "layar Asisten AI"
"$ADB" exec-out screencap -p > "$OUT/ai.png" 2>/dev/null || true
echo "ok: layar Asisten AI terbuka"

# Putar layar (Activity dibuat ulang).
"$ADB" shell settings put system accelerometer_rotation 0 > /dev/null 2>&1 || true
"$ADB" shell settings put system user_rotation 1 > /dev/null 2>&1 || true
settle 5 "putar layar"
"$ADB" shell settings put system user_rotation 0 > /dev/null 2>&1 || true

"$ADB" logcat -d > "$OUT/logcat.txt" || true
"$ADB" exec-out screencap -p > "$OUT/akhir.png" 2>/dev/null || true
echo "SMOKE LULUS: $PKG tetap berjalan tanpa crash."
