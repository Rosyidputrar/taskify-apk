package com.rosyidputrar.taskify.feature.ai.tools

import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.CategorySlice
import com.rosyidputrar.taskify.core.domain.finance.PeriodTotals
import com.rosyidputrar.taskify.core.domain.finance.Transaction
import com.rosyidputrar.taskify.core.domain.finance.TxInput
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.finance.WalletType
import com.rosyidputrar.taskify.core.domain.journal.JournalEntry
import com.rosyidputrar.taskify.core.domain.notes.Note
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.SmartView
import com.rosyidputrar.taskify.core.store.tasks.TaskList
import com.rosyidputrar.taskify.feature.ai.Prepared
import com.rosyidputrar.taskify.feature.ai.ToolKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun task(id: String, title: String, due: String? = null, time: String? = null, priority: Int = 0, list: String? = null, status: TaskStatus = TaskStatus.Todo) =
    Task(id, "u", list, null, null, title, null, status, priority, due, time, null, null, null, 0.0, "", "", null)

private class FakeData : AiData {
    val tasksByView = mutableMapOf<SmartView, List<Task>>()
    val lists = mutableListOf(TaskList("l1", "Kantor", null, 0.0), TaskList("l2", "Rumah", null, 1.0))
    val wallets = mutableListOf(
        Wallet("w1", "Tunai", WalletType.Cash, 0, null, null, 0.0, false, balance = 150_000),
        Wallet("w2", "BCA", WalletType.Bank, 0, null, null, 1.0, false, balance = 2_500_000),
    )
    val categories = listOf(
        Category("c1", "Makan", CategoryKind.Expense, null, null, 0.0),
        Category("c2", "Transportasi", CategoryKind.Expense, null, null, 1.0),
        Category("c3", "Gaji", CategoryKind.Income, null, null, 0.0),
    )
    val searchRows = mutableListOf<EntityRow>()
    val notes = mutableListOf<Note>()
    val journal = mutableListOf<JournalEntry>()
    val tx = mutableListOf<Transaction>()
    val createdTasks = mutableListOf<NewTask>()
    val createdTx = mutableListOf<TxInput>()
    val createdNotes = mutableListOf<Triple<String?, String?, String>>()

    override suspend fun search(query: String, limit: Int) = searchRows.take(limit)
    override suspend fun tasks(view: SmartView, today: String) = tasksByView[view].orEmpty()
    override suspend fun task(id: String) = tasksByView.values.flatten().firstOrNull { it.id == id }
    override suspend fun subtasks(id: String) = emptyList<Task>()
    override suspend fun taskLists() = lists
    override suspend fun notes(query: String) = notes.filter { query.isBlank() || it.content.contains(query, true) }
    override suspend fun note(id: String) = notes.firstOrNull { it.id == id }
    override suspend fun journal(query: String) = journal
    override suspend fun journalEntry(id: String) = journal.firstOrNull { it.id == id }
    override suspend fun totals(from: String, to: String) = PeriodTotals(5_000_000, 1_250_000, 12)
    override suspend fun breakdown(from: String, to: String, kind: CategoryKind) =
        if (kind == CategoryKind.Expense) listOf(CategorySlice("c1", "Makan", null, null, 750_000, 9), CategorySlice("c2", "Transportasi", null, null, 500_000, 3)) else emptyList()
    override suspend fun wallets() = wallets
    override suspend fun categories(kind: CategoryKind) = categories.filter { it.kind == kind }
    override suspend fun budgets(month: String, monthLast: String) = listOf(BudgetStatus("b1", "c1", 1_000_000, 80, month, null, "Makan", null, null, 750_000))
    override suspend fun transactions(from: String, to: String, kind: TxKind?, query: String, limit: Int) = tx.filter { kind == null || it.kind == kind }.take(limit)
    override suspend fun transaction(id: String) = tx.firstOrNull { it.id == id }
    override suspend fun createTask(input: NewTask) = "t-new".also { createdTasks += input }
    override suspend fun createTx(input: TxInput) = "x-new".also { createdTx += input }
    override suspend fun createNote(date: String?, title: String?, content: String) = "n-new".also { createdNotes += Triple(date, title, content) }
}

class TaskifyToolboxTest {
    private val data = FakeData()
    private val box = TaskifyToolbox(data, today = { "2026-11-20" })

