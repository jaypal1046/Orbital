package com.orbital.file

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UniversalFileEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File

    @Before
    fun setUp() {
        rootDir = tempFolder.root
    }

    @Test
    fun `detectCategory recognizes all file extensions`() {
        assertEquals(UniversalFileEngine.FileCategory.TEXT_OR_CODE, UniversalFileEngine.detectCategory("notes.txt"))
        assertEquals(UniversalFileEngine.FileCategory.TEXT_OR_CODE, UniversalFileEngine.detectCategory("README.md"))
        assertEquals(UniversalFileEngine.FileCategory.TEXT_OR_CODE, UniversalFileEngine.detectCategory("config.json"))
        assertEquals(UniversalFileEngine.FileCategory.SPREADSHEET_CSV, UniversalFileEngine.detectCategory("data.csv"))
        assertEquals(UniversalFileEngine.FileCategory.SPREADSHEET_EXCEL, UniversalFileEngine.detectCategory("report.xlsx"))
        assertEquals(UniversalFileEngine.FileCategory.WORD_DOCUMENT, UniversalFileEngine.detectCategory("memo.docx"))
        assertEquals(UniversalFileEngine.FileCategory.POWERPOINT_PRESENTATION, UniversalFileEngine.detectCategory("pitch.pptx"))
        assertEquals(UniversalFileEngine.FileCategory.PDF_DOCUMENT, UniversalFileEngine.detectCategory("paper.pdf"))
    }

    @Test
    fun `readTextFile and writeTextFile operate accurately`() {
        val testFile = File(rootDir, "test.md")
        val content = "# Heading\nLine 2\nLine 3\nLine 4\nLine 5"

        val writeResult = UniversalFileEngine.writeTextFile(testFile, content)
        assertTrue(writeResult is FileOperationResult.Success)

        val readFull = UniversalFileEngine.readTextFile(testFile)
        assertTrue(readFull is FileOperationResult.Success)
        assertEquals(content, (readFull as FileOperationResult.Success).content)

        val readSlice = UniversalFileEngine.readTextFile(testFile, startLine = 2, endLine = 4)
        assertTrue(readSlice is FileOperationResult.Success)
        assertEquals("Line 2\nLine 3\nLine 4", (readSlice as FileOperationResult.Success).content)
    }

    @Test
    fun `replaceFileContent updates target text`() {
        val testFile = File(rootDir, "code.json")
        testFile.writeText("""{"version": "1.0.0", "name": "orbital"}""")

        val replaceRes = UniversalFileEngine.replaceFileContent(
            file = testFile,
            targetContent = "\"version\": \"1.0.0\"",
            replacementContent = "\"version\": \"1.1.0\""
        )
        assertTrue(replaceRes is FileOperationResult.Success)
        assertEquals("""{"version": "1.1.0", "name": "orbital"}""", testFile.readText())
    }

    @Test
    fun `searchFile finds line matches`() {
        val testFile = File(rootDir, "log.txt")
        testFile.writeText("2026-10-04 INFO Server started\n2026-10-04 ERROR Connection failed\n2026-10-04 INFO Retrying")

        val searchRes = UniversalFileEngine.searchFile(testFile, "ERROR")
        assertTrue(searchRes is FileOperationResult.Success)
        val content = (searchRes as FileOperationResult.Success).content
        assertNotNull(content)
        assertTrue(content!!.contains("Line 2: 2026-10-04 ERROR Connection failed"))
    }

    @Test
    fun `readCsv and editCsvCell manipulate table data`() {
        val csvFile = File(rootDir, "users.csv")
        csvFile.writeText("ID,Name,Status\n1,Alice,Pending\n2,Bob,Active")

        val readRes = UniversalFileEngine.readCsv(csvFile)
        assertTrue(readRes is FileOperationResult.Success)
        assertTrue((readRes as FileOperationResult.Success).content!!.contains("Alice"))

        val editRes = UniversalFileEngine.editCsvCell(csvFile, row = 1, col = 2, newValue = "Approved")
        assertTrue(editRes is FileOperationResult.Success)
        assertTrue(csvFile.readText().contains("1,Alice,Approved"))

        val appendRes = UniversalFileEngine.appendCsvRow(csvFile, listOf("3", "Charlie", "Active"))
        assertTrue(appendRes is FileOperationResult.Success)
        assertTrue(csvFile.readText().contains("3,Charlie,Active"))
    }

    @Test
    fun `readDocxText and editDocxText work on zip-packaged openxml`() {
        val docxFile = File(rootDir, "document.docx")
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>Project Alpha Progress Report</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Status: In Progress</w:t></w:r></w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        createMockZipFile(docxFile, "word/document.xml", documentXml)

        val readRes = UniversalFileEngine.readDocxText(docxFile)
        assertTrue(readRes is FileOperationResult.Success)
        assertTrue((readRes as FileOperationResult.Success).content!!.contains("Project Alpha Progress Report"))

        val editRes = UniversalFileEngine.editDocxText(docxFile, "Status: In Progress", "Status: Completed")
        assertTrue(editRes is FileOperationResult.Success)

        val updatedRead = UniversalFileEngine.readDocxText(docxFile)
        assertTrue((updatedRead as FileOperationResult.Success).content!!.contains("Status: Completed"))
    }

    @Test
    fun `readPptxText and editPptxSlideText handle presentation slides`() {
        val pptxFile = File(rootDir, "presentation.pptx")
        val slide1Xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp><p:txBody><a:p><a:r><a:t>Quarterly Roadmap</a:t></a:r></a:p></p:txBody></p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        createMockZipFile(pptxFile, "ppt/slides/slide1.xml", slide1Xml)

        val readRes = UniversalFileEngine.readPptxText(pptxFile)
        assertTrue(readRes is FileOperationResult.Success)
        assertTrue((readRes as FileOperationResult.Success).content!!.contains("Quarterly Roadmap"))

        val editRes = UniversalFileEngine.editPptxSlideText(pptxFile, slideNumber = 1, targetText = "Quarterly Roadmap", replacementText = "Annual Strategy")
        assertTrue(editRes is FileOperationResult.Success)

        val updatedRead = UniversalFileEngine.readPptxText(pptxFile)
        assertTrue((updatedRead as FileOperationResult.Success).content!!.contains("Annual Strategy"))
    }

    @Test
    fun `readXlsx and editXlsxCell handle excel sheets and shared strings`() {
        val xlsxFile = File(rootDir, "financials.xlsx")
        val sheet1Xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                    <row r="1">
                        <c r="A1" t="s"><v>0</v></c>
                        <c r="B1"><v>50000</v></c>
                    </row>
                    <row r="2">
                        <c r="A2" t="s"><v>1</v></c>
                        <c r="B2"><v>120000</v></c>
                    </row>
                </sheetData>
            </worksheet>
        """.trimIndent()

        val sharedStringsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="2" uniqueCount="2">
                <si><t>Revenue</t></si>
                <si><t>Profit</t></si>
            </sst>
        """.trimIndent()

        createMockZipMultiEntries(xlsxFile, mapOf(
            "xl/worksheets/sheet1.xml" to sheet1Xml,
            "xl/sharedStrings.xml" to sharedStringsXml
        ))

        val readRes = UniversalFileEngine.readXlsx(xlsxFile)
        assertTrue(readRes is FileOperationResult.Success)
        val readContent = (readRes as FileOperationResult.Success).content!!
        assertTrue(readContent.contains("Revenue"))
        assertTrue(readContent.contains("50000"))
        assertTrue(readContent.contains("Profit"))
        assertTrue(readContent.contains("120000"))

        val editRes = UniversalFileEngine.editXlsxCell(xlsxFile, targetText = "120000", replacementText = "150000")
        assertTrue(editRes is FileOperationResult.Success)

        val updatedRead = UniversalFileEngine.readXlsx(xlsxFile)
        assertTrue(updatedRead is FileOperationResult.Success)
        val updatedContent = (updatedRead as FileOperationResult.Success).content!!
        assertTrue(updatedContent.contains("150000"))
    }

    @Test
    fun `createPdf, readPdfText, and editPdfText execute correctly`() {
        val pdfFile = File(rootDir, "contract.pdf")
        val createRes = UniversalFileEngine.createPdf(
            file = pdfFile,
            title = "Service Contract",
            content = "Client: Acme Corp\nAmount: $10000\nTerms: Net 30"
        )
        assertTrue(createRes is FileOperationResult.Success)
        assertTrue(pdfFile.exists() && pdfFile.length() > 0)

        val readRes = UniversalFileEngine.readPdfText(pdfFile)
        assertTrue(readRes is FileOperationResult.Success)
        val readContent = (readRes as FileOperationResult.Success).content!!
        assertTrue(readContent.contains("Service Contract"))
        assertTrue(readContent.contains("Acme Corp"))

        val editRes = UniversalFileEngine.editPdfText(pdfFile, targetText = "10000", replacementText = "25000")
        assertTrue(editRes is FileOperationResult.Success)

        val updatedRead = UniversalFileEngine.readPdfText(pdfFile)
        assertTrue(updatedRead is FileOperationResult.Success)
        val updatedContent = (updatedRead as FileOperationResult.Success).content!!
        assertTrue(updatedContent.contains("25000"))
    }

    private fun createMockZipFile(zipFile: File, entryPath: String, content: String) {
        createMockZipMultiEntries(zipFile, mapOf(entryPath to content))
    }

    private fun createMockZipMultiEntries(zipFile: File, entries: Map<String, String>) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            for ((path, content) in entries) {
                zipOut.putNextEntry(ZipEntry(path))
                zipOut.write(content.toByteArray(StandardCharsets.UTF_8))
                zipOut.closeEntry()
            }
        }
    }
}
