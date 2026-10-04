package com.orbital.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun parseMarkdownDocument_extractsTablesProperly() {
        val raw = """
            Here’s a quick roadmap for mastering spoken English:

            | Step | What to Do | Why It Helps | Suggested Resources |
            |---|---|---|---|
            | 1️⃣ | **Set a clear goal** | Knowing *why* you're learning keeps you motivated. | Write down 1-3 specific reasons. |
            | 2️⃣ | **Build a routine** | Consistency beats cramming. | Habit tracking app <br>• Calendar |

            Quick Start Checklist:
            1. Open Coursera
            2. Install Duolingo
        """.trimIndent()

        val blocks = parseMarkdownDocument(raw)

        assertThat(blocks).isNotEmpty()

        val tableBlock = blocks.filterIsInstance<MarkdownBlock.TableBlock>().firstOrNull()
        assertThat(tableBlock).isNotNull()
        assertThat(tableBlock!!.headers).containsExactly("Step", "What to Do", "Why It Helps", "Suggested Resources")
        assertThat(tableBlock.rows).hasSize(2)
        assertThat(tableBlock.rows[0][0]).isEqualTo("1️⃣")
        assertThat(tableBlock.rows[0][1]).isEqualTo("**Set a clear goal**")

        val listBlock = blocks.filterIsInstance<MarkdownBlock.ListBlock>().firstOrNull()
        assertThat(listBlock).isNotNull()
        assertThat(listBlock!!.items).hasSize(2)
    }

    @Test
    fun parseMarkdownDocument_extractsCodeFencesAndImages() {
        val raw = """
            # Setup Guide
            
            ```kotlin
            fun main() {
                println("Hello Orbital")
            }
            ```

            ![Architecture Diagram](https://example.com/arch.png)

            > Note: Always verify tokens before connecting.
            
            ---
        """.trimIndent()

        val blocks = parseMarkdownDocument(raw)

        val headingBlock = blocks.filterIsInstance<MarkdownBlock.HeadingBlock>().firstOrNull()
        assertThat(headingBlock).isNotNull()
        assertThat(headingBlock!!.level).isEqualTo(1)
        assertThat(headingBlock.text).isEqualTo("Setup Guide")

        val codeBlock = blocks.filterIsInstance<MarkdownBlock.CodeBlock>().firstOrNull()
        assertThat(codeBlock).isNotNull()
        assertThat(codeBlock!!.language).isEqualTo("kotlin")
        assertThat(codeBlock.code).contains("fun main()")

        val imageBlock = blocks.filterIsInstance<MarkdownBlock.ImageBlock>().firstOrNull()
        assertThat(imageBlock).isNotNull()
        assertThat(imageBlock!!.alt).isEqualTo("Architecture Diagram")
        assertThat(imageBlock.url).isEqualTo("https://example.com/arch.png")

        val quoteBlock = blocks.filterIsInstance<MarkdownBlock.QuoteBlock>().firstOrNull()
        assertThat(quoteBlock).isNotNull()
        assertThat(quoteBlock!!.text).contains("Note: Always verify tokens")

        val dividerBlock = blocks.filterIsInstance<MarkdownBlock.DividerBlock>().firstOrNull()
        assertThat(dividerBlock).isNotNull()
    }

    @Test
    fun splitTableCells_handlesPipesAndPaddings() {
        val cells = splitTableCells("| 1️⃣ | **Set a clear goal** | Practice daily |")
        assertThat(cells).containsExactly("1️⃣", "**Set a clear goal**", "Practice daily")
    }
}