    private fun ready(p: Prepared): Map<String, String> {
        assertIs<Prepared.Ready>(p)
        return Json.parseToJsonElement(p.arguments).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    private fun invalidText(p: Prepared): String {
        assertIs<Prepared.Invalid>(p)
        assertTrue(p.result.failed)
        return p.result.text
    }

    @Test fun specsAreWellFormedAndKindsAreRight() {
        val specs = box.specs()
        assertEquals(10, specs.size)
        assertEquals(specs.size, specs.map { it.name }.toSet().size)
        specs.forEach { s ->
            assertTrue(Regex("^[a-z_]+$").matches(s.name), s.name)
            assertEquals("object", s.parameters["type"]!!.jsonPrimitive.content)
            assertTrue(s.description.isNotBlank())
        }
        assertEquals(setOf("create_task", "add_transaction", "create_note"), specs.filter { box.kind(it.name) == ToolKind.Write }.map { it.name }.toSet())
        // Tanggal hari ini ada di deskripsi alat yang butuh tanggal.
        assertTrue(specs.first { it.name == "create_task" }.description.contains("2026-11-20"))
        // Tidak ada alat untuk Sandi / Brankas.
        assertFalse(specs.any { it.name.contains("sandi") || it.name.contains("password") || it.name.contains("vault") })
        assertNull(box.kind("baca_sandi"))
    }

    @Test fun createTaskIsNormalizedAndPreviewed() = runTest {
        val args = ready(box.prepare("create_task", """{"title":"  Bayar listrik ","due_date":"2026-11-21","due_time":"9.00","priority":"tinggi","list":"kantor"}"""))
        assertEquals(mapOf("title" to "Bayar listrik", "due_date" to "2026-11-21", "due_time" to "09:00", "priority" to "3", "list_id" to "l1", "list" to "Kantor"), args)
        val json = (box.prepare("create_task", """{"title":"Bayar listrik","due_date":"2026-11-21","due_time":"09:00","priority":3,"list":"Kantor"}""") as Prepared.Ready).arguments
        val preview = box.preview("create_task", json)
        assertEquals("Buat tugas?", preview.title)
        assertEquals(
            listOf("Judul" to "Bayar listrik", "Tenggat" to "Sab, 21 Nov 2026 09.00", "Prioritas" to "Tinggi", "Daftar" to "Kantor"),
            preview.lines,
        )
        val result = box.run("create_task", json)
        assertFalse(result.failed)
        assertEquals(NewTask("Bayar listrik", listId = "l1", dueDate = "2026-11-21", dueTime = "09:00", priority = 3), data.createdTasks.single())
    }

    @Test fun createTaskRejectsBadInput() = runTest {
        assertTrue(invalidText(box.prepare("create_task", """{"title":"  "}""")).contains("Judul"))
        assertTrue(invalidText(box.prepare("create_task", """{"title":"A","due_date":"besok"}""")).contains("YYYY-MM-DD"))
        assertTrue(invalidText(box.prepare("create_task", """{"title":"A","due_date":"2026-02-30"}""")).contains("2026-02-30"))
        assertTrue(invalidText(box.prepare("create_task", """{"title":"A","due_time":"25:00"}""")).contains("HH:MM"))
        assertTrue(invalidText(box.prepare("create_task", """{"title":"A","priority":7}""")).contains("0–3"))
        // Daftar tak dikenal → model diberi daftar yang ada.
        assertTrue(invalidText(box.prepare("create_task", """{"title":"A","list":"Sekolah"}""")).contains("Kantor, Rumah"))
        assertTrue(invalidText(box.prepare("create_task", "bukan json")).contains("JSON"))
        // Jam tanpa tanggal → hari ini.
        assertEquals("2026-11-20", ready(box.prepare("create_task", """{"title":"A","due_time":"14:30"}"""))["due_date"])
        assertTrue(data.createdTasks.isEmpty())
    }

    @Test fun transactionResolvesWalletAndCategory() = runTest {
        val args = ready(box.prepare("add_transaction", """{"kind":"expense","amount":"25rb","title":"Makan siang","category":"makan","wallet":"bca"}"""))
        assertEquals("25000", args["amount"])
        assertEquals("w2" to "BCA", args["wallet_id"] to args["wallet"])
        assertEquals("c1" to "Makan", args["category_id"] to args["category"])
        assertEquals("2026-11-20", args["date"]) // bawaan hari ini
        // Tanpa dompet → dompet pertama; tanpa kategori → tanpa kategori.
        val plain = ready(box.prepare("add_transaction", """{"kind":"income","amount":5000000}"""))
        assertEquals("w1", plain["wallet_id"])
        assertNull(plain["category_id"])
        // Nominal desimal dari model dibulatkan.
        assertEquals("15000", ready(box.prepare("add_transaction", """{"kind":"expense","amount":15000.0}"""))["amount"])

        val json = (box.prepare("add_transaction", """{"kind":"expense","amount":25000,"title":"Makan siang","category":"Makan","wallet":"BCA","time":"12:15"}""") as Prepared.Ready).arguments
        val preview = box.preview("add_transaction", json)
        assertEquals("Catat pengeluaran?", preview.title)
        assertEquals(
            listOf("Nominal" to "Rp 25.000", "Judul" to "Makan siang", "Tanggal" to "Jum, 20 Nov 2026 12.15", "Kategori" to "Makan", "Dompet" to "BCA"),
            preview.lines,
        )
        val r = box.run("add_transaction", json)
        assertFalse(r.failed)
        assertTrue(r.text.contains("Rp 25.000") && r.text.contains("BCA"))
        assertEquals(
            TxInput(TxKind.Expense, 25_000, "w2", categoryId = "c1", occurredOn = "2026-11-20", occurredTime = "12:15:00", title = "Makan siang"),
            data.createdTx.single(),
        )
    }

    @Test fun transactionRejectsBadInput() = runTest {
        assertTrue(invalidText(box.prepare("add_transaction", """{"kind":"transfer","amount":1000}""")).contains("Transfer"))
        assertTrue(invalidText(box.prepare("add_transaction", """{"kind":"expense"}""")).contains("amount"))
        assertTrue(invalidText(box.prepare("add_transaction", """{"kind":"expense","amount":0}""")).contains("lebih dari 0"))
        assertTrue(invalidText(box.prepare("add_transaction", """{"kind":"expense","amount":-5}""")).contains("lebih dari 0"))
        assertTrue(invalidText(box.prepare("add_transaction", """{"kind":"expense","amount":1000,"wallet":"Mandiri"}""")).contains("Tunai, BCA"))
        // Kategori pemasukan tidak boleh dipakai untuk pengeluaran.
        assertTrue(invalidText(box.prepare("add_transaction", """{"kind":"expense","amount":1000,"category":"Gaji"}""")).contains("Makan, Transportasi"))
        data.wallets.clear()
        assertTrue(invalidText(box.prepare("add_transaction", """{"kind":"expense","amount":1000}""")).contains("belum punya dompet"))
        assertTrue(data.createdTx.isEmpty())
    }

    @Test fun runRechecksBecauseDataMayHaveChanged() = runTest {
        val json = (box.prepare("add_transaction", """{"kind":"expense","amount":1000,"wallet":"BCA"}""") as Prepared.Ready).arguments
        data.wallets.removeAll { it.id == "w2" } // dompet dihapus sebelum disetujui
        val r = box.run("add_transaction", json)
        assertTrue(r.failed)
        assertTrue(data.createdTx.isEmpty())
    }

    @Test fun createNote() = runTest {
        val json = (box.prepare("create_note", """{"title":"Ide","content":"- satu\n- dua\n","date":"2026-11-20"}""") as Prepared.Ready).arguments
        val p = box.preview("create_note", json)
        assertEquals(listOf("Judul" to "Ide", "Tanggal" to "Jum, 20 Nov 2026", "Isi" to "- satu\n- dua"), p.lines)
        assertFalse(box.run("create_note", json).failed)
        assertEquals(Triple("2026-11-20", "Ide", "- satu\n- dua"), data.createdNotes.single())
        assertTrue(invalidText(box.prepare("create_note", """{"title":"x"}""")).contains("content"))
        assertTrue(invalidText(box.prepare("create_note", Json.encodeToString(mapOf("content" to "a".repeat(20_001))))).contains("terlalu panjang"))
    }

    @Test fun listTasksIsCompactAndBounded() = runTest {
        data.tasksByView[SmartView.Today] = listOf(task("t1", "Bayar listrik", "2026-11-20", "09:00:00", 3, "l1"), task("t2", "Olahraga", "2026-11-20", status = TaskStatus.Done))
        val r = box.run("list_tasks", """{"view":"today"}""")
        assertEquals("2 tugas", r.summary)
        assertTrue(r.text.contains("- Bayar listrik (tenggat 2026-11-20 09:00, prioritas tinggi, daftar Kantor, belum) · id=t1"), r.text)
        assertTrue(r.text.contains("Olahraga (tenggat 2026-11-20, selesai)"))
        data.tasksByView[SmartView.All] = (1..80).map { task("t$it", "Tugas $it") }
        val many = box.run("list_tasks", """{"view":"all","limit":500}""")
        assertEquals(50, many.text.lines().count { it.startsWith("- ") })
        assertTrue(many.text.contains("dan 30 tugas lain"))
        assertTrue(box.run("list_tasks", """{"view":"kemarin"}""").failed)
    }

    @Test fun searchHidesVaultAndFormatsTransactions() = runTest {
        data.searchRows += EntityRow(EntityType.Snippet, "s1", "kunci server", null)
        data.searchRows += EntityRow(EntityType.Transaction, "x1", "Token listrik", "2026-11-18|expense|200000", snippet = "beli ⟪token⟫ listrik")
        data.searchRows += EntityRow(EntityType.Task, "t1", "Bayar listrik", "2026-11-21")
        val r = box.run("search", """{"query":"listrik"}""")
        assertEquals("2 hasil", r.summary)
        assertFalse(r.text.contains("kunci server"))
        assertTrue(r.text.contains("[transaksi] Token listrik (2026-11-18, pengeluaran Rp 200.000) — \"beli token listrik\" · id=x1"), r.text)
        assertTrue(r.text.contains("[tugas] Bayar listrik (2026-11-21) · id=t1"))
        assertTrue(box.run("search", "{}").failed)
    }

    @Test fun financeSummaryDefaultsToThisMonth() = runTest {
        val r = box.run("finance_summary", "{}")
        assertFalse(r.failed)
        assertTrue(r.text.startsWith("Keuangan 2026-11-01 s.d. 2026-11-30"), r.text)
        assertTrue(r.text.contains("- Pengeluaran: Rp 1.250.000"))
        assertTrue(r.text.contains("- Makan: Rp 750.000 (60%, 9 trx)"))
        assertTrue(r.text.contains("Saldo dompet saat ini (total Rp 2.650.000)"))
        assertTrue(r.text.contains("- Makan: terpakai Rp 750.000 dari Rp 1.000.000 (75%)"))
        assertTrue(box.run("finance_summary", """{"from":"2026-12-01","to":"2026-11-01"}""").failed)
    }

    @Test fun readItemAndNotesAndJournal() = runTest {
        data.notes += Note("n1", null, "Belanja", "telur\nsusu", pinned = true, createdAt = "", updatedAt = "")
        data.journal += JournalEntry("j1", "2026-11-19", null, "Hari yang tenang.", 4, false, "", "")
        data.journal += JournalEntry("j0", "2026-10-01", null, "Lama.", null, false, "", "")
        assertEquals("Catatan: Belanja\n\ntelur\nsusu", box.run("read_item", """{"type":"note","id":"n1"}""").text)
        assertTrue(box.run("read_item", """{"type":"note","id":"nope"}""").failed)
        assertTrue(box.run("read_item", """{"type":"password","id":"n1"}""").failed)
        assertTrue(box.run("list_notes", "{}").text.contains("📌 Belanja — \"telur susu\" · id=n1"))
        val j = box.run("list_journal", """{"from":"2026-11-01"}""")
        assertEquals("1 jurnal", j.summary)
        assertTrue(j.text.contains("2026-11-19 · mood Baik — \"Hari yang tenang.\""), j.text)
    }

    @Test fun transactionsListDefaultsToLast30Days() = runTest {
        data.tx += Transaction("x1", TxKind.Expense, 20_000, "w1", null, "c1", "2026-11-19", "12:00:00", "Bakso", null, categoryName = "Makan", walletName = "Tunai")
        val r = box.run("list_transactions", "{}")
        assertTrue(r.text.startsWith("Transaksi 2026-10-22 s.d. 2026-11-20"), r.text)
        assertTrue(r.text.contains("- 2026-11-19 12:00 · pengeluaran Rp 20.000 · Bakso · kategori Makan · Tunai · id=x1"), r.text)
        assertTrue(box.run("list_transactions", """{"kind":"hutang"}""").failed)
    }
}
