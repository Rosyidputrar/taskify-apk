package com.rosyidputrar.taskify.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeMode
import com.rosyidputrar.taskify.data.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(private val preferences: AppPreferences) : ViewModel() {

    /** null = preferensi belum terbaca (splash tetap tampil supaya tema tidak berkedip). */
    val state: StateFlow<AppPreferences.Snapshot?> = preferences.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setPinnedModule(module: TaskifyModule?) {
        viewModelScope.launch { preferences.setPinnedModule(module) }
    }
}
