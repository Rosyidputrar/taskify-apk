package com.rosyidputrar.taskify.autofill

import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Sandi sebagai layanan Isi Otomatis Android.
 *
 * - Brankas terbuka (dan belum lewat batas kunci otomatis): item yang cocok dengan domain situs /
 *   paket aplikasi langsung ditawarkan (maks. 5), plus "Cari di Sandi…".
 * - Brankas terkunci: hanya "Buka Sandi" — membuka brankas (sidik jari bila perangkat diingat, atau
 *   master password) lalu memilih item. Isi brankas tidak pernah dikirim ke sistem sebelum terbuka.
 * - Sandi baru yang diketik bisa disimpan / memperbarui item lewat dialog simpan sistem.
 */
class TaskifyAutofillService : AutofillService(), KoinComponent {
    private val vault: AutofillVault by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val form = AutofillParser.parse(request.fillContexts.lastOrNull()?.structure)
        if (form == null || form.target.packageName == packageName) return callback.onSuccess(null)
        val job = scope.launch {
            val response = runCatching { buildResponse(form) }.getOrNull()
            runCatching { callback.onSuccess(response) }
        }
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    private suspend fun buildResponse(form: ParsedForm): FillResponse {
        val items = vault.unlockedItems()
        val builder = FillResponse.Builder()
        if (items != null) {
            vault.matches(items, form.target).take(MAX_SUGGESTIONS).forEach { builder.addDataset(AutofillDatasets.item(this, form, it.payload)) }
        }
        builder.addDataset(AutofillDatasets.picker(this, form, locked = items == null))
        if (form.passwords.isNotEmpty()) {
            val type = SaveInfo.SAVE_DATA_TYPE_PASSWORD or (if (form.username != null) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0)
            builder.setSaveInfo(
                SaveInfo.Builder(type, form.passwords.map { it.id }.toTypedArray())
                    .apply { form.username?.let { setOptionalIds(arrayOf(it.id)) } }
                    .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                    .build(),
            )
        }
        return builder.build()
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val form = AutofillParser.parse(request.fillContexts.lastOrNull()?.structure)
        val password = form?.passwordValue
        if (form == null || password == null || form.target.packageName == packageName) return callback.onSuccess()
        val username = form.username?.value.orEmpty()
        scope.launch {
            val items = vault.unlockedItems()
            when {
                items != null -> runCatching { vault.save(items, form.target, username, password) }
                    .onSuccess { callback.onSuccess() }
                    .onFailure { callback.onFailure("Gagal menyimpan ke Sandi: ${it.message}") }
                // Terkunci: buka Sandi dulu (activity), kredensial dititipkan di memori proses saja.
                Build.VERSION.SDK_INT >= 28 -> callback.onSuccess(SandiAutofillActivity.saveSender(this@TaskifyAutofillService, PendingSave(form.target, username, password)))
                else -> callback.onFailure("Buka Sandi di Taskify dulu, lalu masuk lagi untuk menyimpan sandi ini.")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val MAX_SUGGESTIONS = 5
    }
}
