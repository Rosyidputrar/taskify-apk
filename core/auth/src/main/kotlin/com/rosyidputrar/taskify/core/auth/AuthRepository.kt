package com.rosyidputrar.taskify.core.auth

import com.rosyidputrar.taskify.core.domain.auth.AuthFailure
import com.rosyidputrar.taskify.core.domain.auth.GateStatus
import com.rosyidputrar.taskify.core.domain.auth.OFFLINE_GRACE_MILLIS
import com.rosyidputrar.taskify.core.domain.auth.decodeJwtPayload
import com.rosyidputrar.taskify.core.domain.auth.gateStatus
import com.rosyidputrar.taskify.core.domain.auth.mfaStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?, val gate: GateStatus) : AuthState
}

data class TotpFactor(val id: String, val name: String)

data class TotpEnrollment(val factorId: String, val secret: String, val uri: String)

/** Error auth yang sudah diklasifikasi untuk diterjemahkan ke pesan (lihat AuthMessages). */
class AuthError(val failure: AuthFailure) : Exception(failure.message)

/**
 * Sumber kebenaran status login + gerbang MFA. Sesi Supabase disimpan di perangkat dan
 * di-refresh otomatis, jadi saat offline aplikasi tetap terbuka dengan sesi terakhir.
 */
class AuthRepository(
    private val supabase: SupabaseClient,
    private val trustStore: MfaTrustStore,
    private val network: NetworkMonitor,
    scope: CoroutineScope,
) {
    private val auth get() = supabase.auth

    /** User yang sudah lolos gerbang sejak aplikasi dibuka (proses ini hidup). */
    @Volatile private var launchUser: String? = null

    /** Faktor terverifikasi terakhir yang diketahui per user (sesi hasil refresh bisa tanpa user). */
    private val knownVerified = mutableMapOf<String, Boolean>()

    private val gateTick = MutableStateFlow(0)

    val state: StateFlow<AuthState> = combine(auth.sessionStatus, gateTick, network.online) { status, _, online ->
        when (status) {
            SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            // Refresh gagal (mis. offline): sesi tersimpan tetap dipakai — sama dengan webapp.
            is SessionStatus.RefreshFailure -> auth.currentSessionOrNull()?.let { signedIn(it, online) } ?: AuthState.SignedOut
            is SessionStatus.Authenticated -> signedIn(status.session, online)
        }
    }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    private fun signedIn(session: UserSession, online: Boolean): AuthState {
        val user = session.user
        val userId = user?.id ?: subjectOf(session.accessToken) ?: return AuthState.SignedOut
        val verified = user?.factors?.any { it.isVerified }?.also { knownVerified[userId] = it } ?: knownVerified[userId] ?: true
        val mfa = mfaStatus(session.accessToken, verified)
        val gate = gateStatus(mfa, userId, System.currentTimeMillis(), online, trustStore.read(), launchUser, trustStore.lastVerifiedAt())
        if (gate == GateStatus.Ok) launchUser = userId
        return AuthState.SignedIn(userId, user?.email, gate)
    }

    private fun refreshGate() { gateTick.value++ }

    suspend fun signIn(email: String, password: String) = guard {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * Keluar global (mencabut refresh token) butuh internet; bila gagal tetap keluarkan sesi di
     * perangkat ini supaya pengguna tidak "tertahan" login padahal datanya sudah dikosongkan.
     */
    suspend fun signOut() {
        launchUser = null
        try {
            auth.signOut(SignOutScope.GLOBAL)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            auth.signOut(SignOutScope.LOCAL)
        }
    }

    suspend fun verifiedTotpFactors(): List<TotpFactor> = guard {
        auth.mfa.retrieveFactorsForCurrentUser()
            .filter { it.isVerified && it.factorType == "totp" }
            .map { TotpFactor(it.id, it.friendlyName?.takeIf(String::isNotBlank) ?: "Authenticator") }
    }

    /**
     * Verifikasi kode. [remember]: true = percayai perangkat 30 hari, false = lupakan, null = jangan
     * ubah (mis. verifikasi authenticator cadangan dari Pengaturan).
     */
    suspend fun verify(factorId: String, code: String, remember: Boolean?) {
        val session = guard { auth.mfa.createChallengeAndVerify(factorId, code) }
        val userId = session.user?.id ?: auth.currentUserOrNull()?.id ?: subjectOf(session.accessToken)
        if (userId != null) {
            launchUser = userId
            knownVerified[userId] = true
            when (remember) {
                true -> trustStore.trust(userId)
                false -> trustStore.clear()
                null -> Unit
            }
            // Jejak lolos untuk masa tenggang offline — tapi BUKAN bila pengguna memilih untuk
            // tidak mengingat perangkat ini (remember == false): pilihannya dihormati.
            if (remember != false) trustStore.recordVerified()
        }
        refreshGate()
    }

    /** Mulai mendaftarkan authenticator baru (faktor lama yang belum terverifikasi dibersihkan). */
    suspend fun startEnrollment(friendlyName: String): TotpEnrollment = guard {
        val existing = auth.mfa.retrieveFactorsForCurrentUser()
        existing.filter { !it.isVerified }.forEach { auth.mfa.unenroll(it.id) }
        val name = friendlyName.trim().ifEmpty { "Taskify Android" }
        val unique = if (existing.any { it.isVerified && it.friendlyName == name }) "$name (${existing.size + 1})" else name
        val factor = auth.mfa.enroll(FactorType.TOTP, friendlyName = unique)
        TotpEnrollment(factor.id, factor.data.secret, factor.data.uri)
    }

    /** Hapus authenticator (butuh sesi aal2). Pemanggil wajib mencegah menghapus yang terakhir (MFA wajib). */
    suspend fun unenroll(factorId: String) = guard { auth.mfa.unenroll(factorId) }

    /**
     * Konfirmasi ulang offline HANYA bila perangkat pernah lolos verifikasi dalam masa tenggang
     * (24 jam). Tanpa jejak lolos sama sekali — atau sudah lewat tenggang — tombol lewati tidak
     * boleh ada gunanya: gerbang tetap Reverify sampai online (D-02).
     */
    fun canSkipReverifyOffline(): Boolean {
        val last = trustStore.lastVerifiedAt() ?: return false
        val now = System.currentTimeMillis()
        return last <= now && now - last < OFFLINE_GRACE_MILLIS
    }

    /** Lewati reverify untuk pembukaan ini — diam bila [canSkipReverifyOffline] false. */
    fun skipReverifyOffline() {
        if (!canSkipReverifyOffline()) return
        val userId = auth.currentUserOrNull()?.id ?: auth.currentSessionOrNull()?.let { subjectOf(it.accessToken) } ?: return
        launchUser = userId
        refreshGate()
    }

    /** "Lupakan perangkat ini": pembukaan berikutnya meminta kode lagi. */
    fun forgetDevice() {
        trustStore.clear()
    }

    fun currentUserId(): String? = (state.value as? AuthState.SignedIn)?.userId

    fun currentEmail(): String? = (state.value as? AuthState.SignedIn)?.email ?: auth.currentUserOrNull()?.email

    private fun subjectOf(token: String): String? =
        (decodeJwtPayload(token)?.get("sub") as? JsonPrimitive)?.content

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthRestException) {
        throw AuthError(AuthFailure(network = false, status = e.statusCode, code = e.errorCode?.value ?: e.error, message = e.errorDescription))
    } catch (e: RestException) {
        throw AuthError(AuthFailure(network = false, status = e.statusCode, code = e.error, message = e.description ?: e.message))
    } catch (e: HttpRequestException) {
        throw AuthError(AuthFailure(network = true, status = null, code = null, message = e.message))
    } catch (e: IOException) {
        throw AuthError(AuthFailure(network = true, status = null, code = null, message = e.message))
    }
}
