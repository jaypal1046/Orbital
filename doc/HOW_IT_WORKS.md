# 🛰️ How Orbital Works — Complete System & Architecture Guide

This document provides a comprehensive technical and operational breakdown of **Orbital**: an autonomous floating AI companion, on-device automation engine, and Model Context Protocol (MCP) bridge for Android.

---

## 🏛️ 1. High-Level Architecture Overview

Orbital operates across three interconnected layers that bridge on-device user interaction with developer AI environments:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           1. USER INTERFACE                             │
│  • Floating Mascot Overlay HUD (OverlayService / WindowManager)         │
│  • Fullscreen Jetpack Compose Chat UI (ChatActivity / ViewModel)        │
│  • Setup Wizard & Character Customization (Aether / Lumy / Volo)        │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      2. ON-DEVICE COGNITIVE CORE                        │
│  • Chat Engine & Multi-Provider LLM Router (Gemini, Claude, OpenAI)    │
│  • OpenJEV System 1 Decision Engine (Intent parsing & action routing)   │
│  • Device Action Registry & Hindsight Memory Manager                    │
│  • Embedded Ktor Local HTTP/WebSocket Server (Port 3001)                │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   3. ACCESSIBILITY & AUTOMATION ENGINE                  │
│  • OrbitalAccessibilityService (UI Hierarchy inspection & gestures)    │
│  • Secure Token Bridge (HMAC-authenticated session pairing)             │
│  • Model Context Protocol (MCP) Server (IDE Wireless Remote Control)    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🧩 2. Core Components & Subsystems

### A. The Floating Mascot Companion (`OverlayService`)
- **System Alert Window**: Utilizes Android's `SYSTEM_ALERT_WINDOW` permission to render an interactive floating mascot directly over any active application.
- **Physics & Gestures**: Responsive drag-and-drop physics, auto-docking to screen edges, touch animations, and quick-action menu expansion.
- **Micro-HUD & Status**: Visual feedback indicating device states, AI reasoning steps, voice input listening, and automation execution progress.

### B. Onboarding & Jetpack Compose Chat UI
- **Modern Jetpack Compose Interface**: Dynamic markdown rendering, syntax-highlighted code blocks, interactive action confirmation cards, and chat export.
- **Setup Wizard**: Step-by-step onboarding verifying required Android permissions (Accessibility, Overlay, Notification, Audio).
- **Multi-Persona System**: Switchable companion personalities (e.g. *Aether* — balanced assistant, *Lumy* — playful creative, *Volo* — high-efficiency developer/task assistant).

### C. Zero-Latency Accessibility Automation (`OrbitalAccessibilityService`)
- **Semantic Hierarchy Parser**: Ingests the live Android `AccessibilityNodeInfo` tree in real time, extracting element bounds, resource IDs, view classes, content descriptions, and text.
- **Dynamic Gestures**: Dispatches precision programmatic gestures:
  - `tap_phone_element` / `tap_phone_coordinates`
  - `swipe_phone_screen` (scroll up, down, swipe gestures)
  - `type_phone_text` (IME and accessibility text insertion)
  - `press_phone_key` (Back, Home, Recents, Volume, Power)
- **Zero Hardcoding**: All element interactions are computed dynamically from live bounding boxes and screen accessibility trees.

### D. Model Context Protocol (MCP) Mobile Bridge
- **Embedded Ktor Server**: Runs a lightweight, battery-efficient local server on `http://127.0.0.1:3001` (exposed via USB/Wi-Fi ADB port forwarding).
- **IDE Integration**: Direct tool integration for developer environments (**Antigravity**, **Cursor**, **Claude Code**, **VS Code**).
- **13 Standalone MCP Tools**:
  - `inspect_phone_screen`: Returns structured accessibility tree or compact visual hierarchy.
  - `tap_phone_element` & `tap_phone_coordinates`: Precision tap execution.
  - `type_phone_text`: Text input into active fields.
  - `swipe_phone_screen`: Multi-directional swipe and scrolling.
  - `open_phone_app`: Package manager resolution and app launching.
  - `press_phone_key`: Hardware and navigation key simulation.
  - `take_phone_screenshot`: High-resolution screen capture stream.
  - `execute_phone_task_batch`: Multi-step automation batching.
  - `explore_and_analyze_app`: Autonomous on-device UI exploration and test reporting.

---

## 🔒 3. Privacy, Security & Data Handling Principles

Orbital is engineered with a strict **Privacy-First, Zero-Leakage Architecture**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        DATA PRIVACY GUARANTEE                           │
├────────────────────────────────┬────────────────────────────────────────┤
│ Local Storage                  │ 100% on-device AES-256 GCM encrypted   │
│ Analytics / Trackers           │ 0 third-party analytics or ads SDKs    │
│ Voice & Audio                  │ Processed ephemerally in real-time     │
│ Remote Data Harvesting         │ No telemetry collection / selling data │
│ IDE Bridge Authentication      │ Cryptographic session key handshake    │
└────────────────────────────────┴────────────────────────────────────────┘
```

1. **Client-Side Storage**:
   - All chat history, preferences, and custom API keys are encrypted on-device using Android `EncryptedSharedPreferences` backed by the hardware **Android Keystore**.
2. **Zero Third-Party SDKs**:
   - No Firebase Analytics, no Facebook SDK, no advertising networks, and no external tracking frameworks.
3. **Audio & Camera Privacy**:
   - **Microphone**: Audio recording is ephemeral and active only during live user-initiated voice conversation; audio is never persisted to disk.
   - **Camera**: Used exclusively for local QR code pairing to establish secure bridge sessions with your laptop.
4. **Accessibility Service Isolation**:
   - `OrbitalAccessibilityService` operates strictly within the device sandbox. Screen hierarchies and input events are never transmitted to external cloud servers; they are processed locally or streamed directly to your authenticated laptop IDE bridge session.
5. **Session Security (`.orbtoken`)**:
   - Connections to the embedded Ktor server require a cryptographic pairing token exchange, preventing unauthorized local apps or network peers from commanding the device.

---

## 🚀 4. Execution Workflow Example (Laptop Agent ↔ Phone)

1. **Pairing**: Laptop connects over ADB port forward (`tcp:3001:3001`) with the shared pairing token.
2. **Perception**: The agent invokes `inspect_phone_screen`, receiving the active screen's UI hierarchy.
3. **Reasoning**: The AI model formulates the optimal action sequence (e.g. find element with text "Search", click it, and type query).
4. **Execution**: `tap_phone_element` and `type_phone_text` are dispatched instantly via `OrbitalAccessibilityService`.
5. **Verification**: Live screenshot or element state is checked to verify task completion and update the user.
