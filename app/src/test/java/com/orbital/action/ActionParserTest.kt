package com.orbital.action

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ActionParserTest {
    @Test
    fun parsesMultiStepActions() {
        val parsed = ActionParser.parse("""Done. ```action
            {"actions":[{"action":"SET_TIMER","seconds":900},{"action":"PLAY_MUSIC","target":"<installed-app>","query":"<user-query>"}]}
            ```""".trimIndent())

        assertEquals(2, parsed.actions.size)
        assertEquals("SET_TIMER", parsed.actions[0].action)
        assertEquals("<user-query>", parsed.actions[1].query)
    }

    @Test
    fun parsesTopLevelJsonArray() {
        val target = "app-${UUID.randomUUID()}"
        val parsed = ActionParser.parse("""
            ```action
            [{"action":"OPEN_APP","target":"$target"},{"action":"SCHEDULE_MONITOR","title":"Refresh","repeat_minutes":60}]
            ```
        """.trimIndent())

        assertEquals(2, parsed.actions.size)
        assertEquals("OPEN_APP", parsed.actions[0].action)
        assertEquals(target, parsed.actions[0].target)
        assertEquals("SCHEDULE_MONITOR", parsed.actions[1].action)
        assertEquals(60L, parsed.actions[1].repeatMinutes)
    }

    @Test
    fun parsesInlineJsonWithMultiActions() {
        val target = "app-${UUID.randomUUID()}"
        val raw = """I will perform this for you: {"actions":[{"action":"OPEN_APP","target":"$target"}]}"""
        val parsed = ActionParser.parse(raw)
        assertEquals(1, parsed.actions.size)
        assertEquals("OPEN_APP", parsed.actions[0].action)
        assertEquals(target, parsed.actions[0].target)
    }

    @Test
    fun parsesScreenshotJsonAndNaturalIntent() {
        val parsedJson = ActionParser.parse("""```action {"action":"TAKE_SCREENSHOT"} ```""")
        assertEquals(1, parsedJson.actions.size)
        assertEquals("TAKE_SCREENSHOT", parsedJson.actions[0].action)

        val naturalIntent = ActionParser.parse("Please take a screenshot of my screen")
        assertEquals(1, naturalIntent.actions.size)
        assertEquals("TAKE_SCREENSHOT", naturalIntent.actions[0].action)

        val readIntent = ActionParser.parse("Read live screen")
        assertEquals(1, readIntent.actions.size)
        assertEquals("READ_SCREEN", readIntent.actions[0].action)
    }

    @Test
    fun parsesDocumentCreationAndEditNaturalIntents() {
        val pptAction = ActionParser.parse("""```action {"action":"WRITE_FILE","path":"pitch.pptx","content":"Slide 1: AI Future\n• Next-gen agents"}```""")
        assertEquals(1, pptAction.actions.size)
        assertEquals("WRITE_FILE", pptAction.actions[0].action)
        assertEquals("pitch.pptx", pptAction.actions[0].path)
        assertEquals("Slide 1: AI Future\n• Next-gen agents", pptAction.actions[0].content)

        val docxAction = ActionParser.parse("""```action {"action":"WRITE_FILE","path":"memo.docx","content":"# Meeting Notes\nDiscussion on Q4 roadmap"}```""")
        assertEquals(1, docxAction.actions.size)
        assertEquals("WRITE_FILE", docxAction.actions[0].action)
        assertEquals("memo.docx", docxAction.actions[0].path)

        val editIntent = ActionParser.parse("Edit the file pitch.pptx by replacing \"300%\" with \"500%\"")
        assertEquals(1, editIntent.actions.size)
        assertEquals("EDIT_FILE", editIntent.actions[0].action)
        assertEquals("pitch.pptx", editIntent.actions[0].path)
        assertEquals("300%", editIntent.actions[0].targetContent)
        assertEquals("500%", editIntent.actions[0].replacementContent)

        val openIntent = ActionParser.parse("open the file pitch.pptx")
        assertEquals(1, openIntent.actions.size)
        assertEquals("OPEN_FILE", openIntent.actions[0].action)
        assertEquals("pitch.pptx", openIntent.actions[0].path)

        val readIntent = ActionParser.parse("read the file report.pdf")
        assertEquals(1, readIntent.actions.size)
        assertEquals("READ_FILE", readIntent.actions[0].action)
        assertEquals("report.pdf", readIntent.actions[0].path)
    }
}
