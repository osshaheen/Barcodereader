package com.example.multibarcode.util

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Minimal .xlsx reader for the files produced by [XlsxWriter] (and simple real-world sheets).
 * Handles inline strings, shared strings, and plain numeric cells. Cells are read left-to-right;
 * good enough to render a saved backup as a table in the archive viewer.
 */
object XlsxReader {

    data class Sheet(val name: String, val rows: List<List<String>>)

    fun read(bytes: ByteArray): List<Sheet> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) parts[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val shared = parts["xl/sharedStrings.xml"]?.let { parseSharedStrings(text(it)) } ?: emptyList()
        val names = parts["xl/workbook.xml"]?.let { parseSheetNames(text(it)) } ?: emptyList()

        val sheets = ArrayList<Sheet>()
        var i = 1
        while (true) {
            val part = parts["xl/worksheets/sheet$i.xml"] ?: break
            val name = names.getOrNull(i - 1) ?: "Sheet$i"
            sheets.add(Sheet(name, parseSheet(text(part), shared)))
            i++
        }
        return sheets
    }

    private fun text(b: ByteArray) = String(b, Charsets.UTF_8)

    private fun parseSheetNames(xml: String): List<String> =
        Regex("""<sheet\b[^>]*\bname="([^"]*)"""").findAll(xml).map { unescape(it.groupValues[1]) }.toList()

    private fun parseSharedStrings(xml: String): List<String> =
        Regex("""<si\b[^>]*>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
            Regex("""<t\b[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
        }.toList()

    private fun parseSheet(xml: String, shared: List<String>): List<List<String>> =
        Regex("""<row\b[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { row ->
            Regex("""<c\b([^>]*)>(.*?)</c>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(row.groupValues[1]).map { c ->
                    val attrs = c.groupValues[1]
                    val inner = c.groupValues[2]
                    val type = Regex("""\bt="([^"]*)"""").find(attrs)?.groupValues?.get(1)
                    when (type) {
                        "inlineStr" -> Regex("""<t\b[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
                            .findAll(inner).joinToString("") { unescape(it.groupValues[1]) }
                        "s" -> Regex("""<v\b[^>]*>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
                            .find(inner)?.groupValues?.get(1)?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                        "str" -> Regex("""<v\b[^>]*>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
                            .find(inner)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
                        else -> Regex("""<v\b[^>]*>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
                            .find(inner)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
                    }
                }.toList()
        }.toList()

    private fun unescape(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")
}
