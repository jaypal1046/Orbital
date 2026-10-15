package com.orbital.automation

/**
 * Backwards compatibility alias for OrbitalAutomationEngine.
 * Note: Orbital does NOT extend or run an Android AccessibilityService.
 * All automation runs via rootless ADB / Shizuku scripts and Android Intents.
 */
typealias OrbitalAccessibilityService = OrbitalAutomationEngine
