# Orbital - Privacy Policy & Terms of Service

Official privacy policy and legal documentation repository for **Orbital: Autonomous AI Mascot & Companion**.

- **Hosted URL (GitHub Pages)**: [https://jaypal1046.github.io/orbital_policy/](https://jaypal1046.github.io/orbital_policy/)
- **Repository**: [https://github.com/jaypal1046/orbital_policy](https://github.com/jaypal1046/orbital_policy)

---

## Privacy & Data Handling Architecture

Orbital adheres to a strict **100% Client-Side, Privacy-First Architecture**:

- **Local Storage**: All chat messages, character configurations, and API keys are stored solely on-device inside hardware-backed AES-256 GCM encrypted storage (`EncryptedSharedPreferences`).
- **Zero Tracking & Analytics**: The application contains no advertising SDKs, tracking pixels, or third-party telemetry collectors.
- **Microphone**: Audio is processed ephemerally in real time solely during active voice interactions and is never saved to disk or transmitted to third parties.
- **Camera**: Used strictly for local QR-code scanning during developer laptop bridge pairing.
- **Accessibility Service**: Used exclusively for local UI inspection and gesture execution requested by the user or their paired IDE session. No screen data is harvested or transmitted externally.
- **System Overlay (`SYSTEM_ALERT_WINDOW`)**: Renders the floating mascot companion HUD upon explicit user activation.

For a full technical breakdown of the architecture, see [HOW_IT_WORKS.md](file:///c:/Jay/dev/Orbital/doc/HOW_IT_WORKS.md).
