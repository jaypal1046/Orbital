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
}
