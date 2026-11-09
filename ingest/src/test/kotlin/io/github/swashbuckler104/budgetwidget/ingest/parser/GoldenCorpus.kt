package io.github.swashbuckler104.budgetwidget.ingest.parser

/** Loader for the golden corpus in `src/test/resources/golden` (format in its README.md). */
data class GoldenCase(val file: String, val fields: Map<String, String>) {
    val name: String get() = "$file: ${fields["name"] ?: fields.getValue("body").take(40)}"

    fun required(key: String): String = fields[key] ?: error("$name: missing '$key'")
}

object GoldenCorpus {
    /** The seven v1 banks (SCOPE.md §2). Each must have a golden file. */
    val BANK_FILES = listOf("kotak", "federal", "jupiter", "slice", "axis", "sbi", "hdfc")

    fun load(bank: String): List<GoldenCase> {
        val text = GoldenCorpus::class.java.classLoader!!.getResource("golden/$bank.txt")
            ?.readText()
            ?: error("golden/$bank.txt is missing")
        return text.split(Regex("^---\\s*$", RegexOption.MULTILINE)).mapNotNull { block ->
            val fields = block.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { line ->
                    val colon = line.indexOf(':')
                    require(colon > 0) { "golden/$bank.txt: bad line '$line'" }
                    line.substring(0, colon).trim() to line.substring(colon + 1).trim().replace("\\n", "\n")
                }
            if (fields.isEmpty()) null else GoldenCase(bank, fields)
        }
    }
}
