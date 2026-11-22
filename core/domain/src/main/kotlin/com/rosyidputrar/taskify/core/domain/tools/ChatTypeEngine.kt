package com.rosyidputrar.taskify.core.domain.tools

import kotlin.math.max
import kotlin.math.roundToInt

/** Tiga tema visual Arena ChatType. */
enum class ChatVisualTheme(val label: String, val tagline: String) {
    Paper("Kertas & Tinta", "Hangat khas Taskify"),
    Messenger("Messenger", "Gelembung chat modern"),
    Terminal("Terminal", "Monospace matriks gelap"),
}

/** Tingkat kesulitan kalimat balasan yang harus diketik. */
enum class TypingDifficulty(val label: String, val hint: String, val minChars: Int, val maxChars: Int) {
    Casual("Santai", "Kalimat pendek sehari-hari", 35, 70),
    Standard("Menengah", "Alur natural & tanda baca", 65, 115),
    Intense("Ekstrem", "Cepat, angka & istilah teknis", 100, 165),
}

/** Satu putaran percakapan: pesan lawan bicara → target balasan yang harus diketik pengguna. */
data class ChatTurn(
    val partnerMessage: String,
    val targetReply: String,
)

/** Satu skenario alur cerita percakapan (bawaan maupun hasil generate AI). */
data class ChatStory(
    val id: String,
    val title: String,
    val category: String,
    val partnerName: String,
    val partnerRole: String,
    val difficulty: TypingDifficulty,
    val suggestedTheme: ChatVisualTheme,
    val turns: List<ChatTurn>,
    val isAiGenerated: Boolean = false,
)

/** Status tiap karakter pada bayangan target ketik (Ghost Prompt). */
enum class CharState { Correct, Wrong, Cursor, Pending }

data class CharDiff(val char: Char, val state: CharState)

/** Pembaruan state ketikan per perubahan teks input. */
data class TypingStepUpdate(
    val totalKeystrokes: Int,
    val errorKeystrokes: Int,
    val combo: Int,
    val maxCombo: Int,
    val missedChars: Map<Char, Int>,
    /** True bila ketukan terakhir adalah huruf yang salah → picu getar `haptics.error()` & animasi shake. */
    val typoTriggered: Boolean,
    /** True bila ketukan ini tepat menembus kelipatan 25 combo → picu `haptics.tick()` & animasi pulse. */
    val comboMilestone: Boolean,
    /** True bila seluruh kalimat target sudah selesai diketik 100% benar → kirim bubble. */
    val completed: Boolean,
)

/** Metrik untuk satu bubble balasan yang selesai diketik. */
data class TurnMetrics(
    val wpm: Int,
    val cpm: Int,
    val accuracyPct: Int,
    val elapsedMs: Long,
    val keystrokes: Int,
    val errors: Int,
)

/** Ringkasan evaluasi di akhir satu cerita percakapan. */
data class SessionSummary(
    val averageWpm: Int,
    val peakWpm: Int,
    val averageCpm: Int,
    val accuracyPct: Int,
    val totalElapsedMs: Long,
    val totalKeystrokes: Int,
    val totalErrors: Int,
    val maxCombo: Int,
    val isNewRecord: Boolean,
    val previousBestWpm: Int?,
    /** Daftar karakter yang paling sering salah ketik (label ramah, mis. "koma (,)", "huruf 'e'"). */
    val topMissedLabels: List<String>,
)

object ChatTypeEngine {
    const val COMBO_MILESTONE_STEP = 25

    /** Bandingkan kalimat target dengan input saat ini per karakter untuk pewarnaan real-time. */
    fun diff(target: String, typed: String): List<CharDiff> {
        val result = ArrayList<CharDiff>(target.length)
        var prefixOk = true
        for (i in target.indices) {
            val expected = target[i]
            val state = when {
                i < typed.length -> {
                    val actual = typed[i]
                    if (prefixOk && actual == expected) {
                        CharState.Correct
                    } else {
                        prefixOk = false
                        CharState.Wrong
                    }
                }
                i == typed.length -> CharState.Cursor
                else -> CharState.Pending
            }
            result.add(CharDiff(expected, state))
        }
        return result
    }

