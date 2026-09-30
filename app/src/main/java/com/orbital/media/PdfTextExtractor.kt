package com.orbital.media

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.InflaterInputStream

object PdfTextExtractor {

    /**
     * Extracts pure human-readable text from any standard PDF stream or byte array.
     * Decodes uncompressed objects and FlateDecode/zlib compressed streams.
     */
    fun extractText(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        return extractTextFromBytes(bytes)
    }

    fun extractTextFromBytes(bytes: ByteArray): String {
        val rawContent = String(bytes, Charsets.ISO_8859_1)
        val extractedLines = mutableListOf<String>()

        // 1. Search for FlateDecode streams and decompress them
        val streamRegex = Regex("""<<[^>]*?/Filter\s*/FlateDecode[^>]*?>>\s*stream\r?\n([\s\S]*?)\r?\nendstream""")
        val matches = streamRegex.findAll(rawContent)

        for (match in matches) {
            val streamStart = match.groups[1]?.range?.first ?: continue
            val streamEnd = match.groups[1]?.range?.last ?: continue

            val compressedBytes = bytes.sliceArray(streamStart..streamEnd)
            try {
                val decompressed = InflaterInputStream(ByteArrayInputStream(compressedBytes)).readBytes()
                val streamText = String(decompressed, Charsets.ISO_8859_1)
                val textFromStream = parsePdfTextOperators(streamText)
                if (textFromStream.isNotBlank()) {
                    extractedLines.add(textFromStream)
                }
            } catch (_: Exception) {}
        }

        // 2. Also check uncompressed text blocks in the raw PDF
        val rawText = parsePdfTextOperators(rawContent)
        if (rawText.isNotBlank()) {
            extractedLines.add(rawText)
        }

        val combined = extractedLines.joinToString("\n").trim()
        return if (combined.isNotBlank()) {
            cleanExtractedText(combined)
        } else {
            // Fallback: extract printable ascii words from the raw byte stream
            extractAsciiWords(rawContent)
        }
    }

    private fun parsePdfTextOperators(content: String): String {
        val sb = StringBuilder()

        // BT ... ET blocks contain text
        val btRegex = Regex("""BT\s+([\s\S]*?)\s+ET""")
        val btMatches = btRegex.findAll(content)

        for (bt in btMatches) {
            val block = bt.groupValues[1]

            // Match Tj, TJ, and ' operators
            // (string) Tj
            val tjRegex = Regex("""\(([^)]*)\)\s*Tj""")
            for (m in tjRegex.findAll(block)) {
                sb.append(decodePdfString(m.groupValues[1])).append(" ")
            }

            // [(array) 120 (of) -20 (strings)] TJ
            val arrayTjRegex = Regex("""\[([\s\S]*?)\]\s*TJ""")
            for (m in arrayTjRegex.findAll(block)) {
                val inner = m.groupValues[1]
                val strInArray = Regex("""\(([^)]*)\)""")
                val lineSb = StringBuilder()
                for (strMatch in strInArray.findAll(inner)) {
                    lineSb.append(decodePdfString(strMatch.groupValues[1]))
                }
                sb.append(lineSb.toString()).append(" ")
            }

            // Hex strings <00480065006C006C006F> Tj
            val hexRegex = Regex("""<([0-9A-Fa-f]+)>\s*Tj""")
            for (m in hexRegex.findAll(block)) {
                sb.append(decodeHexString(m.groupValues[1])).append(" ")
            }

            sb.append("\n")
        }

        return sb.toString().trim()
    }

    private fun decodePdfString(str: String): String {
        return str
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun decodeHexString(hex: String): String {
        return try {
            val bytes = ByteArray(hex.length / 2)
            for (i in bytes.indices) {
                bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            if (bytes.size >= 2 && bytes[0] == 0.toByte()) {
                String(bytes, Charsets.UTF_16BE)
            } else {
                String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun cleanExtractedText(text: String): String {
        return text
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun extractAsciiWords(raw: String): String {
        val wordRegex = Regex("""[A-Za-z0-9@.,:/\-_+()]{3,}""")
        val words = wordRegex.findAll(raw).map { it.value }.toList()
        val meaningful = words.filter { !it.startsWith("/") && !it.contains("Font") && !it.contains("Length") && !it.contains("Filter") }
        return meaningful.take(500).joinToString(" ")
    }
}
