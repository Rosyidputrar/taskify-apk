/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `core/.../innertube/YouTube.kt:2499-2554`
// (`accountInfo`/`accountDataSyncId`) + `models/response/AccountMenuResponse.kt`
// (v15.1.0, c7f0e24) — logika yang sama, tapi ditulis ulang di atas
// `JsonElement` + helper `pick` Taskify (tanpa deserialisasi ber-skema,
// mengikuti parser Tahap 2 lainnya), dan tanpa `AccountChannel` (multi-akun
// di luar scope PLAN G.5): yang diambil hanya `dataSyncId` akun terpilih.
// Lihat licenses/ARCHIVETUNE.md.

package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Info akun aktif dari respons `account/account_menu` (PLAN C.3):
 * `actions[0].openPopupAction.popup.multiPageMenuRenderer.header.activeAccountHeaderRenderer`.
 * Bila jalur tetap itu tidak ketemu, pindai SELURUH pohon JSON untuk
 * `activeAccountHeaderRenderer` dan pakai yang pertama (PLAN
 * music-login-raw-error — bentuk respons live bisa bergeser nestingnya).
 * Nama = `accountName.runs[0].text` — null (nama kosong/hilang) berarti
 * tidak ada akun yang bisa dipakai → kembalikan null, jangan karang nama.
 * Email/handle = `runs[0].text` masing-masing; foto = thumbnail terakhir
 * di `accountPhoto.thumbnails`.
 */
fun parseAccountInfo(root: JsonElement): AccountInfo? {
    val fixedHeader = pick(
        root,
        "actions.0.openPopupAction.popup.multiPageMenuRenderer.header.activeAccountHeaderRenderer",
    ) as? JsonObject
    val header = fixedHeader
        ?: findObjectsNamed(root, "activeAccountHeaderRenderer").firstOrNull()
        ?: return null
    return accountInfoFromHeader(header)
}

/** Nama/email/handle/foto dari satu `activeAccountHeaderRenderer`. */
private fun accountInfoFromHeader(header: JsonObject): AccountInfo? {
    val name = pickStr(header, "accountName.runs.0.text").takeIf { it.isNotBlank() } ?: return null
    return AccountInfo(
        name = name,
        email = pickStr(header, "email.runs.0.text").takeIf { it.isNotBlank() },
        channelHandle = pickStr(header, "channelHandle.runs.0.text").takeIf { it.isNotBlank() },
        thumbnailUrl = pickArr(header, "accountPhoto.thumbnails")
            .lastOrNull()
            ?.let { (it as? JsonObject)?.get("url") as? JsonPrimitive }
            ?.content
            ?.takeIf { it.isNotBlank() },
    )
}

/**
 * Akun terpilih dari respons `account/accounts_list` (PLAN
 * music-login-raw-error, fallback verifikasi login): pindai
 * `accountItemRenderer`, abaikan yang `isDisabled`, utamakan yang
 * `isSelected`. Nama = `accountName.runs[0].text` (jatuh ke `simpleText`
 * bila tidak ada) — wajib non-blank, bila tidak ada → null. Email dari
 * `email`/`accountByline` bila ada; foto = thumbnail terakhir.
 * `dataSyncId` dari `serviceEndpoint` renderer itu (normalisasi sama
 * seperti [parseAccountDataSyncId]), jatuh ke
 * `responseContext.mainAppWebResponseContext.dataSyncId` bila renderer
 * tidak membawanya. Null bila tidak ada akun terpilih sama sekali
 * (tidak ada fallback ke akun pertama — "terpilih" harus eksplisit).
 */
fun parseSelectedAccount(root: JsonElement): SelectedAccount? {
    val selected = findAccountItemRenderers(root)
        .filter { renderer ->
            val isDisabled = (renderer["isDisabled"] as? JsonPrimitive)?.booleanOrNull ?: false
            !isDisabled
        }
        .filter { renderer ->
            (renderer["isSelected"] as? JsonPrimitive)?.booleanOrNull ?: false
        }
        .mapNotNull { renderer -> selectedAccountFromRenderer(renderer, root) }
        .firstOrNull()
        ?: return null
    return selected
}

private fun selectedAccountFromRenderer(renderer: JsonObject, root: JsonElement): SelectedAccount? {
    val name = textValue(renderer["accountName"])?.takeIf { it.isNotBlank() } ?: return null
    val dataSyncId = findStringValue(
        renderer["serviceEndpoint"],
        setOf("datasyncId", "dataSyncId"),
    )?.normalizeAccountChannelDataSyncId()
        ?: findMainAppWebDataSyncId(root)
    return SelectedAccount(
        info = AccountInfo(
            name = name,
            email = textValue(renderer["email"]) ?: textValue(renderer["accountByline"]),
            channelHandle = textValue(renderer["channelHandle"]),
            thumbnailUrl = lastThumbnailUrl(renderer["accountPhoto"]),
        ),
        dataSyncId = dataSyncId,
    )
}

