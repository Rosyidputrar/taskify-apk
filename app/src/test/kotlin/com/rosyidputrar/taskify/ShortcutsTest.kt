package com.rosyidputrar.taskify

import android.app.Application
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.component.RecentEntities
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.widget.CaptureTileService
import com.rosyidputrar.taskify.widget.EntityShortcuts
import com.rosyidputrar.taskify.widget.ModuleShortcuts
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShortcutsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private fun ids() = ShortcutManagerCompat.getDynamicShortcuts(app).map { it.id }.toSet()

    @After fun reset() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(app)
        RecentEntities.install { _, _, _ -> }
    }

    @Test fun recentlyOpenedNoteBecomesALauncherShortcut() {
        EntityShortcuts.report(app, "note", "n1", "Ide fitur ekspor PDF", pinActive = false)
        val s = ShortcutManagerCompat.getDynamicShortcuts(app).single { it.id == "entity-note-n1" }
        assertEquals("Ide fitur ekspor PDF", s.shortLabel.toString())
        assertEquals(EntityType.Note to "n1", EntityShortcuts.entityOf(s.intent))
    }

    @Test fun nothingIsExposedWhilePinIsOn() {
        EntityShortcuts.report(app, "journal_entry", "j1", "Hari yang berat", pinActive = true)
        assertTrue(ids().none { it.startsWith("entity-") })
    }

    @Test fun clearingRemovesTitlesButKeepsModuleShortcuts() {
        ModuleShortcuts.report(app, TaskifyModule.Finance)
        EntityShortcuts.report(app, "task", "t1", "Bayar listrik", pinActive = false)
        EntityShortcuts.clear(app)
        assertEquals(setOf("module-Finance"), ids())
    }

    @Test fun unknownOrForeignIntentsAreIgnored() {
        assertNull(EntityShortcuts.entityOf(Intent("lain")))
        assertNull(EntityShortcuts.entityOf(Intent(EntityShortcuts.ACTION_OPEN_ENTITY).putExtra(EntityShortcuts.EXTRA_TYPE, "rahasia").putExtra(EntityShortcuts.EXTRA_ID, "x")))
    }

    @Test fun reporterIgnoresBlankTitles() {
        val seen = mutableListOf<String>()
        RecentEntities.install { _, id, _ -> seen += id }
        RecentEntities.report("note", "n1", "  ")
        RecentEntities.report("note", "n2", "Belanja")
        assertEquals(listOf("n2"), seen)
    }

    @Test fun captureTileIsRegistered() {
        val services = app.packageManager.queryIntentServices(Intent(TileService.ACTION_QS_TILE).setPackage(app.packageName), 0)
        assertTrue(services.any { it.serviceInfo.name == CaptureTileService::class.java.name })
        assertEquals(android.Manifest.permission.BIND_QUICK_SETTINGS_TILE, services.first { it.serviceInfo.name == CaptureTileService::class.java.name }.serviceInfo.permission)
    }
}
