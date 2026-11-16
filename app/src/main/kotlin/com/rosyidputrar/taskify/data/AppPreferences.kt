package com.rosyidputrar.taskify.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "taskify_prefs")

/** Preferensi per perangkat (tidak disinkron): tema, modul yang disematkan ke Dok, dan getaran. */
class AppPreferences(private val context: Context) {

    data class Snapshot(val themeMode: ThemeMode, val pinnedModule: TaskifyModule?, val haptics: Boolean = true)

    val snapshot: Flow<Snapshot> = context.dataStore.data.map { prefs ->
        Snapshot(
            themeMode = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
            pinnedModule = prefs[PINNED]?.let { name -> TaskifyModule.entries.firstOrNull { it.name == name } },
            haptics = prefs[HAPTICS] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setPinnedModule(module: TaskifyModule?) {
        context.dataStore.edit { prefs ->
            if (module == null) prefs.remove(PINNED) else prefs[PINNED] = module.name
        }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { it[HAPTICS] = enabled }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val PINNED = stringPreferencesKey("dock_pinned_module")
        val HAPTICS = booleanPreferencesKey("haptics")
    }
}