    /**
     * Proses perubahan input ketikan.
     * - Menghitung ketukan baru, mendeteksi typo (termasuk bila pengguna menghapus dengan backspace
     *   lalu mengetik ulang, riwayat typo tetap tercatat di akurasi murni!).
     */
    fun processInputChange(
        target: String,
        previousInput: String,
        newInput: String,
        prevKeystrokes: Int,
        prevErrors: Int,
        prevCombo: Int,
        prevMaxCombo: Int,
        prevMissed: Map<Char, Int>,
    ): TypingStepUpdate {
        // Batasi input maksimal sepanjang target + 4 karakter supaya tidak melebar jauh saat typo.
        val clipped = if (newInput.length > target.length + 4) newInput.take(target.length + 4) else newInput
        if (clipped.length <= previousInput.length) {
            // Backspace / penghapusan: tidak menambah ketukan atau typo baru.
            return TypingStepUpdate(
                totalKeystrokes = prevKeystrokes,
                errorKeystrokes = prevErrors,
                combo = prevCombo,
                maxCombo = prevMaxCombo,
                missedChars = prevMissed,
                typoTriggered = false,
                comboMilestone = false,
                completed = clipped == target && target.isNotEmpty(),
            )
        }

        var keystrokes = prevKeystrokes
        var errors = prevErrors
        var combo = prevCombo
        var maxCombo = prevMaxCombo
        val missed = prevMissed.toMutableMap()
        var typo = false
        var milestone = false

        // Periksa apakah prefix sebelum penambahan sudah memiliki kesalahan.
        var prefixClean = previousInput.length <= target.length && target.startsWith(previousInput)

        for (i in previousInput.length until clipped.length) {
            keystrokes++
            val typedChar = clipped[i]
            val expectedChar = target.getOrNull(i)
            if (prefixClean && expectedChar != null && typedChar == expectedChar) {
                combo++
                if (combo > maxCombo) maxCombo = combo
                if (combo >= COMBO_MILESTONE_STEP && combo % COMBO_MILESTONE_STEP == 0) {
                    milestone = true
                }
            } else {
                prefixClean = false
                errors++
                combo = 0
                typo = true
                if (expectedChar != null) {
                    missed[expectedChar] = (missed[expectedChar] ?: 0) + 1
                }
            }
        }

        return TypingStepUpdate(
            totalKeystrokes = keystrokes,
            errorKeystrokes = errors,
            combo = combo,
            maxCombo = maxCombo,
            missedChars = missed,
            typoTriggered = typo,
            comboMilestone = milestone && !typo,
            completed = clipped == target && target.isNotEmpty(),
        )
    }

    /** Hitung WPM (standar 5 karakter = 1 kata), CPM, dan Akurasi (%) untuk satu balasan. */
    fun computeTurnMetrics(
        targetLength: Int,
        elapsedMs: Long,
        totalKeystrokes: Int,
        errorKeystrokes: Int,
    ): TurnMetrics {
        // Minimal 400ms agar paste/kalimat super pendek tidak menghasilkan WPM ribuan yang tidak masuk akal.
        val safeMs = elapsedMs.coerceAtLeast(400L)
        val minutes = safeMs / 60_000.0
        val cpm = (targetLength / minutes).roundToInt().coerceIn(1, 1500)
        val wpm = ((targetLength / 5.0) / minutes).roundToInt().coerceIn(1, 300)
        val strokes = max(totalKeystrokes, targetLength).coerceAtLeast(1)
        val correct = (strokes - errorKeystrokes).coerceAtLeast(0)
        val acc = ((correct * 100.0) / strokes).roundToInt().coerceIn(0, 100)
        return TurnMetrics(
            wpm = wpm,
            cpm = cpm,
            accuracyPct = acc,
            elapsedMs = safeMs,
            keystrokes = strokes,
            errors = errorKeystrokes,
        )
    }

    /** Hitung WPM & Akurasi live selagi pengguna sedang mengetik satu balasan. */
    fun computeLiveWpmAndAccuracy(
        correctChars: Int,
        elapsedMs: Long,
        totalKeystrokes: Int,
        errorKeystrokes: Int,
    ): Pair<Int, Int> {
        val wpm = if (correctChars <= 1 || elapsedMs < 350L) {
            0
        } else {
            val minutes = elapsedMs / 60_000.0
            ((correctChars / 5.0) / minutes).roundToInt().coerceIn(0, 300)
        }
        val acc = if (totalKeystrokes <= 0) {
            100
        } else {
            (((totalKeystrokes - errorKeystrokes).coerceAtLeast(0) * 100.0) / totalKeystrokes).roundToInt().coerceIn(0, 100)
        }
        return wpm to acc
    }

