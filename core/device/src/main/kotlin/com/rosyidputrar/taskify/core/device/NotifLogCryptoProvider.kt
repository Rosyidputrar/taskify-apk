package com.rosyidputrar.taskify.core.device

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.rosyidputrar.taskify.core.store.device.AesGcmNotifLogCrypto
import com.rosyidputrar.taskify.core.store.device.NotifLogCrypto
import com.rosyidputrar.taskify.core.store.device.PlainNotifLogCrypto
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * D-06: kunci AES-256-GCM untuk log notifikasi, disimpan di AndroidKeyStore (tidak bisa
 * diekstrak). Kunci dibuat sekali saat pertama dipakai; bila Keystore tidak tersedia
 * (perangkat tanpa Keystore), jatuh ke [PlainNotifLogCrypto] supaya pencatatan tetap jalan
 * tanpa enkripsi — lebih baik mencatat daripada kehilangan log.
 */
fun notifLogCrypto(): NotifLogCrypto = try {
    AesGcmNotifLogCrypto(notifLogKey())
} catch (_: Exception) {
    PlainNotifLogCrypto
}

private fun notifLogKey(): SecretKey {
    val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
    (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
    }.generateKey()
}

private const val PROVIDER = "AndroidKeyStore"
private const val ALIAS = "taskify_notif_log"
