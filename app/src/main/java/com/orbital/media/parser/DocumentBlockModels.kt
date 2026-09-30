package com.orbital.media.parser

import kotlinx.serialization.Serializable

enum class DocBlockType {
    TITLE,
    HEADING,
    PARAGRAPH,
    TABLE,
    CODE_BLOCK,
    FORMULA_LATEX,
    LIST_ITEM
}

@Serializable
data class DocumentBlock(
    val id: String,
    val type: DocBlockType,
    val content: String,
    val pageIndex: Int = 0,
    val rawMarkdown: String = content
)

data class ParsedDocumentResult(
    val uri: String?,
    val fileName: String,
    val pageCount: Int,
    val fullMarkdown: String,
    val blocks: List<DocumentBlock>,
    val extractedTables: List<String>,
    val processingTimeMs: Long
)
