# core/youtubei — QuickJS + youtubei.js (adaptasi ArchiveTune)

Modul ini adalah adaptasi modul `morideobfuscator` ArchiveTune (GPL-3.0).
Enam berkas Kotlin di `src/main/kotlin/.../youtubei/` disalin dari ArchiveTune
(commit `c7f0e24`) dengan hanya mengganti nama paket dan dua nama berkas debug
internal (`archivetune-*.js` → `taskify-*.js`). Aset di `src/main/assets/youtubei/`
disalin verbatim:

- `youtubei.bundle.js` (1.143.417 byte, md5 `499d39d61904404ad5296a29dca3d534` — rebuild 20 Nov 2026 untuk C S-2,
  field `client` di hasil resolve; sebelumnya 1.143.408 byte, md5 `c2342b8a822bdbb6d77e9e51b7846848`)
- `runtime-polyfills.js` (4.208 byte, md5 `46d1ba4559205cd117f183bf1d829fbc`)

Sumber JavaScript bundle ada di `javascript/` (disalin dari
`morideobfuscator/javascript/` ArchiveTune): `package.json` mem-pin
`youtubei.js 18.0.0` dan `whatwg-url 14.2.0`; devDependency `esbuild 0.28.2`.

## Membangun ulang bundle

```sh
cd javascript
npm install           # versi exact pin di package.json (tanpa lockfile)
node scripts/bundle.mjs
```

Skrip menulis ke `../src/main/assets/youtubei/youtubei.bundle.js` relatif
terhadap direktori `javascript/` (sama seperti di ArchiveTune). Bandingkan
hash hasil build dengan md5 di atas; bila berbeda, jangan mengganti bundle
tanpa menyelidiki penyebabnya (versi esbuild/youtubei.js yang berbeda,
perubahan sumber, atau nondeterminisme build).

Hasil rebuild 2026-11-18 (log `~/workspace/music-full-port-archivetune/logs/bundle-rebuild.log`):
`npm install` + `node scripts/bundle.mjs` dari `package-lock.json` yang sama
(esbuild 0.28.2, youtubei.js 18.0.0) menghasilkan berkas ukuran SAMA PERSIS
(1.143.408 byte) tapi md5 `97605f660e99ea4ef003bd9d4a7b1843` — selisih 1.040 byte
dalam 418 klaster, SEMUANYA penamaan identifier hasil minify esbuild
(mis. `BH` → `IH`, `$$` → `K$`) tanpa perubahan struktur kode. Smoke test
Node pada kedua bundle: keduanya memuat dan mengekspos API identik
(`version: 18.0.0`, kunci `prepare, reset, resolvePrepared, version`).
Karena itu bundle AT tetap dipakai verbatim di repo; rebuild membuktikan
sumber JS mereproduksi bundle yang setara secara fungsional.

Catatan: nama global `ArchiveTuneYoutubei` di dalam bundle adalah API JS
youtubei.js (`index.js`) dan sengaja tidak diganti; menggantinya akan
memutuskan kontrak antara bundle dan `YoutubeiQuickJsWorker`.
