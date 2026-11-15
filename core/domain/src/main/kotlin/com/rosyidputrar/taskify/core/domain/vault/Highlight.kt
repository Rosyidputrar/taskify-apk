package com.rosyidputrar.taskify.core.domain.vault

enum class TokenType { Plain, Keyword, String, Comment, Number, Tag }

data class CodeToken(val text: String, val type: TokenType)

/**
 * Pewarnaan sintaks ringan untuk tampilan baca (bukan parser penuh): komentar, string, angka,
 * kata kunci umum, dan tag markup. Cukup untuk membaca snippet & isi ZIP di HP tanpa WebView.
 */
object Highlighter {
    private val C_LIKE = setOf("javascript", "typescript", "jsx", "tsx", "go", "rust", "java", "kotlin", "php", "csharp", "c", "cpp", "swift", "dart", "scss", "css", "graphql")
    private val HASH_COMMENT = setOf("bash", "python", "ruby", "yaml", "toml", "ini", "docker", "nginx", "powershell", "graphql", "php")
    private val PLAIN = setOf("plaintext", "markdown")

    private val KEYWORDS = setOf(
        "abstract", "and", "as", "async", "await", "break", "case", "catch", "class", "const", "continue", "data", "def", "default",
        "defer", "del", "do", "elif", "else", "enum", "except", "export", "extends", "false", "final", "finally", "fn", "for", "from",
        "fun", "func", "function", "go", "if", "impl", "implements", "import", "in", "interface", "is", "lambda", "let", "match",
        "mod", "module", "mut", "new", "nil", "None", "not", "null", "object", "or", "override", "package", "pass", "private",
        "protected", "pub", "public", "raise", "return", "self", "static", "struct", "super", "switch", "this", "throw", "throws",
        "trait", "true", "True", "False", "try", "type", "typeof", "use", "val", "var", "void", "when", "where", "while", "with", "yield",
        "select", "insert", "update", "delete", "where", "join", "left", "right", "inner", "on", "group", "by", "order", "limit",
        "create", "table", "alter", "drop", "values", "into", "set", "begin", "end", "then", "echo", "fi", "done", "esac", "local",
    )

    fun tokenize(code: String, language: String): List<CodeToken> {
        if (language in PLAIN) return listOf(CodeToken(code, TokenType.Plain))
        if (language == "markup") return markup(code)
        val out = mutableListOf<CodeToken>()
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { out += CodeToken(buf.toString(), TokenType.Plain); buf.clear() } }
        val lineComment = when {
            language == "sql" -> "--"
            language in C_LIKE && language != "css" -> "//"
            else -> null
        }
        val hash = language in HASH_COMMENT
        val block = language in C_LIKE || language == "sql"
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                block && code.startsWith("/*", i) -> {
                    flush()
                    val end = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }
                    out += CodeToken(code.substring(i, end), TokenType.Comment); i = end
                }
                (lineComment != null && code.startsWith(lineComment, i)) || (hash && c == '#') -> {
                    flush()
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    out += CodeToken(code.substring(i, end), TokenType.Comment); i = end
                }
                language == "ini" && c == ';' && (i == 0 || code[i - 1] == '\n') -> {
                    flush()
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    out += CodeToken(code.substring(i, end), TokenType.Comment); i = end
                }
                c == '"' || c == '\'' || c == '`' -> {
                    flush()
                    var j = i + 1
                    while (j < code.length && code[j] != c && !(c != '`' && code[j] == '\n')) {
                        if (code[j] == '\\') j++
                        j++
                    }
                    val end = minOf(code.length, j + 1)
                    out += CodeToken(code.substring(i, end), TokenType.String); i = end
                }
                c.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit() && code[i - 1] != '_') -> {
                    flush()
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                    out += CodeToken(code.substring(i, j), TokenType.Number); i = j
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '$')) j++
                    val word = code.substring(i, j)
                    val keyword = word in KEYWORDS || (language == "sql" && word.lowercase() in KEYWORDS)
                    if (keyword) { flush(); out += CodeToken(word, TokenType.Keyword) } else buf.append(word)
                    i = j
                }
                else -> { buf.append(c); i++ }
            }
        }
        flush()
        return out
    }

    private fun markup(code: String): List<CodeToken> {
        val out = mutableListOf<CodeToken>()
        var i = 0
        val re = Regex("<!--[\\s\\S]*?-->|</?[A-Za-z][^>]*>")
        for (m in re.findAll(code)) {
            if (m.range.first > i) out += CodeToken(code.substring(i, m.range.first), TokenType.Plain)
            out += CodeToken(m.value, if (m.value.startsWith("<!--")) TokenType.Comment else TokenType.Tag)
            i = m.range.last + 1
        }
        if (i < code.length) out += CodeToken(code.substring(i), TokenType.Plain)
        return out
    }
}
