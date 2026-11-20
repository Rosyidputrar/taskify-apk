# Rute navigasi type-safe memakai kotlinx.serialization; aturan serializer dibawa library-nya.
# Tambahkan aturan khusus modul di sini bila R8 menghapus kelas yang dipakai lewat refleksi.

# Widget: Glance membuat ActionCallback lewat nama kelas (refleksi).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager (Room 2.6) membuat WorkDatabase_Impl lewat refleksi (Class.getDeclaredConstructor()).
# Aturan bawaan Room hanya "-keep class * extends RoomDatabase" — di R8 full mode (bawaan AGP 9)
# constructor-nya tetap dibuang → crash saat startup (NoSuchMethodException WorkDatabase_Impl.<init>).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Asisten AI: markdown-renderer-m3 + highlights dirender di layar chat. Belum ada bukti
# penghapusan R8, tapi kedua pustaka ini ditarik ke R8 full mode tanpa aturan bawaan yang
# eksplisit — keep defensif supaya layar Asisten AI tidak pecah di build rilis.
-keep class com.mikepenz.markdown.** { *; }
-keep class dev.snipme.highlights.** { *; }
