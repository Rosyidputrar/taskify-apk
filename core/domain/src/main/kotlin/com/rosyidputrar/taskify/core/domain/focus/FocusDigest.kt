package com.rosyidputrar.taskify.core.domain.focus

import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.domain.device.NotifCategory
import com.rosyidputrar.taskify.core.domain.device.TaskHint
import com.rosyidputrar.taskify.core.domain.finance.TxKind

/** Tingkat kepentingan notifikasi yang tertahan selama sesi Fokus. */
enum class DigestKind(val label: String, val priority: Int) {
    Transaction("Transaksi", 0),
    TaskHint("Bisa jadi tugas", 1),
    Message("Pesan", 2),
}

/** Input ringan (bebas dependensi database) untuk menyusun ringkasan pasca-fokus. */
data class FocusDigestInput(
    val id: String,
    val appLabel: String,
    val title: String?,
    val conversation: String?,
    val text: String?,
    val category: NotifCategory,
    val postedAt: Long,
    val moneyAmount: Long? = null,
    val moneyKind: TxKind? = null,
    val moneySource: String? = null,
    val moneyParty: String? = null,
)

data class FocusDigestItem(
    val id: String,
    val appLabel: String,
    val headline: String,
    val detail: String?,
    val kind: DigestKind,
    val postedAt: Long,
    /** Saran judul tugas bila pengguna mengetuk "Jadikan tugas" dari ringkasan. */
    val suggestedTaskTitle: String,
)

/** Ringkasan notifikasi yang masuk selama sesi Fokus berlangsung. */
data class FocusSessionDigest(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val focusedMinutes: Long,
    val taskTitle: String?,
    val totalNotifs: Int,
    val transactionCount: Int,
    val taskHintCount: Int,
    val highlights: List<FocusDigestItem>,
)

/**
 * Saring notifikasi yang masuk selama `[startedAtMs, endedAtMs]` menjadi ringkasan pasca-fokus:
 * transaksi bank/e-wallet dan pesan bertanda "Tugas?" diprioritaskan paling atas.
 */
fun summarizeFocusNotifs(
    entries: List<FocusDigestInput>,
    startedAtMs: Long,
    endedAtMs: Long,
    focusedMinutes: Long,
    taskTitle: String?,
    maxHighlights: Int = 6,
): FocusSessionDigest {
    val inWindow = entries.filter { it.postedAt in startedAtMs..endedAtMs }
    var txCount = 0
    var hintCount = 0
    val mapped = inWindow.mapNotNull { e ->
        val isTx = e.moneyAmount != null && e.moneyKind != null
        val isTaskHint = !isTx && TaskHint.looksLikeTask(e.category, e.text)
        val kind = when {
            isTx -> { txCount++; DigestKind.Transaction }
            isTaskHint -> { hintCount++; DigestKind.TaskHint }
            e.category == NotifCategory.Chat || e.category == NotifCategory.Email -> DigestKind.Message
            else -> return@mapNotNull null
        }
        val headline = when (kind) {
            DigestKind.Transaction -> {
                val sign = if (e.moneyKind == TxKind.Income) "+" else "−"
                val src = e.moneyParty ?: e.moneySource ?: e.appLabel
                "$sign${Rupiah.format(e.moneyAmount!!)} · $src"
            }
            else -> listOfNotNull(e.conversation, e.title).firstOrNull()?.takeIf { it.isNotBlank() } ?: e.appLabel
        }
        val detail = when (kind) {
            DigestKind.Transaction -> e.moneySource ?: e.appLabel
            else -> e.text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
        }
        val taskSuggestion = TaskHint.taskTitle(e.category, e.conversation ?: e.title, e.text).ifBlank { headline }
        FocusDigestItem(
            id = e.id,
            appLabel = e.appLabel,
            headline = headline,
            detail = detail,
            kind = kind,
            postedAt = e.postedAt,
            suggestedTaskTitle = taskSuggestion,
        )
    }.sortedWith(compareBy<FocusDigestItem> { it.kind.priority }.thenByDescending { it.postedAt })
        .take(maxHighlights)

    return FocusSessionDigest(
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        focusedMinutes = focusedMinutes,
        taskTitle = taskTitle,
        totalNotifs = inWindow.size,
        transactionCount = txCount,
        taskHintCount = hintCount,
        highlights = mapped,
    )
}
