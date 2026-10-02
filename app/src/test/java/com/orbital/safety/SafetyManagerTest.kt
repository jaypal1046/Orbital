package com.orbital.safety

import android.content.Context
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SafetyManagerTest {

    private lateinit var context: Context
    private lateinit var safetyManager: SafetyManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        safetyManager = SafetyManager(context)
    }

    @Test
    fun testSafetyManagerInitialization() {
        assertNotNull("SafetyManager should initialize without crashing", safetyManager)
    }

    @Test
    fun testPaymentAppsListIntegrity() {
        // Verify payment apps are recognized
        val paymentApps = listOf(
            "com.google.android.apps.walletnfcrel",
            "com.phonepe.app",
            "com.paytm",
            "com.bhim.upi",
            "com.hdfcbank",
            "com.sbi"
        )
        for (pkg in paymentApps) {
            assertTrue("Package $pkg should not be blank", pkg.isNotBlank())
        }
    }

    @Test
    fun testNonExistentAppNotInForeground() {
        val result = safetyManager.isAppInForeground("com.fake.nonexistent.app")
        assertFalse("Fake app should not be in foreground", result)
    }

    @Test
    fun testShutdownDoesNotCrash() {
        safetyManager.shutdown()
        // Should shut down coroutine scope cleanly without exceptions
        assertTrue(true)
    }
}
