package com.rosyidputrar.taskify.core.domain.media

/**
 * Identitas akun YouTube yang sedang login, dibaca dari
 * `account/account_menu` (PLAN C.1). [name] wajib ada — bila respons tidak
 * berisi nama akun, login dianggap gagal (bukan akun anonim).
 */
data class AccountInfo(
    val name: String,
    val email: String? = null,
    val channelHandle: String? = null,
    val thumbnailUrl: String? = null,
)
