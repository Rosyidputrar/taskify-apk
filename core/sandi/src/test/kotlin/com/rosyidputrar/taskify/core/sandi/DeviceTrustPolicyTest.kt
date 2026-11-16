package com.rosyidputrar.taskify.core.sandi

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceTrustPolicyTest {
    @Test fun android11AndUpBindsVerificationToTheCipher() {
        assertTrue(DeviceTrustPolicy.usesCryptoObject(30))
        assertTrue(DeviceTrustPolicy.usesCryptoObject(36))
        // Biometrik KUAT atau kredensial layar; biometrik lemah tidak bisa membuka kunci Keystore.
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, DeviceTrustPolicy.authenticators(30))
    }

    @Test fun olderAndroidUsesShortTimeBoundKey() {
        assertFalse(DeviceTrustPolicy.usesCryptoObject(26))
        assertFalse(DeviceTrustPolicy.usesCryptoObject(29))
        // BIOMETRIC_STRONG | DEVICE_CREDENTIAL tidak didukung BiometricPrompt di API 28–29.
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, DeviceTrustPolicy.authenticators(29))
        assertTrue(DeviceTrustPolicy.VALIDITY_SECONDS in 5..30)
    }

    @Test fun closingTheDialogIsNotAnError() {
        assertTrue(DeviceTrustPolicy.isCancel(BiometricPrompt.ERROR_USER_CANCELED))
        assertTrue(DeviceTrustPolicy.isCancel(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertTrue(DeviceTrustPolicy.isCancel(BiometricPrompt.ERROR_CANCELED))
        assertFalse(DeviceTrustPolicy.isCancel(BiometricPrompt.ERROR_LOCKOUT))
        assertFalse(DeviceTrustPolicy.isCancel(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL))
    }
}
