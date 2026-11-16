package com.rosyidputrar.taskify.ui.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.auth.AuthRepository
import com.rosyidputrar.taskify.core.auth.AuthState
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.core.domain.auth.GateStatus
import com.rosyidputrar.taskify.session.SessionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Layar mana yang tampil: urutan gerbang sama dengan webapp (auth → MFA → database → PIN). */
sealed interface GateUi {
    data object Loading : GateUi
    data object ConfigMissing : GateUi
    data object SignedOut : GateUi
    data class Mfa(val status: GateStatus) : GateUi
    data object Preparing : GateUi
    data object Locked : GateUi
    data class Open(val displayName: String?) : GateUi
}

class GateViewModel(
    auth: AuthRepository?,
    coordinator: SessionCoordinator?,
    lock: LockManager,
) : ViewModel() {
    val ui: StateFlow<GateUi> =
        if (auth == null || coordinator == null) {
            MutableStateFlow(GateUi.ConfigMissing)
        } else {
            combine(auth.state, coordinator.readyUser, lock.state, coordinator.displayName) { authState, readyUser, lockState, name ->
                when (authState) {
                    AuthState.Loading -> GateUi.Loading
                    AuthState.SignedOut -> GateUi.SignedOut
                    is AuthState.SignedIn -> when {
                        authState.gate != GateStatus.Ok -> GateUi.Mfa(authState.gate)
                        readyUser != authState.userId -> GateUi.Preparing
                        lockState.locked -> GateUi.Locked
                        else -> GateUi.Open(name)
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, GateUi.Loading)
        }
}
