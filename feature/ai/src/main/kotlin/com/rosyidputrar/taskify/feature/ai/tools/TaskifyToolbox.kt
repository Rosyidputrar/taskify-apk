package com.rosyidputrar.taskify.feature.ai.tools

import com.rosyidputrar.taskify.core.ai.AiToolSpec
import com.rosyidputrar.taskify.core.common.Limits
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.Transaction
import com.rosyidputrar.taskify.core.domain.finance.TxInput
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.journal.moodLabel
import com.rosyidputrar.taskify.core.domain.search.Search
import com.rosyidputrar.taskify.core.domain.tasks.PRIORITIES
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.priorityLabel
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.SmartView
import com.rosyidputrar.taskify.feature.ai.AiToolbox
import com.rosyidputrar.taskify.feature.ai.Prepared
import com.rosyidputrar.taskify.feature.ai.ToolKind
import com.rosyidputrar.taskify.feature.ai.ToolPreview
import com.rosyidputrar.taskify.feature.ai.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

/**
 * Alat asisten untuk data Taskify.
 *
 * Baca (langsung jalan): cari, daftar tugas, buka item, catatan, jurnal, ringkasan & daftar transaksi.
 * Tulis (selalu dikonfirmasi): buat tugas, catat transaksi, buat catatan. Argumen tulis diperiksa
 * & dinormalkan di [prepare] (nama dompet/kategori/daftar → id), jadi kartu konfirmasi menampilkan
 * persis apa yang akan disimpan. Hasil baca dibatasi ukurannya; Sandi tidak pernah tersedia.
 */
