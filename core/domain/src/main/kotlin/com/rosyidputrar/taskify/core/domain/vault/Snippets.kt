package com.rosyidputrar.taskify.core.domain.vault

data class Snippet(
    val id: String,
    val title: String,
    val language: String,
    val code: String,
    val description: String?,
    val favorite: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

data class CodeProject(
    val id: String,
    val name: String,
    val description: String?,
    val repoUrl: String?,
    val favorite: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val versions: Int = 0,
    val totalSize: Long = 0,
    val latestSize: Long? = null,
    val latestLabel: String? = null,
    val latestAt: String? = null,
)

data class Language(val value: String, val label: String, val ext: List<String>)

/** Bahasa snippet (nilai disimpan di DB, sama dengan webapp). */
val LANGUAGES = listOf(
    Language("plaintext", "Teks biasa", listOf("txt")),
    Language("bash", "Bash / Shell", listOf("sh", "bash", "zsh")),
    Language("javascript", "JavaScript", listOf("js", "mjs", "cjs")),
    Language("typescript", "TypeScript", listOf("ts", "mts", "cts")),
    Language("jsx", "JSX", listOf("jsx")),
    Language("tsx", "TSX", listOf("tsx")),
    Language("python", "Python", listOf("py")),
    Language("go", "Go", listOf("go")),
    Language("rust", "Rust", listOf("rs")),
    Language("java", "Java", listOf("java")),
    Language("kotlin", "Kotlin", listOf("kt", "kts")),
    Language("php", "PHP", listOf("php")),
    Language("ruby", "Ruby", listOf("rb")),
    Language("csharp", "C#", listOf("cs")),
    Language("c", "C", listOf("c", "h")),
    Language("cpp", "C++", listOf("cpp", "cc", "hpp")),
    Language("swift", "Swift", listOf("swift")),
    Language("dart", "Dart", listOf("dart")),
    Language("sql", "SQL", listOf("sql")),
    Language("json", "JSON", listOf("json")),
    Language("yaml", "YAML", listOf("yml", "yaml")),
    Language("toml", "TOML", listOf("toml")),
    Language("ini", "INI / .env", listOf("ini", "env", "cfg")),
    Language("markdown", "Markdown", listOf("md", "mdx")),
    Language("markup", "HTML / XML", listOf("html", "htm", "xml", "svg", "vue")),
    Language("css", "CSS", listOf("css")),
    Language("scss", "SCSS", listOf("scss")),
    Language("docker", "Dockerfile", listOf("dockerfile")),
    Language("nginx", "Nginx", listOf("conf")),
    Language("graphql", "GraphQL", listOf("graphql", "gql")),
    Language("powershell", "PowerShell", listOf("ps1")),
)

fun languageLabel(value: String): String = LANGUAGES.firstOrNull { it.value == value }?.label ?: value

/** Tebak bahasa dari nama file. */
fun languageFromPath(path: String): String {
    val name = path.substringAfterLast('/').lowercase()
    if (name == "dockerfile") return "docker"
    if (name.startsWith(".env")) return "ini"
    val ext = if ('.' in name) name.substringAfterLast('.') else ""
    return LANGUAGES.firstOrNull { ext in it.ext }?.value ?: "plaintext"
}

private val TEMPLATE_VAR = Regex("\\{\\{\\s*([\\w.-]+)\\s*\\}\\}")

/** Variabel template {{nama}} unik dalam urutan kemunculan. */
fun templateVariables(code: String): List<String> = TEMPLATE_VAR.findAll(code).map { it.groupValues[1] }.distinct().toList()

fun fillTemplate(code: String, values: Map<String, String>): String =
    TEMPLATE_VAR.replace(code) { m -> values[m.groupValues[1]] ?: m.value }
