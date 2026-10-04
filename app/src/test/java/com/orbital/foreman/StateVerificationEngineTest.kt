package com.orbital.foreman

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.UIElement
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StateVerificationEngineTest {

    private lateinit var verificationEngine: StateVerificationEngine

    @Before
    fun setUp() {
        verificationEngine = StateVerificationEngine()
    }

    private fun createSampleSnapshot(
        pkg: String = "com.orbital.demo",
        elements: List<UIElement> = emptyList()
    ) = ScreenHierarchySnapshot(
        packageName = pkg,
        activityTitle = "MainActivity",
        elements = elements
    )

    @Test
    fun `computeStateHash produces consistent hash for same screen elements`() {
        val elements = listOf(
            UIElement("Home", null, "tab_home", "android.widget.TextView", true, false, false, Rect(0, 0, 10, 10))
        )
        val snap1 = createSampleSnapshot(elements = elements)
        val snap2 = createSampleSnapshot(elements = elements)

        val hash1 = StateVerificationEngine.computeStateHash(snap1)
        val hash2 = StateVerificationEngine.computeStateHash(snap2)

        assertThat(hash1).isNotEmpty()
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `verify ContainsText succeeds when text is present`() {
        val snapshot = createSampleSnapshot(
            elements = listOf(
                UIElement("Order Placed Successfully", null, "status", "android.widget.TextView", false, false, false, Rect(0, 0, 10, 10))
            )
        )

        val result = verificationEngine.verify(
            snapshot,
            StateVerificationCriterion.ContainsText("Order Placed")
        )

        assertThat(result.isVerified).isTrue()
        assertThat(result.matchedElement?.text).isEqualTo("Order Placed Successfully")
    }

    @Test
    fun `verify ContainsText fails when text is missing`() {
        val snapshot = createSampleSnapshot(
            elements = listOf(
                UIElement("Welcome", null, "status", "android.widget.TextView", false, false, false, Rect(0, 0, 10, 10))
            )
        )

        val result = verificationEngine.verify(
            snapshot,
            StateVerificationCriterion.ContainsText("Order Placed")
        )

        assertThat(result.isVerified).isFalse()
    }

    @Test
    fun `verify ElementAbsent succeeds when element disappears`() {
        val snapshot = createSampleSnapshot(
            elements = listOf(
                UIElement("Dashboard", null, "title", "android.widget.TextView", false, false, false, Rect(0, 0, 10, 10))
            )
        )

        val result = verificationEngine.verify(
            snapshot,
            StateVerificationCriterion.ElementAbsent("Loading Spinner")
        )

        assertThat(result.isVerified).isTrue()
    }

    @Test
    fun `verify PackageMatches validates foreground package correctly`() {
        val snapshot = createSampleSnapshot(pkg = "com.android.settings")

        val pass = verificationEngine.verify(
            snapshot,
            StateVerificationCriterion.PackageMatches("com.android.settings")
        )
        assertThat(pass.isVerified).isTrue()

        val fail = verificationEngine.verify(
            snapshot,
            StateVerificationCriterion.PackageMatches("com.google.maps")
        )
        assertThat(fail.isVerified).isFalse()
    }

    @Test
    fun `computeDelta detects added and removed UI texts`() {
        val pre = createSampleSnapshot(
            elements = listOf(
                UIElement("Step 1", null, null, "TextView", false, false, false, Rect(0, 0, 10, 10)),
                UIElement("Processing...", null, null, "TextView", false, false, false, Rect(0, 0, 10, 10))
            )
        )
        val post = createSampleSnapshot(
            elements = listOf(
                UIElement("Step 1", null, null, "TextView", false, false, false, Rect(0, 0, 10, 10)),
                UIElement("Completed!", null, null, "TextView", false, false, false, Rect(0, 0, 10, 10))
            )
        )

        val delta = verificationEngine.computeDelta(pre, post)
        assertThat(delta.hasMutated).isTrue()
        assertThat(delta.addedTexts).containsExactly("Completed!")
        assertThat(delta.removedTexts).containsExactly("Processing...")
    }
}