class TaskifyToolbox(
    private val data: AiData,
    private val today: () -> String = { Dates.today() },
) : AiToolbox {

    private class Tool(val kind: ToolKind, val label: String, val description: () -> String, val parameters: () -> JsonObject)

    private val tools: Map<String, Tool> = linkedMapOf(
        SEARCH to Tool(ToolKind.Read, "Mencari data", {
            "Cari teks di tugas, catatan, jurnal, dan transaksi pengguna. Mengembalikan judul, tanggal, potongan teks, dan id (untuk read_item)."
        }) {
            schema(listOf("query"), "query" to str("Kata kunci, mis. \"listrik\" atau \"rapat tim\""), "limit" to int("Jumlah hasil maks (bawaan 10, maks 20)"))
        },
        LIST_TASKS to Tool(ToolKind.Read, "Membaca tugas", {
            "Daftar tugas pengguna per tampilan. today = jatuh tempo hari ini, upcoming = setelah hari ini, overdue = terlambat, all = semua yang belum selesai, done = yang sudah selesai (terbaru dulu). Hari ini ${today()}."
        }) {
            schema(listOf("view"), "view" to str("Tampilan", listOf("today", "upcoming", "overdue", "all", "done")), "limit" to int("Jumlah maks (bawaan 25, maks 50)"))
        },
        READ_ITEM to Tool(ToolKind.Read, "Membuka item", {
            "Baca isi lengkap satu item berdasarkan id dari hasil alat lain."
        }) {
            schema(listOf("type", "id"), "type" to str("Jenis item", listOf("task", "note", "journal", "transaction")), "id" to str("Id item"))
        },
        LIST_NOTES to Tool(ToolKind.Read, "Membaca catatan", {
            "Daftar catatan pengguna (yang disematkan dulu, lalu terbaru), opsional disaring kata kunci. Isi dipotong; pakai read_item untuk isi penuh."
        }) {
            schema(emptyList(), "query" to str("Kata kunci (opsional)"), "limit" to int("Jumlah maks (bawaan 15, maks 30)"))
        },
        LIST_JOURNAL to Tool(ToolKind.Read, "Membaca jurnal", {
            "Daftar entri jurnal harian pengguna beserta mood, terbaru dulu. Rentang tanggal opsional (YYYY-MM-DD). Hari ini ${today()}."
        }) {
            schema(
                emptyList(), "from" to str("Tanggal awal YYYY-MM-DD (opsional)"), "to" to str("Tanggal akhir YYYY-MM-DD (opsional)"),
                "query" to str("Kata kunci (opsional)"), "limit" to int("Jumlah maks (bawaan 10, maks 30)"),
            )
        },
        FINANCE_SUMMARY to Tool(ToolKind.Read, "Membaca ringkasan keuangan", {
            "Ringkasan keuangan dalam rentang tanggal: total pemasukan, pengeluaran, selisih, pengeluaran per kategori, saldo tiap dompet, dan anggaran bulan itu. Bawaan: bulan ini. Hari ini ${today()}. Semua nominal dalam Rupiah."
        }) {
            schema(emptyList(), "from" to str("Tanggal awal YYYY-MM-DD"), "to" to str("Tanggal akhir YYYY-MM-DD"))
        },
        LIST_TRANSACTIONS to Tool(ToolKind.Read, "Membaca transaksi", {
            "Daftar transaksi (terbaru dulu) dalam rentang tanggal, opsional per jenis atau kata kunci. Bawaan: 30 hari terakhir. Hari ini ${today()}."
        }) {
            schema(
                emptyList(), "from" to str("Tanggal awal YYYY-MM-DD"), "to" to str("Tanggal akhir YYYY-MM-DD"),
                "kind" to str("Jenis", listOf("expense", "income", "transfer")), "query" to str("Kata kunci judul/catatan/kategori"),
                "limit" to int("Jumlah maks (bawaan 20, maks 50)"),
            )
        },
        CREATE_TASK to Tool(ToolKind.Write, "Buat tugas", {
            "Buat tugas baru. Pengguna akan melihat dan menyetujuinya dulu. Hari ini ${today()} (${Dates.format(today(), "EEEE")})."
        }) {
            schema(
                listOf("title"),
                "title" to str("Judul tugas, singkat dan jelas"),
                "due_date" to str("Tanggal tenggat YYYY-MM-DD (opsional)"),
                "due_time" to str("Jam tenggat HH:MM 24 jam (opsional, butuh due_date)"),
                "priority" to int("Prioritas: 0 tanpa, 1 rendah, 2 sedang, 3 tinggi"),
                "notes" to str("Catatan tambahan (opsional)"),
                "list" to str("Nama daftar tugas (opsional; harus sudah ada)"),
            )
        },
        ADD_TRANSACTION to Tool(ToolKind.Write, "Catat transaksi", {
            "Catat pemasukan atau pengeluaran. Pengguna akan melihat dan menyetujuinya dulu. Bila dompet tidak disebut, dipakai dompet pertama. Hari ini ${today()}."
        }) {
            schema(
                listOf("kind", "amount"),
                "kind" to str("Jenis", listOf("expense", "income")),
                "amount" to int("Nominal dalam Rupiah, bilangan bulat (mis. 25000)"),
                "title" to str("Judul singkat, mis. \"Makan siang\""),
                "date" to str("Tanggal YYYY-MM-DD (bawaan hari ini)"),
                "time" to str("Jam HH:MM (opsional)"),
                "category" to str("Nama kategori yang sudah ada (opsional)"),
                "wallet" to str("Nama dompet yang sudah ada (opsional)"),
                "note" to str("Catatan (opsional)"),
            )
        },
        CREATE_NOTE to Tool(ToolKind.Write, "Buat catatan", {
            "Buat catatan baru (Markdown). Pengguna akan melihat dan menyetujuinya dulu."
        }) {
            schema(
                listOf("content"), "title" to str("Judul (opsional)"), "content" to str("Isi catatan dalam Markdown"),
                "date" to str("Tempelkan ke tanggal YYYY-MM-DD (opsional)"),
            )
        },
    )

    override fun specs() = tools.map { (name, t) -> AiToolSpec(name, t.description(), t.parameters()) }
    override fun kind(name: String) = tools[name]?.kind
    override fun label(name: String) = tools[name]?.label ?: name

    // ================================================================== tulis: periksa & pratinjau

    override suspend fun prepare(name: String, arguments: String): Prepared {
        val a = Args.parse(arguments) ?: return invalid("Argumen bukan JSON objek yang valid.")
        return when (name) {
            CREATE_TASK -> prepareTask(a)
            ADD_TRANSACTION -> prepareTx(a)
            CREATE_NOTE -> prepareNote(a)
            else -> Prepared.Ready(arguments)
        }
    }

    private suspend fun prepareTask(a: Args): Prepared {
        val title = a.str("title") ?: return invalid("Judul tugas wajib diisi.")
        var date = a.str("due_date")?.let { parseDate(it) ?: return invalid("due_date \"$it\" bukan tanggal YYYY-MM-DD yang valid.") }
        val time = a.str("due_time")?.let { parseTime(it) ?: return invalid("due_time \"$it\" bukan jam HH:MM yang valid.") }
        if (time != null && date == null) date = today()
        val priority = a.long("priority")?.toInt() ?: priorityFromText(a.str("priority")) ?: 0
        if (priority !in 0..3) return invalid("priority harus 0–3.")
        val lists = data.taskLists()
        val list = a.str("list_id")?.let { id -> lists.firstOrNull { it.id == id } }
            ?: a.str("list")?.let { name ->
                pick(lists, name) { it.name } ?: return invalid("Daftar \"$name\" tidak ada. Daftar yang ada: ${lists.joinToString { it.name }.ifEmpty { "(belum ada)" }}. Kosongkan list bila tidak yakin.")
            }
        return ready {
            put("title", Limits.clip(title, Limits.TASK_TITLE))
            date?.let { put("due_date", it) }
            time?.let { put("due_time", it) }
            put("priority", priority)
            a.str("notes")?.let { put("notes", it.take(MAX_TEXT)) }
            list?.let { put("list_id", it.id); put("list", it.name) }
        }
    }

    private suspend fun prepareTx(a: Args): Prepared {
        val kind = when (a.str("kind")?.lowercase()) {
            "expense", "pengeluaran" -> TxKind.Expense
            "income", "pemasukan" -> TxKind.Income
            else -> return invalid("kind harus \"expense\" atau \"income\". Transfer antar dompet belum didukung lewat asisten.")
        }
        val amount = a.money("amount") ?: return invalid("amount wajib berupa nominal Rupiah, mis. 25000.")
        if (amount <= 0 || amount > MAX_AMOUNT) return invalid("Nominal harus lebih dari 0 dan masuk akal (maks ${Rupiah.format(MAX_AMOUNT)}).")
        val date = a.str("date")?.let { parseDate(it) ?: return invalid("date \"$it\" bukan tanggal YYYY-MM-DD yang valid.") } ?: today()
        val time = a.str("time")?.let { parseTime(it) ?: return invalid("time \"$it\" bukan jam HH:MM yang valid.") }
        val wallets = data.wallets()
        if (wallets.isEmpty()) return invalid("Pengguna belum punya dompet. Minta pengguna membuat dompet dulu di Keuangan.")
        val wallet = a.str("wallet_id")?.let { id -> wallets.firstOrNull { it.id == id } }
            ?: a.str("wallet")?.let { name ->
                pick(wallets, name) { it.name } ?: return invalid("Dompet \"$name\" tidak ada. Dompet yang ada: ${wallets.joinToString { it.name }}.")
            }
            ?: wallets.first()
        val categories = data.categories(if (kind == TxKind.Income) CategoryKind.Income else CategoryKind.Expense)
        val category = a.str("category_id")?.let { id -> categories.firstOrNull { it.id == id } }
            ?: a.str("category")?.let { name ->
                pick(categories, name) { it.name } ?: return invalid(
                    "Kategori ${kind.label.lowercase()} \"$name\" tidak ada. Pilih salah satu: ${categories.joinToString { it.name }.ifEmpty { "(belum ada)" }}, atau kosongkan.",
                )
            }
        return ready {
            put("kind", kind.value)
            put("amount", amount)
            a.str("title")?.let { put("title", Limits.clip(it, Limits.TRANSACTION_TITLE)) }
            put("date", date)
            time?.let { put("time", it) }
            put("wallet_id", wallet.id); put("wallet", wallet.name)
            category?.let { put("category_id", it.id); put("category", it.name) }
            a.str("note")?.let { put("note", it.take(MAX_TEXT)) }
        }
    }

    private fun prepareNote(a: Args): Prepared {
        val content = a.str("content", trim = false)?.trimEnd() ?: return invalid("Isi catatan (content) wajib diisi.")
        if (content.length > MAX_NOTE) return invalid("Catatan terlalu panjang (maks $MAX_NOTE karakter). Ringkas dulu.")
        val date = a.str("date")?.let { parseDate(it) ?: return invalid("date \"$it\" bukan tanggal YYYY-MM-DD yang valid.") }
        return ready {
            a.str("title")?.let { put("title", Limits.clip(it, Limits.NOTE_TITLE)) }
            put("content", content)
            date?.let { put("date", it) }
        }
    }

    override fun preview(name: String, arguments: String): ToolPreview {
        val a = Args.parse(arguments) ?: return ToolPreview(label(name) + "?")
        return when (name) {
            CREATE_TASK -> ToolPreview(
                "Buat tugas?",
                listOfNotNull(
                    "Judul" to (a.str("title") ?: "—"),
                    a.str("due_date")?.let { d -> "Tenggat" to (dayLabel(d) + (a.str("due_time")?.let { " ${it.replace(':', '.')}" } ?: "")) },
                    a.long("priority")?.toInt()?.takeIf { it > 0 }?.let { "Prioritas" to priorityLabel(it) },
                    a.str("list")?.let { "Daftar" to it },
                    a.str("notes")?.let { "Catatan" to clip(it, 140) },
                ),
            )
            ADD_TRANSACTION -> {
                val income = a.str("kind") == TxKind.Income.value
                ToolPreview(
                    if (income) "Catat pemasukan?" else "Catat pengeluaran?",
                    listOfNotNull(
                        "Nominal" to (a.long("amount")?.let { Rupiah.format(it) } ?: "—"),
                        a.str("title")?.let { "Judul" to it },
                        "Tanggal" to (a.str("date")?.let(::dayLabel) ?: "Hari ini") + (a.str("time")?.let { " ${it.replace(':', '.')}" } ?: ""),
                        "Kategori" to (a.str("category") ?: "Tanpa kategori"),
                        "Dompet" to (a.str("wallet") ?: "Dompet pertama"),
                        a.str("note")?.let { "Catatan" to clip(it, 140) },
                    ),
                )
            }
            CREATE_NOTE -> ToolPreview(
                "Buat catatan?",
                listOfNotNull(
                    "Judul" to (a.str("title") ?: "Tanpa judul"),
                    a.str("date")?.let { "Tanggal" to dayLabel(it) },
                    "Isi" to clip(a.str("content") ?: "", 220),
                ),
            )
            else -> ToolPreview(label(name))
        }
    }

    // ================================================================== jalankan

    override suspend fun run(name: String, arguments: String): ToolResult {
        val a = Args.parse(arguments) ?: return fail("Argumen bukan JSON objek yang valid.")
        return when (name) {
            SEARCH -> search(a)
            LIST_TASKS -> listTasks(a)
            READ_ITEM -> readItem(a)
            LIST_NOTES -> listNotes(a)
            LIST_JOURNAL -> listJournal(a)
            FINANCE_SUMMARY -> financeSummary(a)
            LIST_TRANSACTIONS -> listTransactions(a)
            // Tulis: periksa ulang (data bisa berubah sejak kartu konfirmasi tampil).
            CREATE_TASK, ADD_TRANSACTION, CREATE_NOTE -> when (val p = prepare(name, arguments)) {
                is Prepared.Invalid -> p.result
                is Prepared.Ready -> write(name, Args.parse(p.arguments)!!)
            }
            else -> fail("Alat \"$name\" tidak ada.")
        }
    }

    private suspend fun write(name: String, a: Args): ToolResult = when (name) {
        CREATE_TASK -> {
            val id = data.createTask(
                NewTask(
                    title = a.str("title")!!, listId = a.str("list_id"), dueDate = a.str("due_date"), dueTime = a.str("due_time"),
                    priority = a.long("priority")?.toInt() ?: 0, notes = a.str("notes"),
                ),
            )
            ToolResult("Tugas \"${a.str("title")}\" berhasil dibuat (id=$id).", "tugas dibuat")
        }
        ADD_TRANSACTION -> {
            val kind = TxKind.from(a.str("kind"))
            val amount = a.long("amount")!!
            val id = data.createTx(
                TxInput(
                    kind = kind, amount = amount, walletId = a.str("wallet_id")!!, categoryId = a.str("category_id"),
                    occurredOn = a.str("date")!!, occurredTime = a.str("time")?.let { "$it:00" }, title = a.str("title"), note = a.str("note"),
                ),
            )
            ToolResult("${kind.label} ${Rupiah.format(amount)} tercatat di dompet ${a.str("wallet")} (id=$id).", "${kind.label.lowercase()} dicatat")
        }
        else -> {
            val id = data.createNote(a.str("date"), a.str("title"), a.str("content", trim = false)!!)
            ToolResult("Catatan berhasil dibuat (id=$id).", "catatan dibuat")
        }
    }

    // ================================================================== baca

    private suspend fun search(a: Args): ToolResult {
        val query = a.str("query") ?: return fail("query wajib diisi.")
        val limit = a.limit(10, 20)
        val rows = data.search(query, limit * 2).filter { it.type in READABLE }.take(limit)
        if (rows.isEmpty()) return ToolResult("Tidak ada hasil untuk \"$query\".", "0 hasil")
        val text = rows.joinToString("\n") { r ->
            val sub = when (r.type) {
                EntityType.Transaction -> r.subtitle?.split('|')?.takeIf { it.size == 3 }?.let { (d, k, amt) -> "$d, ${TxKind.from(k).label.lowercase()} ${Rupiah.format(amt.toLongOrNull() ?: 0)}" }
                else -> r.subtitle
            }
            val snippet = r.snippet?.replace(Search.MARK_OPEN, "")?.replace(Search.MARK_CLOSE, "")?.let { " — \"${oneLine(it)}\"" } ?: ""
            "- [${typeName(r.type)}] ${r.title}${sub?.let { " ($it)" } ?: ""}$snippet · id=${r.id}"
        }
        return ok("${rows.size} hasil untuk \"$query\":\n$text", "${rows.size} hasil")
    }

    private suspend fun listTasks(a: Args): ToolResult {
        val view = when (a.str("view")?.lowercase()) {
            "today" -> SmartView.Today
            "upcoming" -> SmartView.Upcoming
            "overdue" -> SmartView.Overdue
            "done" -> SmartView.Done
            "all", null -> SmartView.All
            else -> return fail("view harus salah satu: today, upcoming, overdue, all, done.")
        }
        val limit = a.limit(25, 50)
        val all = data.tasks(view, today())
        if (all.isEmpty()) return ok("Tidak ada tugas di tampilan \"${view.label}\".", "0 tugas")
        val lists = data.taskLists().associate { it.id to it.name }
        val text = all.take(limit).joinToString("\n") { taskLine(it, lists) }
        val more = if (all.size > limit) "\n(… dan ${all.size - limit} tugas lain)" else ""
        return ok("${all.size} tugas di \"${view.label}\" (hari ini ${today()}):\n$text$more", "${all.size} tugas")
    }

    private suspend fun readItem(a: Args): ToolResult {
        val id = a.str("id") ?: return fail("id wajib diisi.")
        return when (a.str("type")?.lowercase()) {
            "task" -> data.task(id)?.let { t ->
                val lists = data.taskLists().associate { it.id to it.name }
                val subs = data.subtasks(id)
                val body = buildString {
                    appendLine("Tugas: ${t.title}")
                    appendLine("Status: ${statusName(t)}")
                    t.dueDate?.let { appendLine("Tenggat: $it${t.dueTime?.let { tm -> " ${tm.take(5)}" } ?: ""}") }
                    if (t.priority > 0) appendLine("Prioritas: ${priorityLabel(t.priority)}")
                    t.listId?.let(lists::get)?.let { appendLine("Daftar: $it") }
                    if (t.isRecurring) appendLine("Berulang: ${t.rrule}")
                    if (subs.isNotEmpty()) appendLine("Subtugas:\n" + subs.joinToString("\n") { "- [${if (it.isDone) "x" else " "}] ${it.title}" })
                    t.notes?.takeIf { it.isNotBlank() }?.let { append("Catatan:\n$it") }
                }
                ok(body.trimEnd(), "1 tugas")
            }
            "note" -> data.note(id)?.let { n ->
                ok("Catatan: ${n.title?.ifBlank { null } ?: "(tanpa judul)"}${n.noteDate?.let { "\nTanggal: $it" } ?: ""}\n\n${n.content}", "1 catatan")
            }
            "journal", "journal_entry" -> data.journalEntry(id)?.let { j ->
                ok(
                    "Jurnal ${j.entryDate}${j.title?.ifBlank { null }?.let { ": $it" } ?: ""}${moodLabel(j.mood)?.let { "\nMood: $it" } ?: ""}\n\n${j.content}",
                    "1 jurnal",
                )
            }
            "transaction" -> data.transaction(id)?.let { ok(txLine(it, detailed = true), "1 transaksi") }
            else -> return fail("type harus salah satu: task, note, journal, transaction.")
        } ?: fail("Item tidak ditemukan (mungkin sudah dihapus).")
    }

    private suspend fun listNotes(a: Args): ToolResult {
        val limit = a.limit(15, 30)
        val notes = data.notes(a.str("query") ?: "")
        if (notes.isEmpty()) return ok("Tidak ada catatan${a.str("query")?.let { " yang cocok dengan \"$it\"" } ?: ""}.", "0 catatan")
        val text = notes.take(limit).joinToString("\n") { n ->
            val title = n.title?.ifBlank { null } ?: "(tanpa judul)"
            "- ${if (n.pinned) "📌 " else ""}$title${n.noteDate?.let { " ($it)" } ?: ""} — \"${clip(oneLine(n.content), 160)}\" · id=${n.id}"
        }
        val more = if (notes.size > limit) "\n(… dan ${notes.size - limit} catatan lain)" else ""
        return ok("${notes.size} catatan:\n$text$more", "${notes.size} catatan")
    }

    private suspend fun listJournal(a: Args): ToolResult {
        val from = a.str("from")?.let { parseDate(it) ?: return fail("from bukan tanggal YYYY-MM-DD.") }
        val to = a.str("to")?.let { parseDate(it) ?: return fail("to bukan tanggal YYYY-MM-DD.") }
        val limit = a.limit(10, 30)
        val entries = data.journal(a.str("query") ?: "")
            .filter { (from == null || it.entryDate >= from) && (to == null || it.entryDate <= to) }
            .sortedWith(compareByDescending<com.rosyidputrar.taskify.core.domain.journal.JournalEntry> { it.entryDate }.thenByDescending { it.createdAt })
        if (entries.isEmpty()) return ok("Tidak ada entri jurnal di rentang itu.", "0 jurnal")
        val text = entries.take(limit).joinToString("\n") { j ->
            "- ${j.entryDate}${moodLabel(j.mood)?.let { " · mood $it" } ?: ""}${j.title?.ifBlank { null }?.let { " · $it" } ?: ""} — \"${clip(oneLine(j.content), 280)}\" · id=${j.id}"
        }
        val more = if (entries.size > limit) "\n(… dan ${entries.size - limit} entri lain)" else ""
        return ok("${entries.size} entri jurnal:\n$text$more", "${entries.size} jurnal")
    }

    private suspend fun financeSummary(a: Args): ToolResult {
        val (from, to) = range(a, defaultFrom = Dates.monthStart(today()), defaultTo = Dates.monthEnd(today())) ?: return fail("from/to harus tanggal YYYY-MM-DD dan from ≤ to.")
        val totals = data.totals(from, to)
        val expense = data.breakdown(from, to, CategoryKind.Expense)
        val income = data.breakdown(from, to, CategoryKind.Income)
        val wallets = data.wallets()
        val month = Dates.monthStart(from)
        val budgets = data.budgets(month, Dates.monthEnd(month))
        val text = buildString {
            appendLine("Keuangan $from s.d. $to (tanpa transfer & hutang):")
            appendLine("- Pemasukan: ${Rupiah.format(totals.income)}")
            appendLine("- Pengeluaran: ${Rupiah.format(totals.expense)}")
            appendLine("- Selisih: ${Rupiah.format(totals.net)}")
            appendLine("- Jumlah transaksi: ${totals.count}")
            if (expense.isNotEmpty()) {
                appendLine("Pengeluaran per kategori:")
                expense.take(10).forEach { appendLine("- ${it.name}: ${Rupiah.format(it.total)} (${pct(it.total, totals.expense)}, ${it.count} trx)") }
                if (expense.size > 10) appendLine("- … ${expense.size - 10} kategori lain")
            }
            if (income.isNotEmpty()) {
                appendLine("Pemasukan per kategori:")
                income.take(6).forEach { appendLine("- ${it.name}: ${Rupiah.format(it.total)}") }
            }
            if (wallets.isNotEmpty()) {
                appendLine("Saldo dompet saat ini (total ${Rupiah.format(wallets.sumOf { it.balance })}):")
                wallets.forEach { appendLine("- ${it.name} (${it.type.label}): ${Rupiah.format(it.balance)}") }
            }
            if (budgets.isNotEmpty()) {
                appendLine("Anggaran ${month.take(7)}:")
                budgets.forEach { appendLine("- ${it.name}: terpakai ${Rupiah.format(it.spent)} dari ${Rupiah.format(it.amount)} (${pct(it.spent, it.amount)})") }
            }
        }
        return ok(text.trimEnd(), "${totals.count} transaksi")
    }

    private suspend fun listTransactions(a: Args): ToolResult {
        val (from, to) = range(a, defaultFrom = Dates.addDays(today(), -29), defaultTo = today()) ?: return fail("from/to harus tanggal YYYY-MM-DD dan from ≤ to.")
        val kind = when (a.str("kind")?.lowercase()) {
            null -> null
            "expense" -> TxKind.Expense
            "income" -> TxKind.Income
            "transfer" -> TxKind.Transfer
            else -> return fail("kind harus expense, income, atau transfer.")
        }
        val limit = a.limit(20, 50)
        val rows = data.transactions(from, to, kind, a.str("query") ?: "", limit + 1)
        if (rows.isEmpty()) return ok("Tidak ada transaksi $from s.d. $to.", "0 transaksi")
        val text = rows.take(limit).joinToString("\n") { "- " + txLine(it, detailed = false) }
        val more = if (rows.size > limit) "\n(… masih ada transaksi lain; persempit rentang atau pakai finance_summary)" else ""
        return ok("Transaksi $from s.d. $to:\n$text$more", "${rows.take(limit).size} transaksi")
    }

    // ================================================================== bantu

    private fun taskLine(t: Task, lists: Map<String, String>): String {
        val parts = buildList {
            t.dueDate?.let { add("tenggat $it${t.dueTime?.let { tm -> " ${tm.take(5)}" } ?: ""}") }
            if (t.priority > 0) add("prioritas ${priorityLabel(t.priority).lowercase()}")
            t.listId?.let(lists::get)?.let { add("daftar $it") }
            if (t.isRecurring) add("berulang")
            add(statusName(t))
        }
        return "- ${t.title} (${parts.joinToString(", ")}) · id=${t.id}"
    }

    private fun txLine(t: Transaction, detailed: Boolean): String {
        val what = t.title?.ifBlank { null } ?: t.categoryName ?: t.kind.label
        val wallet = if (t.kind == TxKind.Transfer) "${t.walletName} → ${t.toWalletName}" else t.walletName
        val base = "${t.occurredOn}${t.occurredTime?.let { " ${it.take(5)}" } ?: ""} · ${t.kind.label.lowercase()} ${Rupiah.format(t.amount)} · $what" +
            (t.categoryName?.takeIf { it != what }?.let { " · kategori $it" } ?: "") + (wallet?.let { " · $it" } ?: "") + " · id=${t.id}"
        return if (detailed && !t.note.isNullOrBlank()) "$base\nCatatan: ${t.note}" else base
    }

    private fun statusName(t: Task) = when (t.status.db) {
        "done" -> "selesai"
        "cancelled" -> "dibatalkan"
        "in_progress" -> "sedang dikerjakan"
        else -> "belum"
    }

    private fun range(a: Args, defaultFrom: String, defaultTo: String): Pair<String, String>? {
        val from = a.str("from")?.let { parseDate(it) ?: return null } ?: defaultFrom
        val to = a.str("to")?.let { parseDate(it) ?: return null } ?: defaultTo
        return if (from <= to) from to to else null
    }

    private fun dayLabel(date: String) = runCatching { Dates.format(date, "EEE, d MMM yyyy") }.getOrDefault(date)

    private fun ready(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = Prepared.Ready(buildJsonObject(block).toString())

    private fun ok(text: String, summary: String) = ToolResult(clip(text, MAX_RESULT), summary)

    companion object {
        const val SEARCH = "search"
        const val LIST_TASKS = "list_tasks"
        const val READ_ITEM = "read_item"
        const val LIST_NOTES = "list_notes"
        const val LIST_JOURNAL = "list_journal"
        const val FINANCE_SUMMARY = "finance_summary"
        const val LIST_TRANSACTIONS = "list_transactions"
        const val CREATE_TASK = "create_task"
        const val ADD_TRANSACTION = "add_transaction"
        const val CREATE_NOTE = "create_note"

        /** Jenis entitas yang boleh muncul di hasil cari (Brankas kode tidak termasuk). */
        private val READABLE = setOf(EntityType.Task, EntityType.Note, EntityType.JournalEntry, EntityType.Transaction)
        private const val MAX_RESULT = 7_500
        private const val MAX_TEXT = 2_000
        private const val MAX_NOTE = 20_000
        private const val MAX_AMOUNT = 100_000_000_000L

        private fun invalid(message: String) = Prepared.Invalid(fail(message))
        private fun fail(message: String) = ToolResult(message, "tidak valid", failed = true)

        private fun typeName(t: EntityType) = when (t) {
            EntityType.Task -> "tugas"
            EntityType.Note -> "catatan"
            EntityType.JournalEntry -> "jurnal"
            EntityType.Transaction -> "transaksi"
            else -> t.value
        }

        private fun pct(part: Long, whole: Long) = if (whole <= 0) "—" else "${Math.round(part * 100.0 / whole)}%"
        private fun oneLine(s: String) = s.replace(Regex("\\s+"), " ").trim()
        private fun clip(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"

        internal fun parseDate(raw: String): String? = runCatching { LocalDate.parse(raw.trim().take(10)).toString() }.getOrNull()

        internal fun parseTime(raw: String): String? {
            val m = Regex("^(\\d{1,2})[:.](\\d{2})(?::\\d{2})?$").matchEntire(raw.trim()) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            return if (h in 0..23 && min in 0..59) "%02d:%02d".format(h, min) else null
        }

        private fun priorityFromText(raw: String?): Int? = raw?.let { r -> PRIORITIES.firstOrNull { it.label.equals(r, ignoreCase = true) }?.value }
            ?: when (raw?.lowercase()) { "low" -> 1; "medium" -> 2; "high" -> 3; "none" -> 0; else -> null }

        /** Cocokkan nama: sama persis (tanpa beda huruf besar) dulu, lalu satu-satunya yang memuatnya. */
        private fun <T> pick(items: List<T>, name: String, nameOf: (T) -> String): T? {
            val n = name.trim()
            items.firstOrNull { nameOf(it).equals(n, ignoreCase = true) }?.let { return it }
            return items.filter { nameOf(it).contains(n, ignoreCase = true) || n.contains(nameOf(it), ignoreCase = true) }.singleOrNull()
        }

        private fun schema(required: List<String>, vararg props: Pair<String, JsonObject>) = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
            putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        }

        private fun str(description: String, enum: List<String>? = null) = buildJsonObject {
            put("type", "string")
            put("description", description)
            if (enum != null) putJsonArray("enum") { enum.forEach { add(JsonPrimitive(it)) } }
        }

        private fun int(description: String) = buildJsonObject {
            put("type", "integer")
            put("description", description)
        }
    }
}

