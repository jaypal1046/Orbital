package com.orbital.media.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentParserTest {

    @Test
    fun `formatMatrixToMarkdown creates aligned GitHub markdown table`() {
        val matrix = listOf(
            listOf("Flight", "Route", "Departure", "Price"),
            listOf("AI-102", "DEL -> BOM", "08:30", "$120"),
            listOf("6E-505", "BOM -> BLR", "14:00", "$85")
        )

        val markdownTable = TableMarkdownFormatter.formatMatrixToMarkdown(matrix)

        assertThat(markdownTable).contains("| Flight")
        assertThat(markdownTable).contains("| AI-102")
        assertThat(markdownTable).contains(":---")
        assertThat(markdownTable.lines()).hasSize(4)
    }

    @Test
    fun `parseDelimitedLinesToMatrix correctly parses space aligned text`() {
        val rawLines = listOf(
            "Item       Qty   Price",
            "MacBook    1     $2000",
            "iPhone     2     $1800"
        )

        val matrix = TableMarkdownFormatter.parseDelimitedLinesToMatrix(rawLines)

        assertThat(matrix).hasSize(3)
        assertThat(matrix[0]).containsExactly("Item", "Qty", "Price").inOrder()
        assertThat(matrix[1]).containsExactly("MacBook", "1", "$2000").inOrder()
    }

    @Test
    fun `SemanticDocumentChunker segments text into headings, paragraphs, and tables`() {
        val documentText = """
            # INVOICE SUMMARY
            
            This is the official invoice statement for the month of September.
            
            Item       Qty   Total
            Hosting    1     $50
            Database   1     $120
            
            - Payment due in 30 days
            - Please wire to account #9981
        """.trimIndent()

        val blocks = SemanticDocumentChunker.chunkText(documentText)

        assertThat(blocks).isNotEmpty()
        val types = blocks.map { it.type }
        assertThat(types).contains(DocBlockType.TITLE)
        assertThat(types).contains(DocBlockType.PARAGRAPH)
        assertThat(types).contains(DocBlockType.TABLE)
        assertThat(types).contains(DocBlockType.LIST_ITEM)
    }

    @Test
    fun `HybridDocumentPipeline processes raw text and extracts tables`() {
        val pipeline = HybridDocumentPipeline()
        val text = """
            # Salary Slip
            
            Employee: John Doe
            
            Earnings      Amount
            Basic Pay     $5000
            Allowance     $1200
        """.trimIndent()

        val result = pipeline.processRawText(title = "Salary.pdf", rawText = text)

        assertThat(result.fileName).isEqualTo("Salary.pdf")
        assertThat(result.extractedTables).isNotEmpty()
        assertThat(result.fullMarkdown).contains("| Earnings")
    }
}
