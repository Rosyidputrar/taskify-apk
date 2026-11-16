package com.rosyidputrar.taskify.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.view.View
import android.view.autofill.AutofillId
import com.rosyidputrar.taskify.core.domain.sandi.AutofillTarget
import com.rosyidputrar.taskify.core.domain.sandi.FieldClassifier
import com.rosyidputrar.taskify.core.domain.sandi.FieldInfo
import com.rosyidputrar.taskify.core.domain.sandi.FormFields

/** Kolom terpilih pada formulir: id Autofill + nilai yang sedang diketik (untuk disimpan). */
data class FormField(val id: AutofillId, val value: String?)

data class ParsedForm(val target: AutofillTarget, val username: FormField?, val passwords: List<FormField>) {
    val ids: List<AutofillId> get() = listOfNotNull(username?.id) + passwords.map { it.id }
    val passwordValue: String? get() = passwords.firstNotNullOfOrNull { it.value?.takeIf(String::isNotEmpty) }
}

/** Menjelajah AssistStructure → [FieldInfo] → [FieldClassifier] (logika pengenalan ada di domain). */
object AutofillParser {
    fun parse(structure: AssistStructure?): ParsedForm? {
        if (structure == null) return null
        // Kumpulkan kolom per jendela beserta domain web jendela itu. Domain web diambil dari
        // jendela yang berisi kolom sandi pertama (bukan jendela pertama sembarang): jendela
        // overlay/iklan di atas bisa membawa domain lain dan meracuni target.
        val windows = (0 until structure.windowNodeCount).map { wi ->
            val nodes = mutableListOf<AssistStructure.ViewNode>()
            var webDomain: String? = null
            fun visit(node: AssistStructure.ViewNode) {
                if (webDomain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { webDomain = it }
                if (node.autofillId != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) nodes += node
                for (i in 0 until node.childCount) visit(node.getChildAt(i))
            }
            visit(structure.getWindowNodeAt(wi).rootViewNode)
            WindowNodes(nodes, webDomain)
        }
        val nodes = windows.flatMap { it.nodes }
        val form = FieldClassifier.classify(nodes.map(::info))
        if (form.passwords.isEmpty() && form.username == null) return null
        fun field(i: Int) = nodes[i].let { FormField(it.autofillId!!, it.autofillValue?.takeIf { v -> v.isText }?.textValue?.toString()) }
        return ParsedForm(
            // Skema halaman TIDAK disediakan framework (AssistStructure hanya memberi domain),
            // jadi scheme = null (tidak diketahui). Penegakan https-vs-http di AutofillMatch
            // berlaku bila skema diketahui (lihat AutofillTarget.scheme).
            target = AutofillTarget(structure.activityComponent.packageName, domainOf(windows, form)),
            username = form.username?.let(::field),
            passwords = form.passwords.map(::field),
        )
    }

    private data class WindowNodes(val nodes: List<AssistStructure.ViewNode>, val webDomain: String?)

    /** Domain web dari jendela berisi kolom sandi pertama (lalu nama pengguna, lalu jendela mana pun). */
    private fun domainOf(windows: List<WindowNodes>, form: FormFields): String? {
        val anchor = form.passwords.firstOrNull() ?: form.username
        if (anchor != null) {
            var seen = 0
            for (w in windows) {
                val next = seen + w.nodes.size
                if (anchor in seen until next) return w.webDomain
                seen = next
            }
        }
        return windows.firstNotNullOfOrNull { it.webDomain }
    }

    private fun info(node: AssistStructure.ViewNode): FieldInfo {
        val html = node.htmlInfo
        val attrs = html?.attributes.orEmpty().associate { (it.first ?: "").lowercase() to (it.second ?: "") }
        return FieldInfo(
            autofillHints = node.autofillHints?.toList().orEmpty(),
            inputType = node.inputType,
            htmlTag = html?.tag,
            htmlType = attrs["type"],
            htmlName = listOfNotNull(attrs["name"], attrs["id"], attrs["autocomplete"]).joinToString(" ").ifBlank { null },
            idEntry = node.idEntry,
            hint = node.hint ?: (if (Build.VERSION.SDK_INT >= 30) node.hintIdEntry else null),
            isEditable = node.isEnabled,
            isVisible = node.visibility == View.VISIBLE,
        )
    }
}
