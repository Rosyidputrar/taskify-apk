package com.rosyidputrar.taskify.core.domain.home

/** Bagian Beranda yang bisa diurutkan & disembunyikan. */
enum class HomeSection(val label: String) {
    Week("Pita minggu"),
    Focus("Fokus sekarang"),
    Shortcuts("Pintasan"),
    Summary("Ringkasan"),
    Activity("Aktivitas hari ini"),
}

/** Ubin ringkasan (bento) di Beranda. */
enum class HomeTile(val label: String) {
    Tasks("Tugas hari ini"),
    Mood("Mood hari ini"),
    Spending("Keluar hari ini"),
    Streak("Streak jurnal"),
    Balance("Saldo semua dompet"),
    Income("Masuk bulan ini"),
    ScreenTime("Waktu layar"),
    Notifications("Notifikasi hari ini"),
}

/**
 * Susunan Beranda pilihan pengguna (per perangkat). [sections]/[tiles] = urutan SEMUA item;
 * [hiddenSections]/[hiddenTiles] = yang disembunyikan. Disimpan sebagai teks sederhana yang tahan
 * terhadap versi: nilai tak dikenal dibuang, item baru di versi berikutnya ikut muncul di akhir.
 */
data class HomeLayout(
    val sections: List<HomeSection> = HomeSection.entries,
    val hiddenSections: Set<HomeSection> = emptySet(),
    val tiles: List<HomeTile> = DEFAULT_TILES + (HomeTile.entries - DEFAULT_TILES.toSet()),
    val hiddenTiles: Set<HomeTile> = HomeTile.entries.toSet() - DEFAULT_TILES.toSet(),
    /** Kartu "Lanjutkan" otomatis (item yang terakhir diubah) di bagian Pintasan. */
    val showRecent: Boolean = true,
) {
    val visibleSections: List<HomeSection> get() = sections.filter { it !in hiddenSections }
    val visibleTiles: List<HomeTile> get() = tiles.filter { it !in hiddenTiles }
    val isDefault: Boolean get() = this == Default

    fun moveSection(s: HomeSection, by: Int): HomeLayout = copy(sections = sections.moved(s, by))
    fun toggleSection(s: HomeSection): HomeLayout = copy(hiddenSections = hiddenSections.toggled(s))
    fun moveTile(t: HomeTile, by: Int): HomeLayout = copy(tiles = tiles.moved(t, by))
    fun toggleTile(t: HomeTile): HomeLayout = copy(hiddenTiles = hiddenTiles.toggled(t))
    fun toggleRecent(): HomeLayout = copy(showRecent = !showRecent)

    fun encode(): String = listOf(
        sections.joinToString(",") { (if (it in hiddenSections) "-" else "") + it.name },
        tiles.joinToString(",") { (if (it in hiddenTiles) "-" else "") + it.name },
        if (showRecent) "recent" else "-recent",
    ).joinToString("|")

    companion object {
        val DEFAULT_TILES = listOf(HomeTile.Tasks, HomeTile.Mood, HomeTile.Spending, HomeTile.Streak)
        val Default = HomeLayout()

        fun decode(text: String?): HomeLayout {
            if (text.isNullOrBlank()) return Default
            val parts = text.split("|")
            val (sections, hiddenS) = parse(parts.getOrNull(0), HomeSection.entries) { n -> HomeSection.entries.firstOrNull { it.name == n } }
            val (tiles, hiddenT) = parse(parts.getOrNull(1), HomeTile.entries) { n -> HomeTile.entries.firstOrNull { it.name == n } }
            // Item baru (belum ada di teks lama) → ditambahkan; ubin baru disembunyikan, bagian baru tampil.
            val newTiles = HomeTile.entries - tiles.toSet()
            return HomeLayout(
                sections = withNewSections(sections),
                hiddenSections = hiddenS,
                tiles = tiles + newTiles,
                hiddenTiles = hiddenT + newTiles.filter { it !in DEFAULT_TILES },
                showRecent = parts.getOrNull(2) != "-recent",
            )
        }

        /**
         * Bagian yang belum ada di susunan tersimpan (versi lama) disisipkan tepat SETELAH bagian
         * yang mendahuluinya langsung di urutan bawaan (bila tidak ada: di akhir) — mis. Pintasan muncul di bawah Fokus, bukan di
         * paling bawah — tanpa mengubah urutan pilihan pengguna untuk bagian lain.
         */
        private fun withNewSections(saved: List<HomeSection>): List<HomeSection> {
            val out = saved.toMutableList()
            HomeSection.entries.forEach { s ->
                if (s in out) return@forEach
                val before = HomeSection.entries.getOrNull(s.ordinal - 1)
                val at = out.indexOf(before)
                if (at >= 0) out.add(at + 1, s) else out.add(s)
            }
            return out
        }

        private fun <T> parse(part: String?, all: List<T>, of: (String) -> T?): Pair<List<T>, Set<T>> {
            if (part.isNullOrBlank()) return all to emptySet()
            val order = mutableListOf<T>()
            val hidden = mutableSetOf<T>()
            part.split(",").forEach { raw ->
                val item = of(raw.removePrefix("-")) ?: return@forEach
                if (item in order) return@forEach
                order += item
                if (raw.startsWith("-")) hidden += item
            }
            return order to hidden
        }
    }
}

private fun <T> List<T>.moved(item: T, by: Int): List<T> {
    val i = indexOf(item)
    if (i < 0) return this
    val j = (i + by).coerceIn(0, lastIndex)
    if (i == j) return this
    return toMutableList().apply { removeAt(i); add(j, item) }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