/** Akun terpilih + `dataSyncId`-nya, hasil `account/accounts_list`. */
data class SelectedAccount(
    val info: AccountInfo,
    val dataSyncId: String?,
)

/**
 * Teks dari objek teks InnerTube: `simpleText` dulu, bila tidak ada
 * gabungkan `runs[].text` (pola ArchiveTune `textValue`).
 */
private fun textValue(node: JsonElement?): String? {
    val obj = node as? JsonObject ?: return null
    (obj["simpleText"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    val runs = obj["runs"] as? JsonArray ?: return null
    return runs.joinToString(separator = "") { run ->
        ((run as? JsonObject)?.get("text") as? JsonPrimitive)?.content.orEmpty()
    }.trim().takeIf { it.isNotBlank() }
}

/** URL thumbnail terakhir, atau null bila tidak ada. */
private fun lastThumbnailUrl(node: JsonElement?): String? =
    ((node as? JsonObject)?.get("thumbnails") as? JsonArray)
        ?.mapNotNull { ((it as? JsonObject)?.get("url") as? JsonPrimitive)?.content }
        ?.lastOrNull()
        ?.takeIf { it.isNotBlank() }

/**
 * `dataSyncId` akun dari respons `account/accounts_list` (PLAN C.3).
 * Urutan: `responseContext.mainAppWebResponseContext.dataSyncId` dulu;
 * bila tidak ada, pindai `accountItemRenderer` (abaikan `isDisabled` dan
 * yang tanpa channel, utamakan `isSelected`), ambil `datasyncId` /
 * `dataSyncId` dari `serviceEndpoint`. Null bila tidak ada kandidat.
 * Nilai dinormalisasi seperti `PlaybackAuthState.normalized()` (bentuk
 * `delegated||user` dipertahankan, sisi kosong dibuang).
 */
fun parseAccountDataSyncId(root: JsonElement): String? {
    findMainAppWebDataSyncId(root)?.let { return it }
    return findAccountItemRenderers(root)
        .mapNotNull { renderer ->
            val isDisabled = (renderer["isDisabled"] as? JsonPrimitive)?.booleanOrNull ?: false
            val hasChannel = (renderer["hasChannel"] as? JsonPrimitive)?.booleanOrNull ?: true
            if (isDisabled || !hasChannel) return@mapNotNull null
            val raw = findStringValue(
                renderer["serviceEndpoint"],
                setOf("datasyncId", "dataSyncId"),
            ) ?: return@mapNotNull null
            val normalized = raw.normalizeAccountChannelDataSyncId() ?: return@mapNotNull null
            val isSelected = (renderer["isSelected"] as? JsonPrimitive)?.booleanOrNull ?: false
            normalized to isSelected
        }
        .sortedByDescending { (_, isSelected) -> isSelected }
        .firstOrNull()
        ?.first
}

private fun findMainAppWebDataSyncId(root: JsonElement): String? =
    pickStr(root, "responseContext.mainAppWebResponseContext.dataSyncId")
        .takeIf { it.isNotBlank() }
        ?.normalizeAccountChannelDataSyncId()

/** Semua objek bernama [name], sedalam apa pun (port ArchiveTune `objectsNamed`). */
private fun findObjectsNamed(node: JsonElement?, name: String): Sequence<JsonObject> =
    sequence {
        when (node) {
            is JsonObject -> {
                (node[name] as? JsonObject)?.let { yield(it) }
                node.values.forEach { yieldAll(findObjectsNamed(it, name)) }
            }
            is JsonArray -> node.forEach { yieldAll(findObjectsNamed(it, name)) }
            else -> {}
        }
    }

private fun findAccountItemRenderers(root: JsonElement): Sequence<JsonObject> =
    findObjectsNamed(root, "accountItemRenderer")

/**
 * Nilai string pertama untuk salah satu [keys], dicari sedalam apa pun di
 * bawah [node] (port ArchiveTune `findStringValue`): kunci langsung di
 * objek ini dulu, lalu rekursi ke nilai-nilainya.
 */
private fun findStringValue(node: JsonElement?, keys: Set<String>): String? =
    when (node) {
        is JsonObject -> {
            keys.firstNotNullOfOrNull { key ->
                (node[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
            } ?: node.values.firstNotNullOfOrNull { findStringValue(it, keys) }
        }
        is JsonArray -> node.firstNotNullOfOrNull { findStringValue(it, keys) }
        else -> null
    }

private fun String.normalizeAccountChannelDataSyncId(): String? =
    PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
