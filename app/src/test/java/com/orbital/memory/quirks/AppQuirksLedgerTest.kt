package com.orbital.memory.quirks

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppQuirksLedgerTest {

    private lateinit var ledger: AppQuirksLedger

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        ledger = AppQuirksLedger(context)
    }

    @After
    fun tearDown() = runTest {
        ledger.clearQuirks()
    }

    @Test
    fun `recordQuirk saves and retrieves workaround for specific package`() = runTest {
        val pkg = "com.sample.music.app"
        ledger.recordQuirk(
            packageName = pkg,
            failedAction = "SEARCH",
            workaroundDescription = "Tap search icon before typing query"
        )

        val quirks = ledger.getQuirksForPackage(pkg)
        assertThat(quirks).hasSize(1)
        assertThat(quirks[0].failedAction).isEqualTo("SEARCH")
        assertThat(quirks[0].workaroundDescription).contains("Tap search icon")

        val workaround = ledger.getWorkaround(pkg, "SEARCH")
        assertThat(workaround).isEqualTo("Tap search icon before typing query")
    }

    @Test
    fun `recordQuirk increments occurrenceCount on repeated registrations`() = runTest {
        val pkg = "com.sample.notes"
        ledger.recordQuirk(pkg, "SAVE", "Scroll down to find save button")
        ledger.recordQuirk(pkg, "SAVE", "Scroll down to find save button")

        val quirks = ledger.getQuirksForPackage(pkg)
        assertThat(quirks).hasSize(1)
        assertThat(quirks[0].occurrenceCount).isEqualTo(2)
    }

    @Test
    fun `clearQuirks purges all registered quirks`() = runTest {
        ledger.recordQuirk("com.test.app", "ACTION", "Workaround")
        ledger.clearQuirks()

        val quirks = ledger.getQuirksForPackage("com.test.app")
        assertThat(quirks).isEmpty()
    }
}
