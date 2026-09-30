package com.orbital.decision.jev

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import com.orbital.automation.UIElement
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JevDecisionEngineTest {

    private lateinit var localEngine: JevLocalFallbackEngine
    private lateinit var nodeRanker: AccessibilityNodeRanker

    @Before
    fun setUp() {
        localEngine = JevLocalFallbackEngine()
        nodeRanker = AccessibilityNodeRanker(localEngine)
    }

    @Test
    fun `decideBinary returns true when context affirms question`() = runTest {
        val context = "The screen currently displays 'Booking Confirmed' with order ID #9821."
        val question = "Is the booking confirmed?"

        val decision = localEngine.decideBinary(context, question, confidenceThreshold = 0.5f)

        assertThat(decision.value).isTrue()
        assertThat(decision.confidence).isGreaterThan(0.5f)
        assertThat(decision.metadata.isLocalExecution).isTrue()
    }

    @Test
    fun `decideChoice correctly selects relevant option with Softmax distribution`() = runTest {
        val context = "User wants to send a message to a friend."
        val prompt = "Select the messaging app"
        val options = listOf("Calculator", "Settings", "Messages", "Clock")

        val choice = localEngine.decideChoice(
            context = context,
            prompt = prompt,
            candidates = options,
            keyExtractor = { it }
        )

        assertThat(choice.value).isEqualTo("Messages")
        assertThat(choice.confidence).isGreaterThan(0.25f)
        assertThat(choice.distribution).containsKey("Messages")
        assertThat(choice.distribution["Messages"]).isGreaterThan(choice.distribution["Calculator"] ?: 0f)
    }

    @Test
    fun `AccessibilityNodeRanker ranks target UI element on top dynamically`() = runTest {
        val dummyElements = listOf(
            UIElement(
                text = "Home",
                contentDescription = null,
                viewId = "com.app:id/nav_home",
                className = "android.widget.TextView",
                isClickable = true,
                isEditable = false,
                bounds = Rect(0, 0, 100, 100)
            ),
            UIElement(
                text = "Search Flights",
                contentDescription = "Search for available flights",
                viewId = "com.app:id/btn_search_flights",
                className = "android.widget.Button",
                isClickable = true,
                isEditable = false,
                bounds = Rect(0, 100, 200, 200)
            ),
            UIElement(
                text = "Profile",
                contentDescription = null,
                viewId = "com.app:id/nav_profile",
                className = "android.widget.TextView",
                isClickable = true,
                isEditable = false,
                bounds = Rect(200, 0, 300, 100)
            )
        )

        val ranked = nodeRanker.rankElementsForGoal(
            goalDescription = "Find and book a flight",
            screenContext = "Flight Booking Main Screen",
            elements = dummyElements
        )

        assertThat(ranked).isNotEmpty()
        assertThat(ranked.first().element.text).isEqualTo("Search Flights")
        assertThat(ranked.first().isTopMatch).isTrue()
    }

    @Test
    fun `JevRemoteClient gracefully falls back to local engine on null or empty url`() = runTest {
        val client = JevRemoteClient(
            baseUrl = null,
            apiKey = null,
            localFallback = localEngine
        )

        val decision = client.decideBinary(
            context = "Screen title is Settings",
            question = "Is this settings screen?"
        )

        assertThat(decision.value).isTrue()
        assertThat(decision.metadata.isLocalExecution).isTrue()
    }
}
