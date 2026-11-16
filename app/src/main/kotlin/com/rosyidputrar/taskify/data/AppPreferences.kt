package com.rosyidputrar.taskify.data

import com.rosyidputrar.taskify.core.designsystem.theme.ThemeContrast
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeStyle
import com.rosyidputrar.taskify.core.designsystem.theme.ThemePalette
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeSpec
import androidx.datastore.preferences.core.intPreferencesKey
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

    data class Snapshot(val themeMode: ThemeMode, val pinnedModule: TaskifyModule?, val haptics: Boolean = true, val theme: ThemeSpec = ThemeSpec.Default)

    val snapshot: Flow<Snapshot> = context.dataStore.data.map { prefs ->
        Snapshot(
            themeMode = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
            pinnedModule = prefs[PINNED]?.let { name -> TaskifyModule.entries.firstOrNull { it.name == name } },
            haptics = prefs[HAPTICS] ?: true,
            theme = ThemeSpec(
                palette = ThemePalette.from(prefs[PALETTE]),
                seed = prefs[SEED] ?: ThemeSpec.Default.seed,
                style = prefs[STYLE]?.let { n -> ThemeStyle.entries.firstOrNull { it.name == n } } ?: ThemeStyle.TonalSpot,
                paper = prefs[PAPER] ?: true,
                contrast = prefs[CONTRAST]?.let { n -> ThemeContrast.entries.firstOrNull { it.name == n } } ?: ThemeContrast.Standard,
            ),
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

    /** Susunan Beranda (urutan & visibilitas bagian / ubin). */
    val homeLayout: Flow<com.rosyidputrar.taskify.core.domain.home.HomeLayout> =
        context.dataStore.data.map { com.rosyidputrar.taskify.core.domain.home.HomeLayout.decode(it[HOME_LAYOUT]) }

    suspend fun setHomeLayout(layout: com.rosyidputrar.taskify.core.domain.home.HomeLayout) {
        context.dataStore.edit { it[HOME_LAYOUT] = layout.encode() }
    }

    suspend fun setTheme(spec: ThemeSpec) {
        context.dataStore.edit {
            it[PALETTE] = spec.palette.name
            it[SEED] = spec.seed
            it[STYLE] = spec.style.name
            it[PAPER] = spec.paper
            it[CONTRAST] = spec.contrast.name
        }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { it[HAPTICS] = enabled }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val PINNED = stringPreferencesKey("dock_pinned_module")
        val HAPTICS = booleanPreferencesKey("haptics")
        val PALETTE = stringPreferencesKey("theme_palette")
        val SEED = intPreferencesKey("theme_seed")
        val STYLE = stringPreferencesKey("theme_style")
        val PAPER = booleanPreferencesKey("theme_paper")
        val CONTRAST = stringPreferencesKey("theme_contrast")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
    }
}
