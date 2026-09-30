package com.orbital.media.parser

object TableMarkdownFormatter {

    /**
     * Converts a 2D matrix of cell strings into a clean GitHub Flavored Markdown table.
     */
    fun formatMatrixToMarkdown(matrix: List<List<String>>): String {
        if (matrix.isEmpty() || matrix.all { it.isEmpty() }) return ""

        val columnCount = matrix.maxOf { it.size }
        if (columnCount == 0) return ""

        // Normalize matrix so all rows have equal columns
        val normalizedMatrix = matrix.map { row ->
            if (row.size < columnCount) {
                row + List(columnCount - row.size) { "" }
            } else row
        }

        // Calculate maximum width per column
        val colWidths = IntArray(columnCount) { colIdx ->
            var maxW = 3
            for (row in normalizedMatrix) {
                val len = row[colIdx].trim().length
                if (len > maxW) maxW = len
            }
            maxW
        }

        val sb = StringBuilder()

        // 1. Header Row
        val header = normalizedMatrix.first()
        sb.append("|")
        for (i in header.indices) {
            sb.append(" ").append(header[i].trim().padEnd(colWidths[i])).append(" |")
        }
        sb.append("\n")

        // 2. Separator Row
        sb.append("|")
        for (i in header.indices) {
            sb.append(" :").append("-".repeat(maxOf(1, colWidths[i] - 1))).append(" |")
        }
        sb.append("\n")

        // 3. Data Rows
        for (rowIdx in 1 until normalizedMatrix.size) {
            val row = normalizedMatrix[rowIdx]
            sb.append("|")
            for (colIdx in row.indices) {
                sb.append(" ").append(row[colIdx].trim().padEnd(colWidths[colIdx])).append(" |")
            }
            sb.append("\n")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Detects and parses space/tab/pipe delimited text lines into structured 2D matrices.
     */
    fun parseDelimitedLinesToMatrix(lines: List<String>): List<List<String>> {
        val matrix = mutableListOf<List<String>>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val cells = when {
                trimmed.contains("|") -> {
                    trimmed.split("|")
                        .map { it.trim() }
                        .filterIndexed { index, s ->
                            // Ignore empty first/last tokens resulting from leading/trailing pipe
                            !(index == 0 && s.isEmpty() && trimmed.startsWith("|")) &&
                            !(s.isEmpty() && trimmed.endsWith("|"))
                        }
                }
                trimmed.contains("\t") -> {
                    trimmed.split("\t").map { it.trim() }
                }
                trimmed.contains("  ") -> {
                    trimmed.split(Regex("\\s{2,}")).map { it.trim() }
                }
                trimmed.contains(",") -> {
                    trimmed.split(",").map { it.trim() }
                }
                else -> listOf(trimmed)
            }

            if (cells.isNotEmpty()) {
                matrix.add(cells)
            }
        }

        return matrix
    }
}
