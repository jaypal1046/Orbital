package com.orbital.automation

import android.graphics.Rect
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppTestStateTest {
    @Test
    fun editableValuesAreExcludedFromPromptAndScreenIdentity() {
        val packageName = RuntimeEnvironment.getApplication().packageName
        val fieldId = UUID.randomUUID().toString()
        val hint = UUID.randomUUID().toString()
        val firstValue = UUID.randomUUID().toString()
        val secondValue = UUID.randomUUID().toString()
        fun screen(value: String) = ScreenHierarchySnapshot(
            packageName, null,
            listOf(UIElement(value, null, fieldId, "", false, false, true, Rect(), hint))
        )

        assertEquals(screen(firstValue).testStateHash(), screen(secondValue).testStateHash())
        assertTrue(screen(firstValue).toPromptSummary().contains(hint))
        assertFalse(screen(firstValue).toPromptSummary().contains(firstValue))
    }
}
