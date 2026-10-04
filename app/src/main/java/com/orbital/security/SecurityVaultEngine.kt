package com.orbital.security

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.UIElement

data class SensitiveElementInspection(
    val hasSensitiveFields: Boolean,
    val sensitiveElements: List<UIElement>,
    val riskCategories: Set<String>
)

object SecurityVaultEngine {

    private val SENSITIVE_KEYWORDS = listOf(
        "password", "passcode", "pin", "cvv", "security code", "otp", "upi pin",
        "card number", "credit card", "debit card", "ssn", "secret", "private key"
    )

    private val SENSITIVE_CLASS_NAMES = listOf(
        "android.widget.PasswordEditText",
        "com.google.android.material.textfield.TextInputEditText"
    )

    fun inspectScreen(snapshot: ScreenHierarchySnapshot): SensitiveElementInspection {
        val sensitiveElements = mutableListOf<UIElement>()
        val riskCategories = mutableSetOf<String>()

        snapshot.elements.forEach { el ->
            val textLower = el.text.lowercase()
            val descLower = el.contentDescription?.lowercase() ?: ""
            val idLower = el.viewId?.lowercase() ?: ""

            val matchesKeyword = SENSITIVE_KEYWORDS.any { kw ->
                textLower.contains(kw) || descLower.contains(kw) || idLower.contains(kw)
            }

            val isPasswordClass = el.className in SENSITIVE_CLASS_NAMES && (idLower.contains("pass") || idLower.contains("pin"))

            if (matchesKeyword || isPasswordClass) {
                sensitiveElements.add(el)
                when {
                    textLower.contains("pin") || idLower.contains("pin") -> riskCategories.add("PIN_ENTRY")
                    textLower.contains("pass") || idLower.contains("pass") -> riskCategories.add("PASSWORD_ENTRY")
                    textLower.contains("card") || idLower.contains("card") -> riskCategories.add("PAYMENT_CARD")
                    textLower.contains("otp") || idLower.contains("otp") -> riskCategories.add("OTP_VERIFICATION")
                    else -> riskCategories.add("SENSITIVE_DATA")
                }
            }
        }

        return SensitiveElementInspection(
            hasSensitiveFields = sensitiveElements.isNotEmpty(),
            sensitiveElements = sensitiveElements,
            riskCategories = riskCategories
        )
    }

    fun redactSensitiveRegions(bitmap: Bitmap, sensitiveElements: List<UIElement>): Bitmap {
        if (sensitiveElements.isEmpty()) return bitmap

        val mutableBitmap = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        sensitiveElements.forEach { el ->
            val bounds = el.bounds
            // Add padding to guarantee full redaction of sensitive characters
            val paddedRect = Rect(
                (bounds.left - 8).coerceAtLeast(0),
                (bounds.top - 8).coerceAtLeast(0),
                (bounds.right + 8).coerceAtMost(bitmap.width),
                (bounds.bottom + 8).coerceAtMost(bitmap.height)
            )
            canvas.drawRect(paddedRect, paint)
        }

        return mutableBitmap
    }
}
