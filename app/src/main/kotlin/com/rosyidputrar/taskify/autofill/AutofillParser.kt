package com.rosyidputrar.taskify.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.view.View
import android.view.autofill.AutofillId
import com.rosyidputrar.taskify.core.domain.sandi.AutofillTarget
import com.rosyidputrar.taskify.core.domain.sandi.FieldClassifier
import com.rosyidputrar.taskify.core.domain.sandi.FieldInfo

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
        val nodes = mutableListOf<AssistStructure.ViewNode>()
        var webDomain: String? = null
        fun visit(node: AssistStructure.ViewNode) {
            if (webDomain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { webDomain = it }
            if (node.autofillId != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) nodes += node
            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)
        val form = FieldClassifier.classify(nodes.map(::info))
        if (form.passwords.isEmpty() && form.username == null) return null
        fun field(i: Int) = nodes[i].let { FormField(it.autofillId!!, it.autofillValue?.takeIf { v -> v.isText }?.textValue?.toString()) }
        return ParsedForm(
            target = AutofillTarget(structure.activityComponent.packageName, webDomain),
            username = form.username?.let(::field),
            passwords = form.passwords.map(::field),
        )
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