    /** Rangkum seluruh putaran percakapan di akhir episode. */
    fun summarizeSession(
        turns: List<TurnMetrics>,
        maxCombo: Int,
        missedChars: Map<Char, Int>,
        previousBestWpm: Int?,
    ): SessionSummary {
        if (turns.isEmpty()) {
            return SessionSummary(0, 0, 0, 100, 0L, 0, 0, maxCombo, false, previousBestWpm, emptyList())
        }
        val avgWpm = turns.map { it.wpm }.average().roundToInt()
        val peakWpm = turns.maxOf { it.wpm }
        val avgCpm = turns.map { it.cpm }.average().roundToInt()
        val totalStrokes = turns.sumOf { it.keystrokes }.coerceAtLeast(1)
        val totalErr = turns.sumOf { it.errors }
        val totalMs = turns.sumOf { it.elapsedMs }
        val acc = (((totalStrokes - totalErr).coerceAtLeast(0) * 100.0) / totalStrokes).roundToInt().coerceIn(0, 100)
        val isNewRecord = avgWpm > (previousBestWpm ?: 0)
        val topMissed = missedChars.entries
            .sortedByDescending { it.value }
            .take(4)
            .map { (ch, count) -> "${describeChar(ch)} (${count}×)" }
        return SessionSummary(
            averageWpm = avgWpm,
            peakWpm = peakWpm,
            averageCpm = avgCpm,
            accuracyPct = acc,
            totalElapsedMs = totalMs,
            totalKeystrokes = totalStrokes,
            totalErrors = totalErr,
            maxCombo = maxCombo,
            isNewRecord = isNewRecord,
            previousBestWpm = previousBestWpm,
            topMissedLabels = topMissed,
        )
    }

    private fun describeChar(ch: Char): String = when (ch) {
        ' ' -> "spasi"
        ',' -> "koma (,)"
        '.' -> "titik (.)"
        '?' -> "tanda tanya (?)"
        '!' -> "tanda seru (!)"
        '-' -> "strip (-)"
        ':' -> "titik dua (:)"
        else -> "'$ch'"
    }

    // ------------------------------------------------------------------ Cerita Bawaan (Offline)

