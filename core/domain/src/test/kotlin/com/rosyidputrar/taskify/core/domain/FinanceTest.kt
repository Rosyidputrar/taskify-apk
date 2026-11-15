package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.capture.CaptureType
import com.rosyidputrar.taskify.core.domain.capture.matchCategory
import com.rosyidputrar.taskify.core.domain.capture.parseCapture
import com.rosyidputrar.taskify.core.domain.finance.BudgetLevel
import com.rosyidputrar.taskify.core.domain.finance.Csv
import com.rosyidputrar.taskify.core.domain.finance.FinanceException
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.RecurringSchedule
import com.rosyidputrar.taskify.core.domain.finance.Transaction
import com.rosyidputrar.taskify.core.domain.finance.TxInput
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.budgetLevel
import com.rosyidputrar.taskify.core.domain.finance.deterministicUuid
import com.rosyidputrar.taskify.core.domain.finance.dueOccurrences
import com.rosyidputrar.taskify.core.domain.finance.firstOccurrenceFrom
import com.rosyidputrar.taskify.core.domain.finance.groupByDay
import com.rosyidputrar.taskify.core.domain.finance.monthlyNet
import com.rosyidputrar.taskify.core.domain.finance.normalizeTx
import com.rosyidputrar.taskify.core.domain.finance.occurrenceAfter
import com.rosyidputrar.taskify.core.domain.finance.transactionsCsv
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceFreq
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceState
import com.rosyidputrar.taskify.core.domain.tasks.buildRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kasus dari `recurring.test.ts` + aturan `finance-actions.ts` webapp. */
class FinanceRecurringTest {
    private val rule31 = buildRule(RecurrenceState(RecurrenceFreq.Monthly), "2026-01-31")!!
    private val schedule = RecurringSchedule(rule31, "2026-01-31", null)

    @Test fun billOn31stNeverSkipsShortMonths() {
        val due = dueOccurrences(schedule.copy(nextRunOn = "2026-01-31"), "2026-10-07")
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30", "2026-07-31", "2026-08-31", "2026-09-30"),
            due.dates,
        )
        assertEquals("2026-10-31", due.next)
    }

    @Test fun firstAndNextFollowMonthEnd() {
        assertEquals("2026-02-28", firstOccurrenceFrom(schedule, "2026-02-01"))
        assertEquals("2026-03-31", occurrenceAfter(schedule, "2026-02-28"))
        assertNull(occurrenceAfter(schedule.copy(endOn = "2026-03-01"), "2026-02-28"))
        assertEquals(emptyList(), dueOccurrences(schedule.copy(nextRunOn = "2026-11-30"), "2026-10-07").dates)
    }

    @Test fun deterministicUuidMatchesWebapp() {
        val a = deterministicUuid("taskify:recurring:r1:2026-02-28")
        assertEquals(a, deterministicUuid("taskify:recurring:r1:2026-02-28"))
        assertNotEquals(a, deterministicUuid("taskify:recurring:r1:2026-03-31"))
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-8[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(a))
        // Nilai referensi dihitung dengan algoritma webapp (SHA-256 → UUID v8) di Node.
        assertEquals("5fe044dd-ac4b-89ad-b382-8209bb3479f3", a)
        assertEquals("cb866f57-d7ec-89dd-a19b-da4e377f5c0a", deterministicUuid("taskify:debt-open:abc"))
    }

    @Test fun monthlyNetIgnoresTransfersAndOtherFrequencies() {
        fun r(kind: TxKind, amount: Long, rule: String, active: Boolean = true) =
            Recurring("x", kind, amount, "w", null, null, "t", null, rule, "2026-01-01", null, null, false, active)
        val items = listOf(
            r(TxKind.Income, 5_000_000, "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=25"),
            r(TxKind.Expense, 186_000, "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=1"),
            r(TxKind.Transfer, 1_000_000, "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=1"),
            r(TxKind.Expense, 99_000, "FREQ=YEARLY;INTERVAL=1"),
            r(TxKind.Expense, 50_000, "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=1", active = false),
        )
        assertEquals(4_814_000, monthlyNet(items))
    }
}

class FinanceRulesTest {
    @Test fun normalizeEnforcesServerChecks() {
        assertFailsWith<FinanceException> { normalizeTx(TxInput(TxKind.Expense, 0, "w", occurredOn = "2026-10-07")) }
        assertFailsWith<FinanceException> { normalizeTx(TxInput(TxKind.Transfer, 10, "w", toWalletId = "w", occurredOn = "2026-10-07")) }
        val t = normalizeTx(TxInput(TxKind.Transfer, 10, "w", toWalletId = "x", categoryId = "c", occurredOn = "2026-10-07", occurredTime = "09:30", title = "  ", note = " "))
        assertNull(t.categoryId)
        assertNull(t.title)
        assertNull(t.note)
        assertEquals("09:30:00", t.occurredTime)
        val e = normalizeTx(TxInput(TxKind.Expense, 10, "w", toWalletId = "x", categoryId = "c", occurredOn = "2026-10-07", title = "a".repeat(200)))
        assertNull(e.toWalletId)
        assertEquals(120, e.title!!.length)
    }

    @Test fun budgetLevels() {
        assertEquals(BudgetLevel.Ok, budgetLevel(700, 1000, 80))
        assertEquals(BudgetLevel.Warn, budgetLevel(800, 1000, 80))
        assertEquals(BudgetLevel.Over, budgetLevel(1000, 1000, 80))
    }

