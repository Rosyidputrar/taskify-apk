package com.rosyidputrar.taskify.core.domain.files

/** Satu entri arsip ZIP (ukuran asli, tidak terkompresi). */
data class ZipEntryInfo(val path: String, val size: Long, val dir: Boolean)

enum class WarningLevel { Danger, Warning }

data class ZipWarning(val level: WarningLevel, val title: String, val detail: String, val paths: List<String>)

data class ZipSummary(val entries: List<ZipEntryInfo>, val files: Int, val totalSize: Long, val warnings: List<ZipWarning>)

private class Rule(val level: WarningLevel, val title: String, val detail: String, val test: (String) -> Boolean, val group: (String) -> String)

private val BUILD_DIR = Regex("(^|/)(dist|build|\\.next|\\.nuxt|target|__pycache__|\\.venv|venv|\\.turbo|\\.cache)/")
private val BUILD_GROUP = Regex("^(.*?(?:^|/)(?:dist|build|\\.next|\\.nuxt|target|__pycache__|\\.venv|venv|\\.turbo|\\.cache))/")

/** Aturan peringatan sebelum mengarsipkan project (port `zip.ts`). */
private val RULES = listOf(
    Rule(
        WarningLevel.Danger, "Berisi file rahasia (.env / kunci)", "Hapus kredensial sebelum diarsipkan, atau simpan .env.example saja.",
        test = { p ->
            Regex("(^|/)\\.env(\\.(?!example|sample|template)[^/]*)?$", RegexOption.IGNORE_CASE).containsMatchIn(p) ||
                Regex("(^|/)(id_rsa|id_ed25519|.*\\.pem|.*\\.p12|.*\\.keystore)$", RegexOption.IGNORE_CASE).containsMatchIn(p)
        },
        group = { it },
    ),
    Rule(
        WarningLevel.Warning, "Berisi node_modules", "Biasanya ratusan MB dan bisa dipulihkan dengan pnpm/npm install.",
        test = { Regex("(^|/)node_modules/").containsMatchIn(it) },
        group = { p -> p.substring(0, p.indexOf("node_modules/") + "node_modules".length) },
    ),
    Rule(
        WarningLevel.Warning, "Berisi folder .git", "Riwayat git ikut terarsip. Simpan di GitHub/GitLab, atau abaikan jika memang sengaja.",
        test = { Regex("(^|/)\\.git/").containsMatchIn(it) },
        group = { p -> p.substring(0, p.indexOf(".git/") + ".git".length) },
    ),
    Rule(
        WarningLevel.Warning, "Berisi hasil build / cache", "Folder seperti dist, build, .next, target, __pycache__ bisa dibuat ulang.",
        test = { BUILD_DIR.containsMatchIn(it) },
        group = { p -> BUILD_GROUP.find(p)?.groupValues?.get(1) ?: p },
    ),
)

fun summarizeZip(entries: List<ZipEntryInfo>): ZipSummary {
    val files = entries.filterNot { it.dir }
    val warnings = RULES.mapNotNull { rule ->
        val hits = files.filter { rule.test(it.path) }.map { rule.group(it.path) }.distinct()
        if (hits.isEmpty()) null else ZipWarning(rule.level, rule.title, rule.detail, hits.take(5))
    }
    return ZipSummary(entries, files.size, files.sumOf { it.size }, warnings)
}

data class TreeNode(val name: String, val path: String, val dir: Boolean, val size: Long, val children: List<TreeNode>)

/** Pohon folder: folder dulu, lalu alfabet; ukuran folder = jumlah isinya. */
fun buildTree(entries: List<ZipEntryInfo>): TreeNode {
    class Mut(val name: String, val path: String, var dir: Boolean, var size: Long = 0, val children: MutableList<Mut> = mutableListOf())
    val root = Mut("", "", true)
    for (e in entries) {
        val parts = e.path.removeSuffix("/").split('/')
        var node = root
        parts.forEachIndexed { i, part ->
            val last = i == parts.lastIndex
            val path = parts.subList(0, i + 1).joinToString("/") + if (last && !e.dir) "" else "/"
            val child = node.children.firstOrNull { it.name == part } ?: Mut(part, path, !last || e.dir).also { node.children += it }
            if (last && !e.dir) child.size = e.size
            node = child
        }
    }
    fun freeze(n: Mut): TreeNode {
        val kids = n.children.map(::freeze).sortedWith(compareBy<TreeNode> { !it.dir }.thenBy { it.name })
        return TreeNode(n.name, n.path, n.dir, if (n.dir) kids.sumOf { it.size } else n.size, kids)
    }
    return freeze(root)
}

/** Satu folder akar tunggal dibuka otomatis (mis. "taskify-main/"). */
fun TreeNode.unwrapSingleRoot(): TreeNode = if (children.size == 1 && children[0].dir) children[0] else this

private val TEXT_EXT = Regex(
    "\\.(txt|md|mdx|json|ya?ml|toml|ini|env|cfg|conf|js|mjs|cjs|ts|tsx|jsx|py|go|rs|java|kt|php|rb|cs|c|h|cpp|hpp|swift|dart|sql|html?|xml|svg|vue|svelte|css|scss|sh|bash|ps1|gql|graphql|lock|gitignore|editorconfig|prettierrc|eslintrc|dockerfile|gradle|properties|csv|log)$",
    RegexOption.IGNORE_CASE,
)
private val TEXT_NAMES = Regex("^(dockerfile|makefile|license|readme|\\.gitignore|\\.env.*|\\.npmrc|\\.nvmrc|procfile)$", RegexOption.IGNORE_CASE)

fun isTextPath(path: String): Boolean {
    val name = path.substringAfterLast('/')
    return TEXT_EXT.containsMatchIn(name) || TEXT_NAMES.matches(name)
}

fun isImagePath(path: String): Boolean = Regex("\\.(png|jpe?g|gif|webp|svg|ico|avif)$", RegexOption.IGNORE_CASE).containsMatchIn(path)

/** Label versi berikutnya: v1 → v2, v1.2 → v1.3; tanpa label numerik → v1. */
fun nextVersionLabel(labels: List<String?>): String {
    var best: List<Int>? = null
    for (l in labels) {
        val m = l?.let { Regex("^v?(\\d+(?:\\.\\d+)*)$", RegexOption.IGNORE_CASE).find(it) } ?: continue
        val parts = m.groupValues[1].split('.').map { it.toInt() }
        if (best == null || compareVersions(parts, best) > 0) best = parts
    }
    val b = best ?: return "v1"
    val next = b.toMutableList()
    next[next.lastIndex] = next.last() + 1
    return "v" + next.joinToString(".")
}

private fun compareVersions(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = (a.getOrElse(i) { 0 }) - (b.getOrElse(i) { 0 })
        if (d != 0) return d
    }
    return 0
}
