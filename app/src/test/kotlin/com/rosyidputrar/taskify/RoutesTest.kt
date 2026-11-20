package com.rosyidputrar.taskify

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.navigation.ModuleRoute
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A8 — `ModuleRoute.module` parse aman: nama modul yang dikenal → modulnya, nama basi
 * (shortcut launcher dari sebelum modul diganti nama/dihapus) → null, bukan crash `valueOf`.
 */
@RunWith(AndroidJUnit4::class)
class RoutesTest {
    @Test fun knownModuleParses() {
        assertEquals(TaskifyModule.Tasks, ModuleRoute("Tasks").module)
        assertEquals(TaskifyModule.Finance, ModuleRoute("Finance").module)
        assertEquals(TaskifyModule.Home, ModuleRoute("Home").module)
    }

    @Test fun unknownModuleReturnsNullInsteadOfCrashing() {
        assertNull(ModuleRoute("TidakAdaLagi").module)
        assertNull(ModuleRoute("").module)
        assertNull(ModuleRoute("tasks").module, "nama modul peka huruf besar-kecil")
    }
}
