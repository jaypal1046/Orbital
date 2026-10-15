package com.orbital.crash

import org.junit.Test
import org.junit.Assert.assertNotNull

class CrashReporterEngineTest {

    @Test
    fun testCrashReporterEngineSafeFallbackWithoutFirebase() {
        // In local unit test environments without google-services runtime,
        // all calls must silently handle the absence of Firebase without throwing.
        CrashReporterEngine.addBreadcrumb("Unit test breadcrumb")
        CrashReporterEngine.logEvent("test_event", mapOf("test_key" to "test_value"))
        CrashReporterEngine.setUserId("test_user_id")
        CrashReporterEngine.setCustomKey("test_key", "test_val")
        CrashReporterEngine.setCustomKey("test_bool", true)
        CrashReporterEngine.setCustomKey("test_int", 42)
        CrashReporterEngine.trackScreen("TestScreen")
        CrashReporterEngine.recordException(
            throwable = RuntimeException("Sample non-fatal exception"),
            isFatal = false,
            customAttributes = mapOf("context" to "unit_test")
        )
        assertNotNull(CrashReporterEngine)
    }
}
