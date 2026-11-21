package com.rosyidputrar.taskify.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ambil tantangan passkey langsung dari GoTrue REST, tanpa lewat parser supabase-kt.
 *
 * supabase-kt 3.9.0 mem-parse `expires_at` respons challenge pendaftaran sebagai `Instant`
 * (string ISO-8601), padahal server mengirim angka epoch detik, sehingga "Tambah passkey"
 * selalu gagal (`Unexpected JSON token ... at path: $.expires_at`, 21 Nov 2026). Respons
 * dibaca sebagai [JsonObject] mentah: `options` diteruskan apa adanya ke Credential
 * Manager, `expires_at` tidak dibaca sama sekali (tidak dipakai aplikasi), jadi bentuk
 * apa pun yang dikirim server tidak bisa lagi memecahkan parsing ini.
 *
 * Panggilan memakai [HttpClient] Ktor sendiri (bukan `supabase.httpClient`): wrapper
 * supabase memperlakukan argumen sebagai path relatif terhadap host default (localhost),
 * dan klien mentah di dalamnya adalah API internal library. Hanya challenge PENDAFTARAN
 * yang di-bypass: challenge masuk (`startAuthentication`) memakai `UnixTimestampSerializer`
 * di library, jadi sudah benar. Verify/list/rename/delete tetap lewat library; bila server
 * ternyata mengirim angka di `created_at`/`last_used_at`, jalur itu ikut perlu bypass yang
 * sama (baru ketahuan saat tes di HP).
 */
class PasskeyChallengeApi(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Tantangan daftar passkey baru (butuh login: [accessToken] dari sesi aktif). */
    suspend fun registrationOptions(accessToken: String): PasskeyChallenge {
        val response = httpClient.post("${supabaseUrl.trimEnd('/')}/auth/v1/passkeys/registration/options") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("apikey", supabaseKey)
        }
        val body = response.bodyAsText()
        check(response.status.value in 200..299) {
            "Passkey gagal: server menjawab HTTP ${response.status.value}: ${body.take(200)}"
        }
        val root = json.parseToJsonElement(body).jsonObject
        val challengeId = root["challenge_id"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Respons passkey tidak berisi challenge_id.")
        val options = root["options"]?.jsonObject
            ?: throw IllegalStateException("Respons passkey tidak berisi options.")
        return PasskeyChallenge(challengeId, options.toString())
    }
}

/** Klien HTTP bawaan untuk [PasskeyChallengeApi] (satu per proses, mengikuti pola AiHttp). */
internal fun defaultPasskeyHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = 30_000
    }
}
