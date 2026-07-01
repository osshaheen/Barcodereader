package com.example.multibarcode.util

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal .xlsx generator (no external library): an xlsx is a ZIP of XML parts. We write every
 * cell as an inline string, which Excel and Google Sheets open fine. Good enough for backups.
 */
object XlsxWriter {

    data class Sheet(val name: String, val rows: List<List<String>>)

    fun build(sheets: List<Sheet>): ByteArray {
        val safeSheets = sheets.ifEmpty { listOf(Sheet("Sheet1", emptyList())) }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes(safeSheets.size))
            put(zip, "_rels/.rels", dotRels())
            put(zip, "xl/workbook.xml", workbook(safeSheets))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(safeSheets.size))
            safeSheets.forEachIndexed { i, s ->
                put(zip, "xl/worksheets/sheet${i + 1}.xml", sheetXml(s))
            }
        }
        return bos.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c.code < 0x20 && c != '\t' && c != '\n') append(' ') else append(c)
        }
    }

    private fun colName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.toString()
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun dotRels(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s ->
            append("""<sheet name="${esc(sheetName(s.name, i))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun sheetName(name: String, index: Int): String {
        val cleaned = name.replace(Regex("[\\\\/*?\\[\\]:]"), " ").take(31).ifBlank { "Sheet${index + 1}" }
        return cleaned
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("</Relationships>")
    }

    private fun sheetXml(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        sheet.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { cIdx, value ->
                val ref = "${colName(cIdx)}${r + 1}"
                append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${esc(value)}</t></is></c>""")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }
}
