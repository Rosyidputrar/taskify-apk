# Rute navigasi type-safe memakai kotlinx.serialization; aturan serializer dibawa library-nya.
# Tambahkan aturan khusus modul di sini bila R8 menghapus kelas yang dipakai lewat refleksi.

# Widget: Glance membuat ActionCallback lewat nama kelas (refleksi).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
