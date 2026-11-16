package com.rosyidputrar.taskify.feature.vault

import com.rosyidputrar.taskify.feature.vault.config.ConfigEditorUiState
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigDirtyTest {
    @Test fun newFileIsDirtyOnceSomethingIsTyped() {
        val fresh = ConfigEditorUiState(isNew = true, loaded = true)
        assertFalse(fresh.dirty)
        assertTrue(fresh.copy(text = "API_KEY=1").dirty)
        assertTrue(fresh.copy(name = ".env").dirty)
    }

    @Test fun existingFileComparesWithLatestVersion() {
        val opened = ConfigEditorUiState(isNew = false, loaded = true, text = "A=1", baseText = "A=1")
        assertFalse(opened.dirty)
        assertTrue(opened.copy(text = "A=2").dirty)
        assertTrue(opened.copy(note = "ganti kunci").dirty)
        assertFalse(opened.copy(loaded = false, text = "").dirty, "belum dimuat = tidak ada yang hilang")
    }
}
