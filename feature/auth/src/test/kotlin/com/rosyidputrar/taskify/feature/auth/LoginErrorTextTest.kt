package com.rosyidputrar.taskify.feature.auth

import com.rosyidputrar.taskify.core.domain.auth.AuthFailure
import com.rosyidputrar.taskify.core.domain.auth.AuthMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginErrorTextTest {
    @Test fun knownFailuresStayFriendly() {
        assertEquals(AuthMessages.WRONG_LOGIN, loginErrorText(AuthFailure(false, 400, "invalid_credentials", "Invalid login credentials")))
        assertEquals(AuthMessages.OFFLINE_LOGIN, loginErrorText(AuthFailure(true, null, null, "Unable to resolve host")))
    }

    @Test fun unknownFailuresShowRawDetail() {
        // Mis. Keystore/serialisasi yang dulu membuat tombol "Masuk" berputar selamanya.
        val text = loginErrorText(AuthFailure(false, null, "KeyStoreException", "Keystore operation failed"))
        assertTrue("KeyStoreException" in text && "Keystore operation failed" in text, text)
        val server = loginErrorText(AuthFailure(false, 500, "unexpected_failure", "Database error querying schema"))
        assertTrue("HTTP 500" in server && "unexpected_failure" in server && "Database error querying schema" in server, server)
    }

    @Test fun timeoutExplainsWhatHappened() {
        val text = loginErrorText(AuthFailure(true, null, "timeout", "Server tidak menjawab dalam 30 detik."))
        assertTrue(text.startsWith(AuthMessages.OFFLINE_LOGIN), text)
        assertTrue("Server tidak menjawab dalam 30 detik." in text, text)
    }
}
