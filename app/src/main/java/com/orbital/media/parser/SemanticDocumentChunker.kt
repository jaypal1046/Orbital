package com.orbital.media.parser

import java.util.UUID

object SemanticDocumentChunker {

    /**
     * Splits unstructured or layout-extracted text into typed DocumentBlocks.
     */
    fun chunkText(rawText: String, pageIndex: Int = 0): List<DocumentBlock> {
        if (rawText.isBlank()) return emptyList()

        val blocks = mutableListOf<DocumentBlock>()
        val lines = rawText.lines()
        val currentTableBuffer = mutableListOf<String>()
        val currentParagraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (currentParagraphBuffer.isNotEmpty()) {
                val content = currentParagraphBuffer.joinToString(" ").trim()
                if (content.isNotBlank()) {
                    blocks.add(
                        DocumentBlock(
                            id = UUID.randomUUID().toString(),
                            type = DocBlockType.PARAGRAPH,
                            content = content,
                            pageIndex = pageIndex,
                            rawMarkdown = content
                        )
                    )
                }
                currentParagraphBuffer.clear()
            }
        }

        fun flushTable() {
            if (currentTableBuffer.size >= 2) {
                val matrix = TableMarkdownFormatter.parseDelimitedLinesToMatrix(currentTableBuffer)
                val mdTable = TableMarkdownFormatter.formatMatrixToMarkdown(matrix)
                if (mdTable.isNotBlank()) {
                    blocks.add(
                        DocumentBlock(
                            id = UUID.randomUUID().toString(),
                            type = DocBlockType.TABLE,
                            content = mdTable,
                            pageIndex = pageIndex,
                            rawMarkdown = mdTable
                        )
                    )
                }
            } else if (currentTableBuffer.isNotEmpty()) {
                currentParagraphBuffer.addAll(currentTableBuffer)
            }
            currentTableBuffer.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                flushParagraph()
                flushTable()
                continue
            }

            // 1. Heading detection (# or all-caps short title)
            if (trimmed.startsWith("#") || (trimmed.length in 3..60 && trimmed == trimmed.uppercase() && !trimmed.contains("  "))) {
                flushParagraph()
                flushTable()
                val headingType = if (trimmed.startsWith("# ")) DocBlockType.TITLE else DocBlockType.HEADING
                blocks.add(
                    DocumentBlock(
                        id = UUID.randomUUID().toString(),
                        type = headingType,
                        content = trimmed.removePrefix("#").trim(),
                        pageIndex = pageIndex,
                        rawMarkdown = trimmed
                    )
                )
                continue
            }

            // 2. Code Block or Formula detection
            if (trimmed.startsWith("```") || trimmed.startsWith("$$")) {
                flushParagraph()
                flushTable()
                val blockType = if (trimmed.startsWith("$$")) DocBlockType.FORMULA_LATEX else DocBlockType.CODE_BLOCK
                blocks.add(
                    DocumentBlock(
                        id = UUID.randomUUID().toString(),
                        type = blockType,
                        content = trimmed,
                        pageIndex = pageIndex,
                        rawMarkdown = trimmed
                    )
                )
                continue
            }

            // 3. List Item detection (*, -, 1., etc.)
            if (trimmed.matches(Regex("^([*\\-+]|\\d+\\.)\\s+.*"))) {
                flushParagraph()
                flushTable()
                blocks.add(
                    DocumentBlock(
                        id = UUID.randomUUID().toString(),
                        type = DocBlockType.LIST_ITEM,
                        content = trimmed,
                        pageIndex = pageIndex,
                        rawMarkdown = trimmed
                    )
                )
                continue
            }

            // 4. Tabular Line Detection (contains pipes or 2+ consecutive spaces or tabs)
            val isTabularLine = trimmed.contains("|") || trimmed.contains("\t") || trimmed.contains(Regex("\\s{2,}"))
            if (isTabularLine) {
                flushParagraph()
                currentTableBuffer.add(trimmed)
            } else {
                flushTable()
                currentParagraphBuffer.add(trimmed)
            }
        }

        flushParagraph()
        flushTable()

        return blocks
    }
}
