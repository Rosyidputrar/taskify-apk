package com.rosyidputrar.taskify.feature.auth

import android.app.Activity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException

/**
 * Hasil Credential Manager yang sudah diklasifikasi, supaya pesan ke pengguna bisa diuji tanpa
 * perangkat. [raw] = jenis + pesan asli (ditampilkan apa adanya, sesuai kebiasaan aplikasi).
 */
sealed class PasskeyProblem(val raw: String) : Exception(raw) {
    /** Pengguna menutup lembar passkey — bukan galat, tidak perlu pesan. */
    class Cancelled(raw: String) : PasskeyProblem(raw)

    /** Belum ada passkey untuk akun ini di perangkat / pengelola sandi. */
    class NoPasskey(raw: String) : PasskeyProblem(raw)

    /** Perangkat/penyedia tidak mendukung passkey (mis. Android lama tanpa Google Play services). */
    class Unsupported(raw: String) : PasskeyProblem(raw)

    /**
     * Domain/aplikasi tidak saling menaut (assetlinks.json, rp_id, atau origin Android belum
     * didaftarkan di server). Paling sering terjadi saat pertama kali menyiapkan passkey.
     */
    class NotLinked(raw: String) : PasskeyProblem(raw)

    class Other(raw: String) : PasskeyProblem(raw)
}

/** Pesan untuk pengguna (null = diam, mis. dibatalkan). */
fun PasskeyProblem.userMessage(): String? = when (this) {
    is PasskeyProblem.Cancelled -> null
    is PasskeyProblem.NoPasskey -> "Belum ada passkey Taskify di perangkat ini. Masuk dengan email dulu, lalu tambahkan passkey di Pengaturan → Keamanan."
    is PasskeyProblem.Unsupported -> "Perangkat ini belum mendukung passkey (butuh Android 9+ dengan Google Play services atau pengelola sandi yang mendukung passkey).\n$raw"
    is PasskeyProblem.NotLinked -> "Passkey belum disiapkan di server untuk aplikasi ini (domain, assetlinks.json, atau origin Android).\n$raw"
    is PasskeyProblem.Other -> "Passkey gagal.\n$raw"
}

/**
 * Klasifikasi pesan DOMException WebAuthn. "SecurityError"/RP ID/origin = aplikasi belum ditautkan
 * ke domain relying party.
 */
internal fun classifyDom(type: String, message: String?): (String) -> PasskeyProblem {
    val text = "$type ${message.orEmpty()}".lowercase()
    return when {
        "notallowed" in text && ("cancel" in text || "user" in text) -> { r -> PasskeyProblem.Cancelled(r) }
        "security" in text || "rp id" in text || "rpid" in text || "origin" in text || "asset" in text || "domain" in text -> { r -> PasskeyProblem.NotLinked(r) }
        "notsupported" in text -> { r -> PasskeyProblem.Unsupported(r) }
        else -> { r -> PasskeyProblem.Other(r) }
    }
}

private fun raw(e: Exception): String = "${e::class.java.simpleName}: ${e.message.orEmpty()}".trimEnd(' ', ':')

/** Pembungkus Android Credential Manager untuk passkey (WebAuthn JSON masuk/keluar apa adanya). */
class PasskeyClient(private val activity: Activity) {
    private val manager = CredentialManager.create(activity)

    /** Buat passkey baru dari opsi registrasi server; hasil = JSON respons registrasi. */
    suspend fun create(optionsJson: String): String = try {
        val response = manager.createCredential(activity, CreatePublicKeyCredentialRequest(optionsJson))
        (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
            ?: throw PasskeyProblem.Other("Respons bukan passkey: ${response.type}")
    } catch (e: CreateCredentialCancellationException) {
        throw PasskeyProblem.Cancelled(raw(e))
    } catch (e: CreatePublicKeyCredentialDomException) {
        throw classifyDom(e.domError.type, e.errorMessage?.toString())(raw(e))
    } catch (e: CreateCredentialUnsupportedException) {
        throw PasskeyProblem.Unsupported(raw(e))
    } catch (e: CreateCredentialProviderConfigurationException) {
        throw PasskeyProblem.Unsupported(raw(e))
    } catch (e: CreateCredentialException) {
        throw PasskeyProblem.Other(raw(e))
    }

    /** Pilih passkey untuk masuk; hasil = JSON respons autentikasi. */
    suspend fun get(optionsJson: String): String = try {
        val result = manager.getCredential(activity, GetCredentialRequest(listOf(GetPublicKeyCredentialOption(optionsJson))))
        (result.credential as? PublicKeyCredential)?.authenticationResponseJson
            ?: throw PasskeyProblem.Other("Kredensial bukan passkey: ${result.credential.type}")
    } catch (e: GetCredentialCancellationException) {
        throw PasskeyProblem.Cancelled(raw(e))
    } catch (e: NoCredentialException) {
        throw PasskeyProblem.NoPasskey(raw(e))
    } catch (e: GetPublicKeyCredentialDomException) {
        throw classifyDom(e.domError.type, e.errorMessage?.toString())(raw(e))
    } catch (e: GetCredentialUnsupportedException) {
        throw PasskeyProblem.Unsupported(raw(e))
    } catch (e: GetCredentialProviderConfigurationException) {
        throw PasskeyProblem.Unsupported(raw(e))
    } catch (e: GetCredentialException) {
        throw PasskeyProblem.Other(raw(e))
    }
}

/** Operasi Credential Manager yang dibutuhkan layar — bisa diganti palsu di tes. */
interface PasskeyDevice {
    suspend fun create(optionsJson: String): String
    suspend fun get(optionsJson: String): String
}

fun PasskeyClient.asDevice(): PasskeyDevice = object : PasskeyDevice {
    override suspend fun create(optionsJson: String) = this@asDevice.create(optionsJson)
    override suspend fun get(optionsJson: String) = this@asDevice.get(optionsJson)
}
