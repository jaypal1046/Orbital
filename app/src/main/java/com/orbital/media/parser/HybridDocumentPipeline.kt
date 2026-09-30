package com.orbital.media.parser

import android.content.Context
import android.net.Uri
import com.orbital.media.DocumentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HybridDocumentPipeline {

    /**
     * Processes any attached document URI into a structured Markdown document with extracted tables.
     */
    suspend fun processDocument(
        context: Context,
        uri: Uri
    ): ParsedDocumentResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val media = DocumentReader.readUri(context, uri)

        val rawText = media.textContent.orEmpty()
        val blocks = SemanticDocumentChunker.chunkText(rawText, pageIndex = 0)

        val extractedTables = blocks
            .filter { it.type == DocBlockType.TABLE }
            .map { it.content }

        val fullMarkdown = if (blocks.isNotEmpty()) {
            blocks.joinToString("\n\n") { it.rawMarkdown }
        } else {
            rawText
        }

        ParsedDocumentResult(
            uri = uri.toString(),
            fileName = media.name,
            pageCount = media.pageCount,
            fullMarkdown = fullMarkdown,
            blocks = blocks,
            extractedTables = extractedTables,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Directly structures an existing raw string (e.g. from clipboard or web scrape).
     */
    fun processRawText(title: String, rawText: String): ParsedDocumentResult {
        val startTime = System.currentTimeMillis()
        val blocks = SemanticDocumentChunker.chunkText(rawText)
        val tables = blocks.filter { it.type == DocBlockType.TABLE }.map { it.content }
        val fullMarkdown = if (blocks.isNotEmpty()) blocks.joinToString("\n\n") { it.rawMarkdown } else rawText

        return ParsedDocumentResult(
            uri = null,
            fileName = title,
            pageCount = 1,
            fullMarkdown = fullMarkdown,
            blocks = blocks,
            extractedTables = tables,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }
}
