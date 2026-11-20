package com.rosyidputrar.taskify.core.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Pengaturan AI aktif (tanpa kunci API — hanya [hasKey]). */
data class AiSettings(
    val provider: AiProvider = AiProvider.OpenAiCompatible,
    val baseUrl: String = provider.defaultBaseUrl,
    val model: String = "",
    /** Daftar model terakhir yang berhasil diambil (untuk dipilih tanpa memanggil server lagi). */
    val models: List<String> = emptyList(),
    val systemPrompt: String = AiPrompt.DEFAULT,
    /** Bawaan sakelar "Akses data Taskify" untuk sesi baru. */
    val dataAccess: Boolean = false,
    val hasKey: Boolean = false,
) {
    val ready: Boolean get() = hasKey && model.isNotBlank() && baseUrl.isNotBlank()
}

/** Alamat + kunci untuk satu penyedia. */
data class AiConnection(val baseUrl: String, val apiKey: String)

/** Sumber koneksi per penyedia (dipisah supaya mesin chat bisa diuji tanpa SharedPreferences). */
fun interface AiConnector {
    fun connection(provider: AiProvider): AiConnection?
}

/** Enkripsi rahasia kecil (kunci API). */
interface SecretBox {
    fun seal(plain: ByteArray): String
    /** null = tidak bisa dibuka (kunci perangkat hilang / data rusak). */
    fun open(sealed: String): ByteArray?
    fun destroy() {}
}

/** AES-GCM dengan kunci Android Keystore (tidak bisa diekspor). Tidak ada cadangan teks biasa. */
class KeystoreSecretBox(private val alias: String = "taskify_ai_api_key") : SecretBox {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    override fun seal(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    override fun open(sealed: String): ByteArray? = runCatching {
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12)) }
        cipher.doFinal(raw, 12, raw.size - 12)
    }.getOrNull()

    override fun destroy() {
        runCatching { KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(alias) }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}

/**
 * Pengaturan AI di SharedPreferences. Base URL, model, dan kunci disimpan PER PENYEDIA, jadi
 * berpindah penyedia tidak menghapus pengaturan yang lain. Kunci API hanya tersimpan terenkripsi.
 */