    val BUILT_IN_STORIES: List<ChatStory> = listOf(
        ChatStory(
            id = "prod-outage",
            title = "Darurat Server Produksi",
            category = "Kerja & Tech",
            partnerName = "Bima (DevOps)",
            partnerRole = "Site Reliability Engineer",
            difficulty = TypingDifficulty.Standard,
            suggestedTheme = ChatVisualTheme.Terminal,
            turns = listOf(
                ChatTurn(
                    partnerMessage = "Bro, bangun! Alert merah semua, latency API pembayaran tembus 12 detik!",
                    targetReply = "Gue udah buka laptop, cek log database utama sekarang juga.",
                ),
                ChatTurn(
                    partnerMessage = "Koneksi pool penuh di node-02 sejak rilis jam 02:15 tadi. Mau langsung rollback?",
                    targetReply = "Tahan dulu, masalahnya ada di query indeks transaksi yang belum ke-cache.",
                ),
                ChatTurn(
                    partnerMessage = "Kalau gitu kita punya waktu 3 menit sebelum antrean webhook timeout semua.",
                    targetReply = "Oke, gue kirim patch hotfix sekarang dan aktifkan rate limiter sementara.",
                ),
                ChatTurn(
                    partnerMessage = "Build hotfix v2.4.1 sudah hijau! Mau gue deploy ke cluster produksi?",
                    targetReply = "Gas deploy bertahap mulai dari 20 persen trafik, pantau grafik error-nya.",
                ),
                ChatTurn(
                    partnerMessage = "Gila, latency langsung turun ke 45ms dan antrean kembali normal! Mantap jiwa!",
                    targetReply = "Syukurlah aman, besok pagi kita tulis post-mortem biar nggak kejadian lagi.",
                ),
            ),
        ),
        ChatStory(
            id = "client-revision",
            title = "Negosiasi Revisi Malam Minggu",
            category = "Profesional",
            partnerName = "Pak Hendra",
            partnerRole = "Klien Proyek Aplikasi",
            difficulty = TypingDifficulty.Standard,
            suggestedTheme = ChatVisualTheme.Messenger,
            turns = listOf(
                ChatTurn(
                    partnerMessage = "Selamat malam, boleh minta tolong tambah fitur laporan PDF buat presentasi Senin pagi?",
                    targetReply = "Malam Pak Hendra, untuk hari Senin kita fokus ke stabilitas fitur utama dulu ya.",
                ),
                ChatTurn(
                    partnerMessage = "Tapi direksi pengen lihat contoh ekspor datanya walau masih sederhana.",
                    targetReply = "Kami bisa siapkan ekspor CSV yang rapi malam ini tanpa mengubah jadwal rilis.",
                ),
                ChatTurn(
                    partnerMessage = "Oh CSV bisa dibuka langsung di Excel ya? Kalau format PDF-nya kapan bisa menyusul?",
                    targetReply = "Betul Pak, CSV langsung rapi di Excel dan modul PDF kami jadwalkan di sprint depan.",
                ),
                ChatTurn(
                    partnerMessage = "Sip, masuk akal. Tolong pastikan angka ringkasan bulanan sudah akurat ya.",
                    targetReply = "Siap Pak, seluruh pengujian otomatis sudah hijau dan siap didemokan Senin pukul 09:00.",
                ),
            ),
        ),
        ChatStory(
            id = "weekend-trip",
            title = "Rencana Liburan Mendadak",
            category = "Sehari-hari",
            partnerName = "Nadia",
            partnerRole = "Sahabat Perjalanan",
            difficulty = TypingDifficulty.Casual,
            suggestedTheme = ChatVisualTheme.Paper,
            turns = listOf(
                ChatTurn(
                    partnerMessage = "Besok jadi berangkat ke Bandung naik kereta jam berapa?",
                    targetReply = "Jadi dong, kita ambil kereta jam tujuh pagi dari Gambir ya.",
                ),
                ChatTurn(
                    partnerMessage = "Jangan lupa bawa jaket tebal, di Lembang lagi dingin banget.",
                    targetReply = "Siap, jaket sama kamera udah masuk tas dari sore tadi.",
                ),
                ChatTurn(
                    partnerMessage = "Sampai sana kita sarapan kupat tahu dulu atau langsung ngopi?",
                    targetReply = "Sarapan kupat tahu hangat dulu baru lanjut cari kopi susu.",
                ),
                ChatTurn(
                    partnerMessage = "Oke deal! Jangan telat bangun lagi kayak bulan lalu ya!",
                    targetReply = "Tenang, alarm udah gue pasang tiga kali mulai jam lima subuh.",
                ),
            ),
        ),
        ChatStory(
            id = "midnight-signal",
            title = "Sinyal Rahasia Tengah Malam",
            category = "Misteri & Sci-Fi",
            partnerName = "Agen Orion",
            partnerRole = "Operator Lapangan",
            difficulty = TypingDifficulty.Intense,
            suggestedTheme = ChatVisualTheme.Terminal,
            turns = listOf(
                ChatTurn(
                    partnerMessage = "[ENCRYPTED] Pintu sektor 7 terkunci rapat, sensor laser aktif setiap 15 detik. Butuh kode override!",
                    targetReply = "Masukkan kode 482-alpha di panel kiri tepat saat lampu indikator berkedip hijau.",
                ),
                ChatTurn(
                    partnerMessage = "Pintu terbuka! Di ruang server ada 3 terminal aktif: alpha, beta, dan omega. Mana yang berisi cadangan kunci?",
                    targetReply = "Jangan sentuh terminal beta, salin arsip terenkripsi dari omega ke drive lokal dalam 40 detik.",
                ),
                ChatTurn(
                    partnerMessage = "Unduhan selesai 100%, tapi alarm koridor utama tiba-tiba berbunyi! Jalur keluar utama ditutup!",
                    targetReply = "Naik lewat tangga darurat barat menuju atap gedung, helikopter jemputan tiba pukul 00:45 tepat.",
                ),
            ),
        ),
        ChatStory(
            id = "tokyo-coffee",
            title = "Morning Coffee in Tokyo",
            category = "English Practice",
            partnerName = "Kenji",
            partnerRole = "Specialty Barista",
            difficulty = TypingDifficulty.Standard,
            suggestedTheme = ChatVisualTheme.Paper,
            turns = listOf(
                ChatTurn(
                    partnerMessage = "Good morning! Welcome to Roastery Tokyo. What can I brew for you today?",
                    targetReply = "Good morning! Could I get a hot pour-over with Ethiopian beans, please?",
                ),
                ChatTurn(
                    partnerMessage = "Great choice, it has bright citrus notes. Would you like a croissant to go with it?",
                    targetReply = "Yes please, one almond croissant and a glass of cold water on the side.",
                ),
                ChatTurn(
                    partnerMessage = "Feel free to take the window seat upstairs, the Wi-Fi password is on your receipt.",
                    targetReply = "Thank you so much, this place has the best view for a quiet morning focus session.",
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------ AI Story Workflow Generator

    /** Susun prompt terstruktur untuk meminta LLM membuat alur cerita percakapan JSON. */
    fun buildAiStoryPrompt(
        topic: String,
        partnerRole: String?,
        difficulty: TypingDifficulty,
        turnCount: Int,
    ): String {
        val roleHint = partnerRole?.trim()?.takeIf { it.isNotEmpty() } ?: "lawan bicara yang relevan dengan topik"
        return """
            Buatkan skenario percakapan chat interaktif untuk latihan mengetik cepat di HP.
            Topik / Skenario: ${topic.trim()}
            Peran lawan bicara: $roleHint
            Tingkat kesulitan ketik: ${difficulty.label} (panjang tiap balasan "reply" wajib antara ${difficulty.minChars} sampai ${difficulty.maxChars} karakter).
            Jumlah putaran (turns): ${turnCount.coerceIn(3, 12)} putaran.

            Balas HANYA dengan JSON valid tanpa penjelasan tambahan, dengan format persis berikut:
            {
              "title": "Judul Singkat Cerita",
              "category": "Kategori Singkat",
              "partnerName": "Nama Lawan Bicara",
              "partnerRole": "Jabatan / Peran",
              "suggestedTheme": "Paper | Messenger | Terminal",
              "turns": [
                {
                  "partner": "Pesan dari lawan bicara...",
                  "reply": "Kalimat balasan yang harus diketik pengguna..."
                }
              ]
            }
            Pastikan alur ceritanya menyambung dari awal sampai akhir, natural, dan "reply" nyaman diketik tanpa emoji.
        """.trimIndent()
    }

    /**
     * Ekstrak [ChatStory] dari respons teks LLM (tahan terhadap blok ```json ... ```).
     * Mengembalikan null bila JSON tidak punya minimal 2 putaran valid.
     */
    fun parseAiStoryJson(
        raw: String,
        difficulty: TypingDifficulty,
        fallbackTopic: String,
        id: String = "ai-${System.currentTimeMillis()}",
    ): ChatStory? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val jsonSlice = raw.substring(start, end + 1)

        fun extractString(key: String, source: String = jsonSlice): String? {
            val re = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
            return re.find(source)?.groupValues?.get(1)?.let(::unescapeJson)?.trim()?.takeIf { it.isNotEmpty() }
        }

        val turnBlocks = Regex("""\{\s*"partner"\s*:\s*"((?:\\.|[^"\\])*)"\s*,\s*"reply"\s*:\s*"((?:\\.|[^"\\])*)"\s*\}""")
            .findAll(jsonSlice)
            .mapNotNull { m ->
                val partner = unescapeJson(m.groupValues[1]).trim()
                val reply = unescapeJson(m.groupValues[2]).trim().replace(Regex("\\s+"), " ")
                if (partner.isNotEmpty() && reply.length >= 10) ChatTurn(partner, reply) else null
            }
            .toList()

        if (turnBlocks.size < 2) return null

        val themeName = extractString("suggestedTheme")
        val theme = ChatVisualTheme.entries.firstOrNull { it.name.equals(themeName, ignoreCase = true) }
            ?: ChatVisualTheme.Messenger

        return ChatStory(
            id = id,
            title = extractString("title") ?: fallbackTopic.trim().take(40).ifEmpty { "Cerita AI" },
            category = extractString("category") ?: "Kreasi AI",
            partnerName = extractString("partnerName") ?: "Rekan Chat",
            partnerRole = extractString("partnerRole") ?: "Lawan Bicara AI",
            difficulty = difficulty,
            suggestedTheme = theme,
            turns = turnBlocks,
            isAiGenerated = true,
        )
    }

    /**
     * Generator variasi cerita instan secara offline bila pengguna belum mengatur API key AI
     * atau sedang tanpa internet, sehingga fitur pembuatan cerita kustom tetap selalu berfungsi.
     */
    fun generateOfflineStory(
        topic: String,
        partnerRole: String?,
        difficulty: TypingDifficulty,
        turnCount: Int,
        id: String = "custom-${System.currentTimeMillis()}",
    ): ChatStory {
        val cleanTopic = topic.trim().ifEmpty { "Proyek Baru" }
        val role = partnerRole?.trim()?.takeIf { it.isNotEmpty() } ?: "Rekan Diskusi"
        val name = role.split(' ').firstOrNull()?.takeIf { it.length >= 3 } ?: "Raka"
        val templates = listOf(
            ChatTurn(
                partnerMessage = "Halo! Kita perlu bahas langkah awal untuk topik \"$cleanTopic\" hari ini.",
                targetReply = when (difficulty) {
                    TypingDifficulty.Casual -> "Siap, aku sudah catat poin utamanya supaya kita bisa langsung mulai."
                    TypingDifficulty.Standard -> "Siap, aku sudah menyusun tiga prioritas utama untuk $cleanTopic agar eksekusinya tepat waktu."
                    TypingDifficulty.Intense -> "Siap, seluruh rincian teknis untuk $cleanTopic sudah kusiapkan lengkap dengan target metrik dan jadwal evaluasi mingguan."
                },
            ),
            ChatTurn(
                partnerMessage = "Bagian mana yang menurutmu paling menantang dan harus kita selesaikan lebih dulu?",
                targetReply = when (difficulty) {
                    TypingDifficulty.Casual -> "Kita bereskan fondasi dasarnya dulu supaya langkah berikutnya lancar."
                    TypingDifficulty.Standard -> "Kita kunci dulu bagian paling kritis di awal minggu, lalu lanjut ke pengujian detail."
                    TypingDifficulty.Intense -> "Prioritas nomor 1 adalah menyamakan standar kualitas dan menguji skenario batas sebelum integrasi penuh dilakukan."
                },
            ),
            ChatTurn(
                partnerMessage = "Setuju. Kalau ada kendala di tengah jalan, siapa yang perlu langsung kita hubungi?",
                targetReply = when (difficulty) {
                    TypingDifficulty.Casual -> "Langsung kabari aku lewat grup utama supaya cepat kita tangani bareng."
                    TypingDifficulty.Standard -> "Langsung kirim ringkasan kendalanya ke kanal koordinasi supaya kita putuskan solusinya hari itu juga."
                    TypingDifficulty.Intense -> "Catat setiap kendala di daftar pantauan harian dan sinkronkan pukul 16:00 agar tidak ada hambatan yang menumpuk."
                },
            ),
            ChatTurn(
                partnerMessage = "Bagaimana dengan target waktunya? Apakah akhir pekan ini sudah bisa kita tinjau hasilnya?",
                targetReply = when (difficulty) {
                    TypingDifficulty.Casual -> "Bisa banget, hari Jumat sore draf pertamanya sudah siap kita cek."
                    TypingDifficulty.Standard -> "Hari Jumat pukul 15:00 versi pertamanya sudah siap ditinjau bersama seluruh catatan perbaikannya."
                    TypingDifficulty.Intense -> "Target kita Jumat pukul 15:00 seluruh tahapan utama sudah terverifikasi minimal 95 persen dan siap dipresentasikan."
                },
            ),
            ChatTurn(
                partnerMessage = "Mantap! Terima kasih sudah gerak cepat, semangat menuntaskan $cleanTopic!",
                targetReply = when (difficulty) {
                    TypingDifficulty.Casual -> "Sama-sama, ayo kita kerjakan pelan-pelan tapi konsisten sampai tuntas."
                    TypingDifficulty.Standard -> "Sama-sama! Mari kita jaga ritme kerjanya supaya hasil akhirnya rapi dan sesuai harapan."
                    TypingDifficulty.Intense -> "Terima kasih kembali! Konsistensi dan ketelitian di setiap langkah bakal membuat hasil akhirnya jauh lebih solid."
                },
            ),
        )
        return ChatStory(
            id = id,
            title = cleanTopic.take(42),
            category = "Skenario Kustom",
            partnerName = name,
            partnerRole = role,
            difficulty = difficulty,
            suggestedTheme = if (difficulty == TypingDifficulty.Intense) ChatVisualTheme.Terminal else ChatVisualTheme.Messenger,
            turns = templates.take(turnCount.coerceIn(3, templates.size)),
            isAiGenerated = true,
        )
    }

    private fun unescapeJson(s: String): String = s
        .replace("\\\"", "\"")
        .replace("\\n", " ")
        .replace("\\r", " ")
        .replace("\\t", " ")
        .replace("\\\\", "\\")
}
