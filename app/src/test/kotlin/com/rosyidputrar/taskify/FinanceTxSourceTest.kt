package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.feature.finance.component.TxRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Transaksi yang dicatat dari notifikasi memakai ikon aplikasi asalnya di daftar Keuangan; kategori
 * tetap terlihat sebagai lencana kecil. Transaksi biasa tetap memakai ikon kategori.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class FinanceTxSourceTest {
    @get:Rule val compose = createComposeRule()

    private val fromBca = SampleFinance.transactions[0].copy(
        id = "n1", title = "Transfer ke Andi", amount = 150_000, kind = TxKind.Expense, sourcePackage = "com.bca", sourceLabel = "BCA mobile",
    )
    private val fromDanaNoCategory = SampleFinance.transactions[0].copy(
        id = "n2", title = "Top up", categoryId = null, categoryName = null, categoryIcon = null, categoryColor = null,
        sourcePackage = "id.dana", sourceLabel = "DANA",
    )
    private val manual = SampleFinance.transactions[0]

    @Test fun rowAnnouncesTheSourceApp() {
        compose.setContent { TaskifyTheme { TxRow(fromBca, onOpen = {}, onDelete = null) } }
        compose.onNodeWithContentDescription("dari BCA mobile", substring = true).assertExists()
        // Ikon aplikasi tidak tersedia di tes → huruf awal aplikasi, bukan ikon kategori.
        compose.onNodeWithText("B", useUnmergedTree = true).assertExists()
    }

    @Test fun manualTransactionsKeepCategoryIcon() {
        compose.setContent { TaskifyTheme { TxRow(manual, onOpen = {}, onDelete = null) } }
        compose.onNodeWithContentDescription("dari", substring = true).assertDoesNotExist()
    }

    @Test fun screenshot() {
        compose.setContent {
            TaskifyTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        TxRow(fromBca, onOpen = {}, onDelete = null)
                        TxRow(fromDanaNoCategory, onOpen = {}, onDelete = null)
                        TxRow(manual, onOpen = {}, onDelete = null)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/finance_tx_source_light.png")
    }
}
