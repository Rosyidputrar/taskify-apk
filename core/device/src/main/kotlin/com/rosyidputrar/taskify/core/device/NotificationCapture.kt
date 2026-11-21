package com.rosyidputrar.taskify.core.device

import com.rosyidputrar.taskify.core.domain.device.MoneyNotifParser
import com.rosyidputrar.taskify.core.domain.device.NotifClassifier
import com.rosyidputrar.taskify.core.domain.device.NotifStorageLevel
import com.rosyidputrar.taskify.core.domain.device.OtpRedactor
import com.rosyidputrar.taskify.core.domain.device.ParcelParser
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.NewNotif

/** Notifikasi mentah dari sistem (sudah dibaca dari StatusBarNotification). */
data class RawNotif(
    val pkg: String,
    val key: String?,
    val title: String?,
    val conversation: String?,
    val text: String?,
    val androidCategory: String?,
    val postedAt: Long,
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
)

/** Keputusan untuk satu notifikasi. */
sealed interface CaptureDecision {
    data object Ignore : CaptureDecision
    data class CountOnly(val day: String, val pkg: String) : CaptureDecision
    data class Log(val notif: NewNotif) : CaptureDecision
}

/** Aturan pencatatan (murni, diuji tanpa Android): saringan, sensor OTP, tingkat penyimpanan. */
object NotificationCapture {
    private val SKIP_CATEGORIES = setOf("progress", "service", "transport", "sys", "call", "navigation", "stopwatch", "alarm")

    fun decide(raw: RawNotif, settings: NotifSettings, ownPackage: String, label: String): CaptureDecision {
        if (raw.pkg == ownPackage || raw.pkg in AppInfo.SYSTEM_UI || raw.pkg in settings.excluded) return CaptureDecision.Ignore
        if (raw.groupSummary) return CaptureDecision.Ignore
        if (settings.ignoreOngoing && (raw.ongoing || raw.androidCategory in SKIP_CATEGORIES)) return CaptureDecision.Ignore
        val rawTitle = raw.title?.trim()?.takeIf { it.isNotEmpty() }
        val rawText = raw.text?.trim()?.takeIf { it.isNotEmpty() }
        val rawConversation = raw.conversation?.trim()?.takeIf { it.isNotEmpty() }
        // Cek konteks OTP gabungan (judul + isi + percakapan) agar kode di isi tetap disensor
        // meskipun kata "OTP"/"Kode verifikasi" hanya muncul di judul notifikasi.
        val otpContext = OtpRedactor.hasContext(rawTitle) || OtpRedactor.hasContext(rawText) || OtpRedactor.hasContext(rawConversation)
        val title = OtpRedactor.redact(rawTitle, forceContext = otpContext)
        val text = OtpRedactor.redact(rawText, forceContext = otpContext)
        if (title == null && text == null) return CaptureDecision.Ignore
        val day = Dates.today(raw.postedAt)
        if (settings.level == NotifStorageLevel.CountOnly) return CaptureDecision.CountOnly(day, raw.pkg)
        val category = NotifClassifier.category(raw.pkg, raw.androidCategory, label)
        val money = MoneyNotifParser.parse(raw.pkg, title, text, label)
        val parcel = ParcelParser.parse(raw.pkg, title, text)
        val keepText = settings.level == NotifStorageLevel.Full
        // D-07: percakapan (judul grup/pengirim) disensor seperti judul & dibatasi panjangnya —
        // sebelumnya tersimpan mentah tanpa batas.
        val conversation = if (keepText) OtpRedactor.redact(rawConversation, forceContext = otpContext)?.take(MAX_TITLE) else null
        return CaptureDecision.Log(
            NewNotif(
                pkg = raw.pkg,
                appLabel = label,
                key = raw.key,
                conversation = conversation,
                title = title?.take(MAX_TITLE),
                text = if (keepText) text?.take(MAX_TEXT) else null,
                category = category,
                postedAt = raw.postedAt,
                day = day,
                money = money,
                parcel = parcel,
            ),
        )
    }

    const val MAX_TITLE = 200
    const val MAX_TEXT = 2_000
}