/** Argumen alat dari model — toleran: angka boleh berupa string, string kosong = tidak ada. */
internal class Args(private val o: JsonObject) {
    fun str(key: String, trim: Boolean = true): String? {
        val p = o[key] as? JsonPrimitive ?: return null
        val s = p.contentOrNull ?: return null
        return (if (trim) s.trim() else s).takeIf { it.isNotBlank() }
    }

    fun long(key: String): Long? {
        val p = o[key] as? JsonPrimitive ?: return null
        return p.longOrNull ?: p.doubleOrNull?.let { Math.round(it) } ?: p.contentOrNull?.trim()?.toLongOrNull()
    }

    /** Nominal: angka JSON, atau teks gaya Indonesia ("25.000", "25rb", "1,5jt"). */
    fun money(key: String): Long? {
        val p = o[key] as? JsonPrimitive ?: return null
        if (!p.isString) return p.longOrNull ?: p.doubleOrNull?.let { Math.round(it) }
        return p.contentOrNull?.let { Rupiah.parse(it) }
    }

    fun limit(default: Int, max: Int): Int = (long("limit")?.toInt() ?: default).coerceIn(1, max)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(raw: String): Args? {
            if (raw.isBlank()) return Args(JsonObject(emptyMap()))
            val el: JsonElement = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
            return runCatching { Args(el.jsonObject) }.getOrNull()
        }
    }
}
