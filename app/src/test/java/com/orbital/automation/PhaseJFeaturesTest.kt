package com.orbital.automation

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseJFeaturesTest {

    @Test
    fun `NotificationTriggerEngine matches regex and keyword rules`() {
        NotificationTriggerEngine.clearRules()

        val otpRule = NotificationTriggerRule(
            id = "otp_rule",
            keywordOrRegex = """(?:code|otp|verification)[:\s]+([0-9]{4,6})""",
            isRegex = true,
            description = "Extracts incoming OTP"
        )
        val cabRule = NotificationTriggerRule(
            id = "cab_rule",
            keywordOrRegex = "arrived",
            isRegex = false,
            description = "Alerts on ride arrival"
        )

        NotificationTriggerEngine.registerRule(otpRule)
        NotificationTriggerEngine.registerRule(cabRule)

        val notif1 = NotificationPayload("com.google.android.apps.messaging", "Security Alert", "Your verification code: 829104")
        val matches1 = NotificationTriggerEngine.processNotification(notif1)

        assertEquals(1, matches1.size)
        assertEquals("829104", matches1[0].extractedValue)

        val notif2 = NotificationPayload("com.ubercab", "Driver Update", "Your driver has arrived at the pickup location.")
        val matches2 = NotificationTriggerEngine.processNotification(notif2)

        assertEquals(1, matches2.size)
        assertEquals("cab_rule", matches2[0].rule.id)
    }

    @Test
    fun `SmartAutofillEngine classifies fields and synthesizes plan`() {
        val nameEl = UIElement("", null, "input_full_name", "android.widget.EditText", isClickable = false, isScrollable = false, isEditable = true, bounds = Rect(0, 0, 200, 50))
        val emailEl = UIElement("Enter Email Address", null, "txt_email", "android.widget.EditText", isClickable = false, isScrollable = false, isEditable = true, bounds = Rect(0, 60, 200, 110))
        val zipEl = UIElement("", null, "com.app:id/edit_zipcode", "android.widget.EditText", isClickable = false, isScrollable = false, isEditable = true, bounds = Rect(0, 120, 200, 170))

        val snapshot = ScreenHierarchySnapshot("com.example.checkout", "Shipping", listOf(nameEl, emailEl, zipEl))

        val profile = mapOf(
            FormFieldType.FULL_NAME to "Taylor Swift",
            FormFieldType.EMAIL_ADDRESS to "taylor@example.com",
            FormFieldType.POSTAL_CODE to "90210"
        )

        val plan = SmartAutofillEngine.generateAutofillPlan(snapshot, profile)
        assertEquals(3, plan.items.size)
        assertEquals("Taylor Swift", plan.items[0].valueToFill)
        assertEquals("taylor@example.com", plan.items[1].valueToFill)
        assertEquals("90210", plan.items[2].valueToFill)
    }

    @Test
    fun `SemanticMacroRecorder records taps and synthesizes SKILL md`() {
        val recorder = SemanticMacroRecorder()
        recorder.startRecording("Book Fast Train", "com.example.travel")

        val fromStationBtn = UIElement("Select Departure Station", null, "btn_from", "android.widget.Button", isClickable = true, isScrollable = false, isEditable = false, bounds = Rect(0, 0, 100, 50))
        recorder.recordTap(fromStationBtn, "com.example.travel")

        val searchInput = UIElement("Search trains", null, "input_search", "android.widget.EditText", isClickable = false, isScrollable = false, isEditable = true, bounds = Rect(0, 60, 100, 110))
        recorder.recordType(searchInput, "London", "com.example.travel")

        val actions = recorder.stopRecording()
        assertEquals(2, actions.size)

        val skill = recorder.synthesizeSkillMarkdown("Automated train booking macro")
        assertEquals("book_fast_train", skill.id)
        assertTrue(skill.instructions.contains("Select Departure Station"))
        assertTrue(skill.instructions.contains("London"))
    }

    @Test
    fun `AccessibilityAuditorEngine detects missing labels and small targets`() {
        // Missing label clickable with small touch target
        val badButton = UIElement(
            text = "",
            contentDescription = null,
            viewId = null,
            className = "android.widget.ImageButton",
            isClickable = true,
            isScrollable = false,
            isEditable = false,
            bounds = Rect().apply { left = 0; top = 0; right = 20; bottom = 20 }
        )
        // Valid button
        val goodButton = UIElement(
            text = "Save Settings",
            contentDescription = null,
            viewId = "btn_save",
            className = "android.widget.Button",
            isClickable = true,
            isScrollable = false,
            isEditable = false,
            bounds = Rect().apply { left = 0; top = 50; right = 200; bottom = 150 }
        )

        val snapshot = ScreenHierarchySnapshot("com.example.app", "Settings", listOf(badButton, goodButton))
        val report = AccessibilityAuditorEngine.auditScreen(snapshot)

        assertTrue(report.overallScore < 100)
        assertTrue(report.issues.any { it.issueType == "Missing Accessible Label" })
        assertTrue(report.issues.any { it.issueType == "Small Touch Target" })
        assertTrue(report.toMarkdownReport().contains("Mobile Accessibility & Usability Audit Report"))
    }
}
