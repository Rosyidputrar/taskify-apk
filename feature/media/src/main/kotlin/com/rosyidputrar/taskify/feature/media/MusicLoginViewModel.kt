package com.rosyidputrar.taskify.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginError
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginException
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Keadaan layar login musik (kontrak PLAN C.8 — port `LoginScreenState`
 * ArchiveTune, tanpa Hilt; `Error.message` = [MusicLoginError.message],
 * bahasa Indonesia).
 */
sealed interface MusicLoginScreenState {
    data object Idle : MusicLoginScreenState
    data object Loading : MusicLoginScreenState
    data class Success(val account: AccountInfo) : MusicLoginScreenState
    data class Error(val message: String) : MusicLoginScreenState
}

/**
 * Port `LoginViewModel` ArchiveTune (tanpa Hilt, tanpa
 * `UpdateYouTubeLoginContextUseCase` — di Taskify `visitorData`/`dataSyncId`
 * dibawa langsung ke [MusicLoginRepository.completeLogin]).
 *
 * Guard persis ArchiveTune: jangan `startLogin` dua kali untuk cookie yang
 * sama; cookie yang sudah sukses jangan diproses lagi; `dataSyncId` baru
 * memicu `startLogin` ulang bila cookie aktif sudah ada.
 */
class MusicLoginViewModel(
    private val loginRepo: MusicLoginRepository,
) : ViewModel() {
    private val _screenState = MutableStateFlow<MusicLoginScreenState>(MusicLoginScreenState.Idle)
    val screenState: StateFlow<MusicLoginScreenState> = _screenState.asStateFlow()

    private var latestVisitorData: String? = null
    private var latestDataSyncId: String? = null
    private var loginJob: Job? = null
    private var activeCookie: String? = null
    private var completedCookie: String? = null

    fun onVisitorDataExtracted(visitorData: String?) {
        latestVisitorData = visitorData.normalizeAuthValue() ?: return
    }

    fun onDataSyncIdExtracted(dataSyncId: String?) {
        val normalized = dataSyncId.normalizeDataSyncId() ?: return
        if (latestDataSyncId == normalized) return
        // Sesudah sukses, dataSyncId baru dari halaman tidak memulai login ulang.
        if (_screenState.value is MusicLoginScreenState.Success) return
        latestDataSyncId = normalized
        activeCookie?.let { startLogin(it, replaceActive = true) }
    }

    fun onCookiesCaptured(cookie: String?) {
        val normalizedCookie = cookie.normalizeAuthValue() ?: return
        startLogin(normalizedCookie, replaceActive = false)
    }

    /** Kembali ke Idle (dipakai saat layar dibuka ulang). */
    fun reset() {
        loginJob?.cancel()
        loginJob = null
        activeCookie = null
        completedCookie = null
        latestVisitorData = null
        latestDataSyncId = null
        _screenState.value = MusicLoginScreenState.Idle
    }

    private fun startLogin(normalizedCookie: String, replaceActive: Boolean) {
        if (completedCookie == normalizedCookie) return
        if (!replaceActive && loginJob?.isActive == true && activeCookie == normalizedCookie) return

        activeCookie = normalizedCookie
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _screenState.value = MusicLoginScreenState.Loading
            loginRepo.completeLogin(
                cookie = normalizedCookie,
                visitorData = latestVisitorData,
                dataSyncId = latestDataSyncId,
            ).onSuccess { account ->
                completedCookie = normalizedCookie
                _screenState.value = MusicLoginScreenState.Success(account)
            }.onFailure { throwable ->
                _screenState.value = MusicLoginScreenState.Error(
                    (throwable as? MusicLoginException)?.error?.message
                        ?: MusicLoginError.VerificationFailed.message,
                )
            }
        }
    }
}

private fun String?.normalizeAuthValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? =
    PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
