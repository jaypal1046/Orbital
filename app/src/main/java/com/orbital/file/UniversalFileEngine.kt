package com.orbital.file

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

sealed class FileOperationResult {
    data class Success(val message: String, val content: String? = null, val details: Map<String, Any>? = null) : FileOperationResult()
    data class Error(val errorMessage: String) : FileOperationResult()
}

data class SpreadsheetRow(val rowIndex: Int, val cells: List<String>)
data class SpreadsheetData(val sheetName: String, val headers: List<String>, val rows: List<SpreadsheetRow>)

object UniversalFileEngine {

    enum class FileCategory {
        TEXT_OR_CODE,
        SPREADSHEET_CSV,
        SPREADSHEET_EXCEL,
        WORD_DOCUMENT,
        POWERPOINT_PRESENTATION,
        PDF_DOCUMENT,
        UNKNOWN_BINARY
    }

    fun detectCategory(filePath: String): FileCategory {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt", "md", "json", "yaml", "yml", "xml", "html", "htm", "css", "js", "ts",
            "kt", "java", "py", "sh", "properties", "gradle", "kts", "sql", "log" -> FileCategory.TEXT_OR_CODE
            "csv", "tsv" -> FileCategory.SPREADSHEET_CSV
            "xlsx", "xls" -> FileCategory.SPREADSHEET_EXCEL
            "docx", "doc" -> FileCategory.WORD_DOCUMENT
            "pptx", "ppt" -> FileCategory.POWERPOINT_PRESENTATION
            "pdf" -> FileCategory.PDF_DOCUMENT
            else -> FileCategory.UNKNOWN_BINARY
        }
    }

    // ==========================================
    // 1. Text / Code / Markdown / JSON Operations
    // ==========================================

    fun readTextFile(
        file: File,
        startLine: Int = 1,
        endLine: Int = Int.MAX_VALUE,
        maxBytes: Int = 100_000
    ): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        if (!file.isFile) return FileOperationResult.Error("Path '${file.path}' is not a valid file.")

        return try {
            val lines = file.readLines(StandardCharsets.UTF_8)
            val totalLines = lines.size
            val effectiveStart = startLine.coerceAtLeast(1)
            val effectiveEnd = endLine.coerceAtMost(totalLines)

            if (effectiveStart > totalLines) {
                return FileOperationResult.Success(
                    message = "File has $totalLines lines. Start line $effectiveStart exceeds file length.",
                    content = ""
                )
            }

            val slice = lines.subList(effectiveStart - 1, effectiveEnd)
            val resultText = slice.joinToString("\n")
            val truncatedText = if (resultText.length > maxBytes) resultText.take(maxBytes) + "\n...[truncated]" else resultText

            FileOperationResult.Success(
                message = "Read ${slice.size} lines from '${file.name}' (Lines $effectiveStart to $effectiveEnd of $totalLines)",
                content = truncatedText,
                details = mapOf(
                    "totalLines" to totalLines,
                    "startLine" to effectiveStart,
                    "endLine" to effectiveEnd,
                    "bytes" to file.length()
                )
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to read '${file.path}': ${e.message}")
        }
    }

    fun writeTextFile(
        file: File,
        content: String,
        overwrite: Boolean = true
    ): FileOperationResult {
        if (file.exists() && !overwrite) {
            return FileOperationResult.Error("File '${file.path}' already exists and overwrite is false.")
        }
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content, StandardCharsets.UTF_8)
            FileOperationResult.Success(
                message = "Wrote ${content.length} characters to '${file.name}'",
                details = mapOf("path" to file.absolutePath, "bytes" to file.length())
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to write to '${file.path}': ${e.message}")
        }
    }

    fun replaceFileContent(
        file: File,
        targetContent: String,
        replacementContent: String,
        allowMultiple: Boolean = false
    ): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val original = file.readText(StandardCharsets.UTF_8)
            val occurrences = original.split(targetContent).size - 1

            if (occurrences == 0) {
                return FileOperationResult.Error("Target content not found in '${file.name}'.")
            }
            if (occurrences > 1 && !allowMultiple) {
                return FileOperationResult.Error("Target content found $occurrences times in '${file.name}'. Specify allowMultiple=true or use a more specific target.")
            }

            val updated = if (allowMultiple) {
                original.replace(targetContent, replacementContent)
            } else {
                original.replaceFirst(targetContent, replacementContent)
            }

            file.writeText(updated, StandardCharsets.UTF_8)
            FileOperationResult.Success(
                message = "Replaced $occurrences occurrence(s) in '${file.name}'",
                details = mapOf("occurrences" to occurrences, "newLength" to updated.length)
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to edit '${file.path}': ${e.message}")
        }
    }

    fun searchFile(
        file: File,
        query: String,
        isRegex: Boolean = false,
        caseInsensitive: Boolean = true
    ): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val lines = file.readLines(StandardCharsets.UTF_8)
            val matches = mutableListOf<String>()

            val regex = if (isRegex) {
                val options = if (caseInsensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
                Regex(query, options)
            } else null

            lines.forEachIndexed { idx, line ->
                val lineNum = idx + 1
                val matched = if (regex != null) {
                    regex.containsMatchIn(line)
                } else {
                    line.contains(query, ignoreCase = caseInsensitive)
                }
                if (matched) {
                    matches.add("Line $lineNum: $line")
                }
            }

            FileOperationResult.Success(
                message = "Found ${matches.size} match(es) in '${file.name}'",
                content = matches.joinToString("\n"),
                details = mapOf("matchCount" to matches.size)
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Search error in '${file.path}': ${e.message}")
        }
    }

    // ==========================================
    // 2. Spreadsheets (CSV / TSV / Excel XLSX)
    // ==========================================

    fun readCsv(file: File, maxRows: Int = 500): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val lines = file.readLines(StandardCharsets.UTF_8)
            if (lines.isEmpty()) return FileOperationResult.Success("CSV file is empty", "")

            val delimiter = if (file.extension.equals("tsv", ignoreCase = true)) "\t" else ","
            val parsedRows = lines.take(maxRows).mapIndexed { index, line ->
                val cols = parseCsvLine(line, delimiter)
                SpreadsheetRow(index, cols)
            }

            val headers = parsedRows.firstOrNull()?.cells ?: emptyList()
            val dataRows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()

            val formatted = buildString {
                append("Headers: [${headers.joinToString(" | ")}]\n")
                append("Total Rows: ${lines.size}\n\n")
                dataRows.take(50).forEach { r ->
                    append("Row ${r.rowIndex}: ${r.cells.joinToString(" | ")}\n")
                }
                if (dataRows.size > 50) {
                    append("... [${dataRows.size - 50} more rows]")
                }
            }

            FileOperationResult.Success(
                message = "Parsed CSV '${file.name}' (${lines.size} rows)",
                content = formatted,
                details = mapOf("rowCount" to lines.size, "colCount" to headers.size)
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to parse CSV '${file.path}': ${e.message}")
        }
    }

    fun editCsvCell(file: File, row: Int, col: Int, newValue: String): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val lines = file.readLines(StandardCharsets.UTF_8).toMutableList()
            if (row < 0 || row >= lines.size) {
                return FileOperationResult.Error("Row index $row is out of bounds (0 to ${lines.size - 1}).")
            }

            val delimiter = if (file.extension.equals("tsv", ignoreCase = true)) "\t" else ","
            val cells = parseCsvLine(lines[row], delimiter).toMutableList()

            while (cells.size <= col) {
                cells.add("")
            }
            cells[col] = newValue

            lines[row] = cells.joinToString(delimiter) { escapeCsvCell(it, delimiter) }
            file.writeText(lines.joinToString("\n"), StandardCharsets.UTF_8)

            FileOperationResult.Success(
                message = "Updated cell [$row, $col] to '$newValue' in '${file.name}'"
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to edit CSV cell in '${file.path}': ${e.message}")
        }
    }

    fun appendCsvRow(file: File, rowValues: List<String>): FileOperationResult {
        return try {
            val delimiter = if (file.extension.equals("tsv", ignoreCase = true)) "\t" else ","
            val formattedRow = rowValues.joinToString(delimiter) { escapeCsvCell(it, delimiter) }
            file.parentFile?.mkdirs()

            val exists = file.exists()
            val textToAppend = if (exists && file.readText().isNotEmpty() && !file.readText().endsWith("\n")) {
                "\n$formattedRow\n"
            } else {
                "$formattedRow\n"
            }

            file.appendText(textToAppend, StandardCharsets.UTF_8)
            FileOperationResult.Success("Appended row with ${rowValues.size} column(s) to '${file.name}'")
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to append row to '${file.path}': ${e.message}")
        }
    }

    fun readXlsx(file: File): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val sheetXml = extractZipEntryText(file, "xl/worksheets/sheet1.xml")
                ?: return FileOperationResult.Error("Invalid XLSX: 'xl/worksheets/sheet1.xml' not found.")
            val sharedStringsXml = extractZipEntryText(file, "xl/sharedStrings.xml")

            val sharedStrings = if (sharedStringsXml != null) {
                val sstRegex = Regex("<t(?:[^>]*)>([\\s\\S]*?)</t>")
                sstRegex.findAll(sharedStringsXml).map { it.groupValues[1] }.toList()
            } else emptyList()

            val rowMatches = Regex("<row[^>]*>([\\s\\S]*?)</row>").findAll(sheetXml)
            val rows = mutableListOf<List<String>>()

            rowMatches.forEach { rowMatch ->
                val rowContent = rowMatch.groupValues[1]
                val cellMatches = Regex("<c[\\s\\S]*?</c>").findAll(rowContent)
                val rowCells = mutableListOf<String>()
                cellMatches.forEach { cellMatch ->
                    val fullCell = cellMatch.value
                    val isSharedString = fullCell.contains("t=\"s\"") || fullCell.contains("t='s'")
                    val vMatch = Regex("<v>([\\s\\S]*?)</v>").find(fullCell)
                    val tMatch = Regex("<t>([\\s\\S]*?)</t>").find(fullCell)
                    val rawVal = vMatch?.groupValues?.get(1) ?: tMatch?.groupValues?.get(1) ?: ""
                    val resolved = if (isSharedString) {
                        val idx = rawVal.toIntOrNull() ?: -1
                        if (idx in sharedStrings.indices) sharedStrings[idx] else rawVal
                    } else rawVal
                    if (resolved.isNotBlank()) {
                        rowCells.add(resolved)
                    }
                }
                if (rowCells.isNotEmpty()) {
                    rows.add(rowCells)
                }
            }

            val formatted = buildString {
                append("Excel Spreadsheet: '${file.name}' (${rows.size} rows)\n\n")
                rows.take(50).forEachIndexed { idx, r ->
                    append("Row $idx: ${r.joinToString(" | ")}\n")
                }
                if (rows.size > 50) append("... [${rows.size - 50} more rows]")
            }

            FileOperationResult.Success(
                message = "Read ${rows.size} rows from Excel spreadsheet '${file.name}'",
                content = formatted,
                details = mapOf("rowCount" to rows.size)
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to read Excel file '${file.path}': ${e.message}")
        }
    }

    fun editXlsxCell(file: File, targetText: String, replacementText: String): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            var updatedAny = false
            val sharedStrings = extractZipEntryText(file, "xl/sharedStrings.xml")
            if (sharedStrings != null && sharedStrings.contains(targetText)) {
                val updatedSst = sharedStrings.replace(targetText, replacementText)
                updateZipEntryText(file, "xl/sharedStrings.xml", updatedSst)
                updatedAny = true
            }

            val sheet1 = extractZipEntryText(file, "xl/worksheets/sheet1.xml")
            if (sheet1 != null && sheet1.contains(targetText)) {
                val updatedSheet = sheet1.replace(targetText, replacementText)
                updateZipEntryText(file, "xl/worksheets/sheet1.xml", updatedSheet)
                updatedAny = true
            }

            if (updatedAny) {
                FileOperationResult.Success("Replaced '$targetText' with '$replacementText' in Excel file '${file.name}'")
            } else {
                FileOperationResult.Error("Target text '$targetText' not found in Excel file '${file.name}'.")
            }
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to edit Excel file '${file.path}': ${e.message}")
        }
    }

    // ==========================================
    // 3. OpenXML Formats (Word .docx & PPT .pptx)
    // ==========================================

    fun readDocxText(file: File): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val documentXml = extractZipEntryText(file, "word/document.xml")
                ?: return FileOperationResult.Error("Invalid DOCX: 'word/document.xml' not found.")

            val plainText = extractXmlTextContent(documentXml)
            FileOperationResult.Success(
                message = "Extracted text from Word document '${file.name}'",
                content = plainText,
                details = mapOf("charCount" to plainText.length)
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to read Word document '${file.path}': ${e.message}")
        }
    }

    fun editDocxText(file: File, targetText: String, replacementText: String): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val documentXml = extractZipEntryText(file, "word/document.xml")
                ?: return FileOperationResult.Error("Invalid DOCX: 'word/document.xml' not found.")

            if (!documentXml.contains(targetText)) {
                return FileOperationResult.Error("Target text '$targetText' not found in Word document XML.")
            }

            val updatedXml = documentXml.replace(targetText, replacementText)
            updateZipEntryText(file, "word/document.xml", updatedXml)

            FileOperationResult.Success(
                message = "Replaced '$targetText' with '$replacementText' in Word document '${file.name}'"
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to update Word document '${file.path}': ${e.message}")
        }
    }

    fun readPptxText(file: File): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val slideTexts = mutableListOf<String>()
            ZipInputStream(FileInputStream(file)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                        val slideNumber = entry.name.substringAfter("slide").substringBefore(".").toIntOrNull() ?: 0
                        val xmlContent = zip.readBytes().toString(StandardCharsets.UTF_8)
                        val slideText = extractXmlTextContent(xmlContent)
                        slideTexts.add("--- Slide $slideNumber ---\n$slideText")
                    }
                    entry = zip.nextEntry
                }
            }

            val fullPresentation = slideTexts.joinToString("\n\n")
            FileOperationResult.Success(
                message = "Extracted ${slideTexts.size} slide(s) from PowerPoint '${file.name}'",
                content = fullPresentation,
                details = mapOf("slideCount" to slideTexts.size)
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to read PowerPoint '${file.path}': ${e.message}")
        }
    }

    fun editPptxSlideText(file: File, slideNumber: Int, targetText: String, replacementText: String): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        val slidePath = if (slideNumber > 0) "ppt/slides/slide$slideNumber.xml" else null
        return try {
            if (slidePath != null) {
                val slideXml = extractZipEntryText(file, slidePath)
                    ?: return FileOperationResult.Error("Slide $slideNumber ('$slidePath') not found in presentation.")

                if (!slideXml.contains(targetText)) {
                    return FileOperationResult.Error("Target text '$targetText' not found in Slide $slideNumber.")
                }

                val updatedXml = slideXml.replace(targetText, replacementText)
                updateZipEntryText(file, slidePath, updatedXml)

                FileOperationResult.Success(
                    message = "Updated text on Slide $slideNumber in PowerPoint '${file.name}'"
                )
            } else {
                var replacedAny = false
                ZipInputStream(FileInputStream(file)).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                            val xml = zip.readBytes().toString(StandardCharsets.UTF_8)
                            if (xml.contains(targetText)) {
                                updateZipEntryText(file, entry.name, xml.replace(targetText, replacementText))
                                replacedAny = true
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
                if (replacedAny) {
                    FileOperationResult.Success("Updated PowerPoint presentation '${file.name}'")
                } else {
                    FileOperationResult.Error("Target text '$targetText' not found in PowerPoint slides.")
                }
            }
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to edit PowerPoint Slide in '${file.path}': ${e.message}")
        }
    }

    fun createDocx(file: File, title: String, content: String): FileOperationResult {
        return try {
            file.parentFile?.mkdirs()
            val cleanTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val pXmls = content.lines().joinToString("") { line ->
                val escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                "<w:p><w:r><w:t>$escaped</w:t></w:r></w:p>"
            }

            val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:r><w:rPr><w:b/><w:sz w:val="32"/></w:rPr><w:t>$cleanTitle</w:t></w:r></w:p>
    $pXmls
  </w:body>
</w:document>"""

            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

            val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

            ZipOutputStream(FileOutputStream(file)).use { zipOut ->
                writeZipEntry(zipOut, "[Content_Types].xml", contentTypes)
                writeZipEntry(zipOut, "_rels/.rels", rels)
                writeZipEntry(zipOut, "word/document.xml", docXml)
            }

            FileOperationResult.Success("Created Word document '${file.name}' (${file.length()} bytes)")
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to create Word document '${file.path}': ${e.message}")
        }
    }

    fun createXlsx(file: File, title: String, content: String): FileOperationResult {
        return try {
            file.parentFile?.mkdirs()
            val rowLines = content.lines().filter { it.isNotBlank() }
            val rowsXml = StringBuilder()

            rowLines.forEachIndexed { rIdx, line ->
                val cells = parseCsvLine(line, ",")
                val cellsXml = cells.mapIndexed { cIdx, cellVal ->
                    val colLetter = ('A' + cIdx).toString()
                    val cellRef = "$colLetter${rIdx + 1}"
                    val esc = cellVal.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    val isNum = cellVal.toDoubleOrNull() != null
                    if (isNum) {
                        "<c r=\"$cellRef\"><v>$esc</v></c>"
                    } else {
                        "<c r=\"$cellRef\" t=\"inlineStr\"><is><t>$esc</t></is></c>"
                    }
                }.joinToString("")
                rowsXml.append("<row r=\"${rIdx + 1}\">$cellsXml</row>")
            }

            val sheet1Xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    $rowsXml
  </sheetData>
</worksheet>"""

            val wbXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
  </sheets>
</workbook>"""

            val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

            val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

            ZipOutputStream(FileOutputStream(file)).use { zipOut ->
                writeZipEntry(zipOut, "[Content_Types].xml", contentTypes)
                writeZipEntry(zipOut, "_rels/.rels", rels)
                writeZipEntry(zipOut, "xl/workbook.xml", wbXml)
                writeZipEntry(zipOut, "xl/_rels/workbook.xml.rels", wbRels)
                writeZipEntry(zipOut, "xl/worksheets/sheet1.xml", sheet1Xml)
            }

            FileOperationResult.Success("Created Excel spreadsheet '${file.name}' (${file.length()} bytes)")
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to create Excel spreadsheet '${file.path}': ${e.message}")
        }
    }

    fun createPptx(file: File, title: String, content: String): FileOperationResult {
        return try {
            file.parentFile?.mkdirs()
            val cleanTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val lines = content.lines()
            val slides = if (lines.any { it.startsWith("---") || it.startsWith("Slide") }) {
                val currentSlides = mutableListOf<Pair<String, List<String>>>()
                var curTitle = ""
                var curBody = mutableListOf<String>()
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("---") || trimmed.startsWith("Slide")) {
                        if (curTitle.isNotEmpty() || curBody.isNotEmpty()) {
                            currentSlides.add(curTitle to curBody)
                            curBody = mutableListOf()
                        }
                        curTitle = trimmed.trim('-', ' ')
                    } else if (trimmed.isNotBlank()) {
                        if (curTitle.isEmpty()) {
                            curTitle = cleanTitle
                        }
                        curBody.add(trimmed)
                    }
                }
                if (curTitle.isNotEmpty() || curBody.isNotEmpty()) {
                    currentSlides.add(curTitle to curBody)
                }
                if (currentSlides.isEmpty()) listOf(cleanTitle to lines.filter { it.isNotBlank() }) else currentSlides
            } else {
                listOf(cleanTitle to lines.filter { it.isNotBlank() })
            }

            ZipOutputStream(FileOutputStream(file)).use { zipOut ->
                val sldIds = StringBuilder()
                val presRels = StringBuilder()
                val ctOverrides = StringBuilder()

                slides.forEachIndexed { idx, (sTitle, sBody) ->
                    val sNum = idx + 1
                    val rId = "rId$sNum"
                    sldIds.append("<p:sldId id=\"${255 + sNum}\" r:id=\"$rId\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>")
                    presRels.append("<Relationship Id=\"$rId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide$sNum.xml\"/>")
                    ctOverrides.append("<Override PartName=\"/ppt/slides/slide$sNum.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")

                    val bodyXml = sBody.joinToString("") { bLine ->
                        val esc = bLine.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        "<a:p><a:r><a:t>$esc</a:t></a:r></a:p>"
                    }

                    val slideXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr/>
      <p:sp>
        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr>
        <p:spPr/>
        <p:txBody>
          <a:bodyPr/>
          <a:lstStyle/>
          <a:p><a:r><a:t>${sTitle.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
      <p:sp>
        <p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr>
        <p:spPr/>
        <p:txBody>
          <a:bodyPr/>
          <a:lstStyle/>
          $bodyXml
        </p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
</p:sld>"""
                    writeZipEntry(zipOut, "ppt/slides/slide$sNum.xml", slideXml)
                }

                val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  $ctOverrides
</Types>"""

                val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

                val presXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldIdLst>
    $sldIds
  </p:sldIdLst>
</p:presentation>"""

                val presRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  $presRels
</Relationships>"""

                writeZipEntry(zipOut, "[Content_Types].xml", contentTypes)
                writeZipEntry(zipOut, "_rels/.rels", rels)
                writeZipEntry(zipOut, "ppt/presentation.xml", presXml)
                writeZipEntry(zipOut, "ppt/_rels/presentation.xml.rels", presRelsXml)
            }

            FileOperationResult.Success("Created PowerPoint presentation '${file.name}' with ${slides.size} slide(s)")
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to create PowerPoint presentation '${file.path}': ${e.message}")
        }
    }

    // ==========================================
    // 4. PDF Documents (Read & Edit/Create)
    // ==========================================

    fun readPdfText(file: File, maxPages: Int = 50): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val bytes = file.readBytes()
            val rawString = String(bytes, StandardCharsets.ISO_8859_1)

            val textBlocks = mutableListOf<String>()
            val streamRegex = Regex("""stream\s*[\r\n]+([\s\S]*?)[\r\n]+endstream""")
            val matches = streamRegex.findAll(rawString)

            matches.take(maxPages).forEach { match ->
                val streamContent = match.groupValues[1]
                val tjRegex = Regex("""\(([^()]*)\)\s*(?:T[jJ]|'|")""")
                val extracted = tjRegex.findAll(streamContent).map { it.groupValues[1] }.joinToString(" ")
                if (extracted.isNotBlank()) {
                    textBlocks.add(extracted)
                }
            }

            val content = if (textBlocks.isNotEmpty()) {
                textBlocks.joinToString("\n\n")
            } else {
                "PDF document '${file.name}' (${bytes.size} bytes)."
            }

            FileOperationResult.Success(
                message = "Read PDF '${file.name}' (${bytes.size} bytes)",
                content = content,
                details = mapOf("bytes" to bytes.size)
            )
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to read PDF '${file.path}': ${e.message}")
        }
    }

    fun editPdfText(file: File, targetText: String, replacementText: String): FileOperationResult {
        if (!file.exists()) return FileOperationResult.Error("File '${file.path}' does not exist.")
        return try {
            val rawBytes = file.readBytes()
            val rawString = String(rawBytes, StandardCharsets.ISO_8859_1)

            if (!rawString.contains(targetText)) {
                return FileOperationResult.Error("Target text '$targetText' not found in PDF streams.")
            }

            val updatedString = rawString.replace(targetText, replacementText)
            file.writeBytes(updatedString.toByteArray(StandardCharsets.ISO_8859_1))

            FileOperationResult.Success("Updated text '$targetText' with '$replacementText' in PDF '${file.name}'")
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to edit PDF '${file.path}': ${e.message}")
        }
    }

    fun createPdf(file: File, title: String, content: String): FileOperationResult {
        return try {
            file.parentFile?.mkdirs()
            val cleanTitle = title.replace("(", "[").replace(")", "]")
            val cleanContent = content.replace("(", "[").replace(")", "]")

            val pdfData = buildString {
                append("%PDF-1.4\n")
                append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
                append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
                append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n")
                
                val streamContent = buildString {
                    append("BT\n/F1 18 Tf\n50 720 Td\n($cleanTitle) Tj\nET\n")
                    append("BT\n/F1 12 Tf\n50 680 Td\n16 TL\n")
                    cleanContent.lines().take(40).forEach { line ->
                        append("(${line.take(80)}) '\n")
                    }
                    append("ET\n")
                }
                
                append("4 0 obj\n<< /Length ${streamContent.length} >>\nstream\n")
                append(streamContent)
                append("endstream\nendobj\n")
                append("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
                append("xref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000224 00000 n \n0000000300 00000 n \n")
                append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n380\n%%EOF\n")
            }

            file.writeText(pdfData, StandardCharsets.ISO_8859_1)
            FileOperationResult.Success("Created PDF document '${file.name}' (${file.length()} bytes)")
        } catch (e: Exception) {
            FileOperationResult.Error("Failed to create PDF '${file.path}': ${e.message}")
        }
    }

    // ==========================================
    // Helper Utilities
    // ==========================================

    private fun parseCsvLine(line: String, delimiter: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (line.startsWith(delimiter, i) && !inQuotes) {
                result.add(current.toString().trim())
                current.clear()
                i += delimiter.length - 1
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun escapeCsvCell(cell: String, delimiter: String): String {
        return if (cell.contains(delimiter) || cell.contains('"') || cell.contains('\n')) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }
    }

    private fun extractZipEntryText(zipFile: File, entryPath: String): String? {
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryPath) {
                    return zip.readBytes().toString(StandardCharsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun updateZipEntryText(zipFile: File, entryPath: String, newContent: String) {
        val tempFile = File.createTempFile("orbital_zip_edit", ".tmp")
        val newBytes = newContent.toByteArray(StandardCharsets.UTF_8)

        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            ZipOutputStream(FileOutputStream(tempFile)).use { zipOut ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == entryPath) {
                        zipOut.putNextEntry(ZipEntry(entryPath))
                        zipOut.write(newBytes)
                        zipOut.closeEntry()
                    } else {
                        zipOut.putNextEntry(ZipEntry(entry.name))
                        zipIn.copyTo(zipOut)
                        zipOut.closeEntry()
                    }
                    entry = zipIn.nextEntry
                }
            }
        }

        tempFile.copyTo(zipFile, overwrite = true)
        tempFile.delete()
    }

    private fun extractXmlTextContent(xmlString: String): String {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xmlString.toByteArray(StandardCharsets.UTF_8)))
            
            val sb = StringBuilder()
            extractNodeText(doc.documentElement, sb)
            sb.toString().trim()
        } catch (_: Exception) {
            // Fallback regex tag stripping
            xmlString.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        }
    }

    private fun extractNodeText(node: Node, sb: StringBuilder) {
        if (node.nodeType == Node.TEXT_NODE) {
            val text = node.nodeValue?.trim()
            if (!text.isNullOrBlank()) {
                sb.append(text).append(" ")
            }
        }
        if (node.nodeName == "w:p" || node.nodeName == "a:p") {
            sb.append("\n")
        }
        var child = node.firstChild
        while (child != null) {
            extractNodeText(child, sb)
            child = child.nextSibling
        }
    }

    private fun writeZipEntry(zipOut: ZipOutputStream, entryName: String, content: String) {
        zipOut.putNextEntry(ZipEntry(entryName))
        zipOut.write(content.toByteArray(StandardCharsets.UTF_8))
        zipOut.closeEntry()
    }
}