class AiSettingsStore(context: Context, private val box: SecretBox) : AiConnector {
    private val prefs = context.getSharedPreferences("taskify_ai", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    private fun k(p: AiProvider, name: String) = "${p.name}_$name"

    private fun read(): AiSettings {
        val provider = prefs.getString("provider", null)?.let { n -> AiProvider.entries.firstOrNull { it.name == n } } ?: AiProvider.OpenAiCompatible
        return AiSettings(
            provider = provider,
            baseUrl = prefs.getString(k(provider, "base_url"), null) ?: provider.defaultBaseUrl,
            model = prefs.getString(k(provider, "model"), null).orEmpty(),
            models = prefs.getString(k(provider, "models"), null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            systemPrompt = prefs.getString("system_prompt", null) ?: AiPrompt.DEFAULT,
            dataAccess = prefs.getBoolean("data_access", false),
            hasKey = prefs.contains(k(provider, "key")),
        )
    }

    private fun refresh() { _settings.value = read() }

    fun setProvider(p: AiProvider) { prefs.edit().putString("provider", p.name).apply(); refresh() }

    /** @throws AiException bila URL tidak valid (bukan HTTPS, dll). */
    fun setBaseUrl(raw: String) {
        val p = _settings.value.provider
        val url = AiEndpoint.normalize(raw, p)
        if (url != _settings.value.baseUrl) {
            // Server lain → daftar model lama tidak berlaku.
            prefs.edit().putString(k(p, "base_url"), url).remove(k(p, "models")).apply()
        }
        refresh()
    }

    fun setModel(model: String) { prefs.edit().putString(k(_settings.value.provider, "model"), model.trim()).apply(); refresh() }

    fun setModels(list: List<String>) { prefs.edit().putString(k(_settings.value.provider, "models"), list.joinToString("\n")).apply(); refresh() }

    fun setSystemPrompt(text: String) {
        val t = text.trim()
        prefs.edit().apply { if (t.isEmpty() || t == AiPrompt.DEFAULT) remove("system_prompt") else putString("system_prompt", t) }.apply()
        refresh()
    }

    fun setDataAccess(on: Boolean) { prefs.edit().putBoolean("data_access", on).apply(); refresh() }

    /** Simpan kunci API (terenkripsi). Kosong = hapus. @throws AiException bila perangkat tidak bisa mengenkripsi. */
    fun setApiKey(key: String) {
        val p = _settings.value.provider
        val clean = key.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove(k(p, "key")).apply()
        } else {
            val bytes = clean.toByteArray(Charsets.UTF_8)
            val sealed = try {
                box.seal(bytes)
            } catch (e: Exception) {
                throw AiException(AiErrorKind.Config, "Kunci API tidak bisa disimpan aman di perangkat ini: ${e.message ?: "Keystore gagal"}")
            } finally {
                bytes.fill(0)
            }
            prefs.edit().putString(k(p, "key"), sealed).apply()
        }
        refresh()
    }

    /** Kunci API penyedia aktif (dibuka saat dibutuhkan saja). null = belum ada / tidak bisa dibuka. */
    fun apiKey(): String? {
        val sealed = prefs.getString(k(_settings.value.provider, "key"), null) ?: return null
        val bytes = box.open(sealed) ?: return null
        return try { String(bytes, Charsets.UTF_8) } finally { bytes.fill(0) }
    }

    /** Koneksi penyedia mana pun (sesi lama bisa memakai penyedia yang bukan aktif saat ini). */
    override fun connection(provider: AiProvider): AiConnection? {
        val sealed = prefs.getString(k(provider, "key"), null) ?: return null
        val bytes = box.open(sealed) ?: return null
        val key = try { String(bytes, Charsets.UTF_8) } finally { bytes.fill(0) }
        return AiConnection(prefs.getString(k(provider, "base_url"), null) ?: provider.defaultBaseUrl, key)
    }

    /** Keluar akun: hapus semua pengaturan & kunci (perangkat bisa dipakai orang lain). */
    fun wipe() {
        prefs.edit().clear().apply()
        box.destroy()
        refresh()
    }
}

object AiPrompt {
    val DEFAULT: String = """
        Kamu asisten di aplikasi Taskify milik pengguna. Jawab dalam bahasa Indonesia yang jelas dan ringkas, kecuali pengguna memakai bahasa lain.
        Sekarang {hari}, {tanggal} pukul {jam} ({zona}).
        Jangan mengarang data pengguna. Untuk tugas, catatan, jurnal, atau keuangan, pakai alat yang tersedia; bila tidak ada alat, katakan bahwa akses data Taskify sedang mati.
        Untuk membuat sesuatu, pakai alat tulis; pengguna akan mengonfirmasinya dulu, jadi jangan mengklaim sudah tersimpan sebelum hasil alat mengatakan berhasil.
    """.trimIndent()

    private val ID = Locale.forLanguageTag("id-ID")

    /** Isi variabel {tanggal} {hari} {jam} {zona} saat pesan dikirim (bukan saat sesi dibuat). */
    fun render(template: String, now: ZonedDateTime = ZonedDateTime.now()): String = template
        .replace("{tanggal}", now.format(DateTimeFormatter.ofPattern("d MMMM yyyy", ID)))
        .replace("{hari}", now.format(DateTimeFormatter.ofPattern("EEEE", ID)))
        .replace("{jam}", now.format(DateTimeFormatter.ofPattern("HH.mm", ID)))
        .replace("{zona}", now.zone.id)
}

object AiClients {
    fun create(provider: AiProvider, baseUrl: String, apiKey: String): AiClient = when (provider) {
        AiProvider.OpenAiCompatible -> OpenAiCompatClient(baseUrl, apiKey)
        AiProvider.Anthropic -> AnthropicClient(baseUrl, apiKey)
    }
}
