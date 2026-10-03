package com.orbital.action

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionParserTest {
    @Test
    fun parsesMultiStepActions() {
        val parsed = ActionParser.parse("""Done. ```action
            {"actions":[{"action":"SET_TIMER","seconds":900},{"action":"PLAY_MUSIC","target":"Spotify","query":"lo-fi"}]}
            ```""".trimIndent())

        assertEquals(2, parsed.actions.size)
        assertEquals("SET_TIMER", parsed.actions[0].action)
        assertEquals("lo-fi", parsed.actions[1].query)
    }

    @Test
    fun parsesTopLevelJsonArray() {
        val parsed = ActionParser.parse("""
            ```action
            [{"action":"OPEN_APP","target":"Naukri"},{"action":"SCHEDULE_MONITOR","title":"Refresh","repeat_minutes":60}]
            ```
        """.trimIndent())

        assertEquals(2, parsed.actions.size)
        assertEquals("OPEN_APP", parsed.actions[0].action)
        assertEquals("Naukri", parsed.actions[0].target)
        assertEquals("SCHEDULE_MONITOR", parsed.actions[1].action)
        assertEquals(60L, parsed.actions[1].repeatMinutes)
    }

    @Test
    fun parsesInlineJsonWithMultiActions() {
        val raw = """I will perform this for you: {"actions":[{"action":"OPEN_APP","target":"WhatsApp"}]}"""
        val parsed = ActionParser.parse(raw)
        assertEquals(1, parsed.actions.size)
        assertEquals("OPEN_APP", parsed.actions[0].action)
        assertEquals("WhatsApp", parsed.actions[0].target)
    }
}
