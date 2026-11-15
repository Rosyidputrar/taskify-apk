package com.rosyidputrar.taskify.core.sandi

import android.content.Context
import com.rosyidputrar.taskify.core.domain.sandi.Generator
import com.rosyidputrar.taskify.core.domain.sandi.PassphraseOptions
import com.rosyidputrar.taskify.core.domain.sandi.PasswordOptions
import com.rosyidputrar.taskify.core.domain.sandi.SANDI_AUTO_LOCK_OPTIONS
import com.rosyidputrar.taskify.core.domain.sandi.SANDI_DEFAULT_AUTO_LOCK

/** Pengaturan Sandi per perangkat (bukan rahasia): kunci otomatis, koreksi jam TOTP, pilihan generator. */
class SandiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("taskify_sandi", Context.MODE_PRIVATE)

    var autoLockMinutes: Int
        get() = prefs.getInt("auto_lock", SANDI_DEFAULT_AUTO_LOCK).takeIf { it in SANDI_AUTO_LOCK_OPTIONS } ?: SANDI_DEFAULT_AUTO_LOCK
        set(value) = prefs.edit().putInt("auto_lock", value).apply()

    var clockOffsetMs: Long
        get() = prefs.getLong("clock_offset", 0)
        set(value) = prefs.edit().putLong("clock_offset", value).apply()

    /** Hanya PENGATURAN yang disimpan, tidak pernah sandi yang dihasilkan. */
    var generator: Generator.Prefs
        get() = Generator.sanitize(
            Generator.Prefs(
                mode = if (prefs.getString("gen_mode", "password") == "passphrase") Generator.Mode.Passphrase else Generator.Mode.Password,
                password = PasswordOptions(
                    prefs.getInt("gen_length", Generator.LENGTH_DEFAULT), prefs.getBoolean("gen_lower", true), prefs.getBoolean("gen_upper", true),
                    prefs.getBoolean("gen_digits", true), prefs.getBoolean("gen_symbols", true), prefs.getBoolean("gen_ambiguous", false),
                ),
                passphrase = PassphraseOptions(
                    prefs.getInt("gen_words", Generator.WORDS_DEFAULT), prefs.getString("gen_sep", "-") ?: "-",
                    prefs.getBoolean("gen_cap", false), prefs.getBoolean("gen_number", false),
                ),
            ),
        )
        set(v) = prefs.edit()
            .putString("gen_mode", if (v.mode == Generator.Mode.Passphrase) "passphrase" else "password")
            .putInt("gen_length", v.password.length).putBoolean("gen_lower", v.password.lower).putBoolean("gen_upper", v.password.upper)
            .putBoolean("gen_digits", v.password.digits).putBoolean("gen_symbols", v.password.symbols).putBoolean("gen_ambiguous", v.password.avoidAmbiguous)
            .putInt("gen_words", v.passphrase.words).putString("gen_sep", v.passphrase.separator).putBoolean("gen_cap", v.passphrase.capitalize)
            .putBoolean("gen_number", v.passphrase.includeNumber)
            .apply()
}
