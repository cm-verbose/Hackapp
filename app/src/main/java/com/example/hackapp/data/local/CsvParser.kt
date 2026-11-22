package com.example.hackapp.data.local

/**
 * Minimal RFC 4180 CSV parser: handles quoted fields containing commas,
 * escaped quotes (""), and line breaks inside quotes.
 */
object CsvParser {

    /** UTF-8 byte-order mark some spreadsheet tools prepend. */
    private val BOM = 0xFEFF.toChar()

    /** Parses [text] into rows of header-name → value. Blank values are dropped. */
    fun parseWithHeader(text: String): List<Map<String, String>> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().trimStart(BOM) }
        return rows.drop(1)
            .filter { row -> row.any { it.isNotBlank() } }
            .map { row ->
                buildMap {
                    header.forEachIndexed { i, name ->
                        row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }?.let { put(name, it) }
                    }
                }
            }
    }

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { row += field.toString(); field.clear() }
                    '\r' -> Unit
                    '\n' -> {
                        row += field.toString(); field.clear()
                        rows += row; row = mutableListOf()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }
}
