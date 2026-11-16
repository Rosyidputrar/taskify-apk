# Vektor uji silang Sandi (webapp ⇄ Android)

Kriptografi Sandi di aplikasi Android (`core/domain/.../sandi/SandiCrypto.kt`) harus byte-kompatibel
dengan `src/features/sandi/crypto.ts` di webapp. Kompatibilitas diuji dua arah memakai kode webapp
**apa adanya** (dijalankan dengan Node ≥ 22, tanpa dependensi npm):

```bash
W=../taskify-webapp/src            # lokasi webapp
mkdir -p /tmp/xv && cp $W/features/sandi/crypto.ts $W/features/sandi/totp.ts $W/lib/hash.ts tools/sandi-vectors/*.ts /tmp/xv/
sed -i "s#from '../../lib/hash'#from './hash.ts'#; s#from './totp'#from './totp.ts'#" /tmp/xv/crypto.ts

# 1. webapp → Android: buat vektor, lalu jalankan tes Kotlin yang membukanya
(cd /tmp/xv && node --experimental-strip-types gen.ts) > core/domain/src/test/resources/sandi/webapp-vectors.json
./gradlew :core:domain:test --tests '*SandiCryptoTest*'

# 2. Android → webapp: tes di atas menulis core/domain/build/kotlin-vectors.json
cp core/domain/build/kotlin-vectors.json /tmp/xv/ && (cd /tmp/xv && node --experimental-strip-types verify.ts)
```

`verify.ts` gagal (exception) bila ada yang tidak bisa dibuka. Jalankan ulang setiap kali
`crypto.ts` atau `SandiCrypto.kt` berubah.
