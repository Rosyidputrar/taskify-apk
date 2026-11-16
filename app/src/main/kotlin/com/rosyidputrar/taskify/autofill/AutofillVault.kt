package com.rosyidputrar.taskify.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.Dataset
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.rosyidputrar.taskify.R
import com.rosyidputrar.taskify.core.domain.sandi.AutofillMatch
import com.rosyidputrar.taskify.core.domain.sandi.AutofillTarget
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.MatchStrength
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.core.sandi.SandiService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Hasil menyimpan kredensial dari formulir. */
sealed interface SaveOutcome {
    data class Saved(val title: String, val updated: Boolean) : SaveOutcome
    data object Unchanged : SaveOutcome
}

/** Akses brankas Sandi untuk Isi Otomatis (dipakai layanan & activity). */
class AutofillVault(private val sandi: SandiService) {
    /** Item brankas bila terbuka & belum lewat batas kunci otomatis; null = terkunci. */
    suspend fun unlockedItems(): List<SandiItem>? {
        sandi.session.openForAutofill() ?: return null
        return withTimeoutOrNull(4_000) { sandi.items.first { it.ready } }?.items
    }

    fun matches(items: List<SandiItem>, target: AutofillTarget): List<SandiItem> = AutofillMatch.rank(items, target) { it.payload }

    /** Simpan / perbarui: item cocok dengan nama pengguna sama diperbarui, selain itu item baru. */
    suspend fun save(items: List<SandiItem>, target: AutofillTarget, username: String, password: String): SaveOutcome =
        when (val plan = planSave(items, target, username, password)) {
            SavePlan.Unchanged -> SaveOutcome.Unchanged
            is SavePlan.Update -> {
                sandi.saveItem(plan.payload, plan.id)
                SaveOutcome.Saved(plan.payload.title, updated = true)
            }
            is SavePlan.Create -> {
                sandi.saveItem(plan.payload)
                SaveOutcome.Saved(plan.payload.title, updated = false)
            }
        }

    sealed interface SavePlan {
        data object Unchanged : SavePlan
        data class Update(val id: String, val payload: ItemPayload) : SavePlan
        data class Create(val payload: ItemPayload) : SavePlan
    }

    companion object {
        fun planSave(items: List<SandiItem>, target: AutofillTarget, username: String, password: String): SavePlan {
            // Hanya item yang BENAR-BENAR cocok yang boleh diperbarui: Exact, atau Domain untuk
            // target web. Tanpa ini, aplikasi sideload dengan username sama bisa menimpa sandi
            // item lain diam-diam (rank() dulu menyertakan kecocokan heuristik nama paket).
            val existing = items
                .filter {
                    val s = AutofillMatch.strength(it.payload, target)
                    s == MatchStrength.Exact || (target.webDomain != null && s == MatchStrength.Domain)
                }
                .firstOrNull { it.payload.username == username }
            if (existing != null) {
                return if (existing.payload.password == password) SavePlan.Unchanged
                else SavePlan.Update(existing.id, existing.payload.copy(password = password))
            }
            return SavePlan.Create(ItemPayload(title = target.label, url = AutofillMatch.urlFor(target), username = username, password = password))
        }
    }
}

/** Pembuat Dataset & tampilan baris saran Isi Otomatis. */
@Suppress("DEPRECATION") // Dataset.Builder#setValue(id, value, RemoteViews): satu-satunya jalur untuk API 26–32.
object AutofillDatasets {
    fun presentation(context: Context, title: String, subtitle: String?, icon: Int = R.drawable.ic_autofill_key): RemoteViews =
        RemoteViews(context.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_subtitle, subtitle.orEmpty())
            setViewVisibility(R.id.autofill_subtitle, if (subtitle.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            setImageViewResource(R.id.autofill_icon, icon)
        }

    fun item(context: Context, form: ParsedForm, item: ItemPayload): Dataset {
        val view = presentation(context, item.title.ifBlank { form.target.label }, item.username.ifBlank { "tanpa nama pengguna" })
        return Dataset.Builder(view).apply {
            form.username?.let { setValue(it.id, AutofillValue.forText(item.username), view) }
            form.passwords.forEach { setValue(it.id, AutofillValue.forText(item.password), view) }
        }.build()
    }

    /** Baris "Buka Sandi…" / "Cari di Sandi…": membuka [SandiAutofillActivity] lalu mengembalikan Dataset. */
    fun picker(context: Context, form: ParsedForm, locked: Boolean): Dataset {
        val view = presentation(
            context,
            if (locked) "Buka Sandi" else "Cari di Sandi…",
            if (locked) "Isi otomatis untuk ${form.target.label}" else "Pilih item lain",
            R.drawable.ic_autofill_lock,
        )
        return Dataset.Builder(view).apply {
            form.ids.forEach { setValue(it, null, view) }
            setAuthentication(SandiAutofillActivity.fillSender(context))
        }.build()
    }

    /**
     * IntentSender ke activity. FILL harus MUTABLE: sistem menambahkan AssistStructure ke intent
     * saat dataset autentikasi dipakai. SAVE tidak butuh itu → IMMUTABLE (lebih aman: intent
     * tidak bisa diubah siapa pun setelah dibuat).
     */
    @SuppressLint("WrongConstant")
    fun sender(context: Context, intent: Intent, requestCode: Int, mutable: Boolean): android.content.IntentSender =
        PendingIntent.getActivity(context, requestCode, intent, flagsFor(mutable)).intentSender

    /** Bendera PendingIntent untuk [sender]: save = IMMUTABLE, fill = MUTABLE. */
    internal fun flagsFor(mutable: Boolean): Int {
        var flags = PendingIntent.FLAG_CANCEL_CURRENT
        if (Build.VERSION.SDK_INT >= 31) {
            flags = flags or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        } else if (!mutable && Build.VERSION.SDK_INT >= 23) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }
}
