package com.rosyidputrar.taskify.core.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.domain.auth.TRUST_MILLIS
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class DeviceStoresTest {
    private val prefs = DevicePrefs(ApplicationProvider.getApplicationContext()).also { it.prefs.edit().clear().commit() }

    @Test fun trustLasts30DaysAndCanBeCleared() {
        val store = MfaTrustStore(prefs)
        store.trust("u", now = 100)
        assertEquals("u", store.read()?.userId)
        assertEquals(100 + TRUST_MILLIS, store.read()?.untilMillis)
        store.clear()
        assertNull(store.read())
    }

    @Test fun loginNameCanBeTurnedOffWhichDeletesIt() {
        val store = LoginNameStore(prefs)
        store.write("  Rosyid  ")
        assertEquals("Rosyid", store.read())
        store.setEnabled(false, "Rosyid")
        assertNull(store.read())
        store.write("Lain")
        assertNull(store.read())
        store.setEnabled(true, "Rosyid")
        assertEquals("Rosyid", store.read())
    }

    @Test fun dbOwnerPersists() {
        prefs.dbOwner = "a"
        assertEquals("a", DevicePrefs(ApplicationProvider.getApplicationContext()).dbOwner)
        prefs.dbOwner = null
        assertNull(prefs.dbOwner)
    }
}
