package com.rosyidputrar.taskify.core.store

/** User pemilik database lokal (diisi saat sesi aal2 dibuka). Dipakai untuk kolom `user_id`. */
fun interface UserIdProvider {
    fun currentUserId(): String?
}

class NotSignedInException : IllegalStateException("Belum masuk: tidak ada pemilik database lokal.")

fun UserIdProvider.require(): String = currentUserId() ?: throw NotSignedInException()