    private fun tx(id: String, kind: TxKind, amount: Long, date: String, title: String? = null, category: String? = null, debt: String? = null) =
        Transaction(id, kind, amount, "w", null, null, date, null, title, null, debtId = debt, categoryName = category, walletName = "Tunai")

    @Test fun titlesGroupingAndCsv() {
        assertEquals("Makan", tx("1", TxKind.Expense, 1, "d", category = "Makan").displayTitle)
        assertEquals("Pemasukan", tx("1", TxKind.Income, 1, "d").displayTitle)
        assertEquals("Transfer", tx("1", TxKind.Transfer, 1, "d", category = "x").displayTitle)
        val days = groupByDay(listOf(
            tx("a", TxKind.Expense, 20_000, "2026-10-07"), tx("b", TxKind.Income, 5_000, "2026-10-07"),
            tx("c", TxKind.Expense, 99_000, "2026-10-07", debt = "d"), tx("d", TxKind.Expense, 1_000, "2026-10-06"),
        ))
        assertEquals(listOf("2026-10-07", "2026-10-06"), days.map { it.date })
        assertEquals(20_000, days[0].expense) // transaksi hutang tidak dihitung
        assertEquals(5_000, days[0].income)

        val csv = transactionsCsv(listOf(tx("a", TxKind.Expense, 20_000, "2026-10-07", title = "=HYPERLINK(\"x\")", category = "Makan; minum")))
        val line = csv.lines()[1]
        assertTrue(line.startsWith("2026-10-07;;Pengeluaran;\"'=HYPERLINK(\"\"x\"\")\";\"Makan; minum\";Tunai;;-20000;"))
        assertEquals("-5000", Csv.build(listOf("a"), listOf(listOf("-5000"))).lines()[1])
    }
}

/** Kasus dari `parse-capture.test.ts` webapp. */
class ParseCaptureTest {
    private val today = "2026-10-07"

    @Test fun journalAndNote() {
        assertEquals(CaptureType.Journal, parseCapture("jurnal: halo", today).type)
        parseCapture("c: ide export #app #Fitur", today).let {
            assertEquals(CaptureType.Note, it.type)
            assertEquals("ide export", it.title)
            assertEquals(listOf("app", "fitur"), it.tags)
            assertNull(it.date)
        }
        assertEquals("ide fitur", parseCapture("c: ide  fitur   #app", today).rawText)
        assertEquals("anggaran 25rb per hari", parseCapture("c: anggaran 25rb per hari", today).title)
        assertEquals(CaptureType.Task, parseCapture("baca catatan rapat besok", today).type)
    }

    @Test fun transactions() {
        parseCapture("makan siang 25rb kemarin", today).let {
            assertEquals(CaptureType.Expense, it.type)
            assertEquals("makan siang", it.title)
            assertEquals(25_000, it.amount)
            assertEquals("2026-10-06", it.date)
            assertEquals("makan", it.categoryHint)
        }
        assertEquals(CaptureType.Income, parseCapture("+5jt gaji oktober", today).type)
        assertEquals(5_000_000, parseCapture("+5jt gaji oktober", today).amount)
        assertEquals(1_250_000, parseCapture("servis motor 1.250.000", today).amount)
        assertEquals(2_500_000, parseCapture("tiket konser 2500000", today).amount)
        assertEquals(45_000, parseCapture("beli 3 buku 45rb", today).amount)
        assertEquals("beli 3 buku 45rb", parseCapture("beli 3 buku 45rb", today).rawText)
        assertEquals(CaptureType.Task, parseCapture("target tahun 2027 tercapai", today).type)
        parseCapture("bensin 50rb hari ini", today).let { assertEquals("bensin", it.title); assertEquals(today, it.date) }
        assertEquals(CaptureType.Expense, parseCapture("beli kado 100rb untuk ulang tahun besok", today).type)
        assertEquals(CaptureType.Income, parseCapture("+5jt gaji minggu depan", today).type)
    }

    @Test fun numbersThatAreNotMoney() {
        for (s in listOf("hubungi 081234567890", "wa ke 6281234567890", "hubungi +6281234567890")) {
            assertEquals(CaptureType.Task, parseCapture(s, today).type, s)
            assertNull(parseCapture(s, today).amount, s)
        }
        parseCapture("tulis artikel 1000 kata", today).let { assertEquals(CaptureType.Task, it.type); assertEquals("tulis artikel 1000 kata", it.title) }
        parseCapture("tulis 1000 kata lalu beli kopi 25rb", today).let { assertEquals(CaptureType.Expense, it.type); assertEquals(25_000, it.amount) }
    }

    @Test fun tasksKeepRawText() {
        parseCapture("besok rapat jam 9 #kerja", today).let {
            assertEquals(CaptureType.Task, it.type)
            assertEquals("rapat", it.title)
            assertEquals("besok rapat jam 9", it.rawText)
            assertEquals(listOf("kerja"), it.tags)
        }
    }

    @Test fun categoryMatching() {
        data class C(val name: String, val kind: String)
        val cats = listOf(C("Makan & Minum", "expense"), C("Transportasi", "expense"), C("Gaji", "income"))
        assertEquals("Makan & Minum", matchCategory(cats, { it.kind }, { it.name }, "expense", "makan", "makan siang")?.name)
        assertEquals("Transportasi", matchCategory(cats, { it.kind }, { it.name }, "expense", null, "transport kantor")?.name)
        assertEquals("Gaji", matchCategory(cats, { it.kind }, { it.name }, "income", "gaji", "gaji")?.name)
        assertNull(matchCategory(cats, { it.kind }, { it.name }, "expense", null, "xx"))
    }
}
