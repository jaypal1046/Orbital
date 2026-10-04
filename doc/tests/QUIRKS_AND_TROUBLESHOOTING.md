# Android Device Quirks, OEM Permissions & Troubleshooting Guide

This guide details known OEM-specific nuances (Samsung OneUI, Xiaomi MIUI/HyperOS, OnePlus/Oppo ColorOS, Google Pixel) and how to configure a physical device for seamless, uninterrupted Orbital autonomous execution.

---

## 1. OEM-Specific Background & Permission Settings

Different Android manufacturers implement aggressive task killers and custom permission dialogs. Follow these configurations on your test device:

### 1. Samsung (One UI)
- **Battery Optimization:** Go to `Settings > Apps > Orbital > Battery` $\rightarrow$ Select **Unrestricted**.
- **Sleeping Apps:** Go to `Settings > Battery > Background usage limits` $\rightarrow$ Add **Orbital** to **Never sleeping apps**.
- **Auto Blocker:** On One UI 6+, if sideloading APKs, go to `Settings > Security and privacy > Auto Blocker` $\rightarrow$ Turn **OFF** temporarily during `./gradlew installDebug`.

### 2. Xiaomi / Redmi / Poco (MIUI & HyperOS)
- **Autostart:** Go to `Settings > Apps > Permissions > Autostart` $\rightarrow$ Enable **Orbital**.
- **Battery Saver:** Go to `Settings > Apps > Manage Apps > Orbital > Battery Saver` $\rightarrow$ Select **No restrictions**.
- **Accessibility Timeout Fix:** MIUI occasionally disables accessibility services after device reboot. Enable **Display pop-up windows while running in the background** under App Permissions.

### 3. OnePlus / Oppo / Realme (OxygenOS / ColorOS)
- **App Battery Management:** Go to `Settings > Battery > More settings > App battery management > Orbital` $\rightarrow$ Enable **Allow foreground activity**, **Allow background activity**, and **Allow auto-launch**.
- **Accessibility Service:** Go to `Settings > Additional settings > Accessibility > Orbital Accessibility Service` $\rightarrow$ Enable.

### 4. Google Pixel (Stock Android 14 / 15 / 16)
- **Battery:** Go to `Settings > Apps > All Apps > Orbital > App battery usage` $\rightarrow$ Choose **Unrestricted**.
- **Overlay & System Alerts:** Go to `Settings > Apps > Special app access > Display over other apps > Orbital` $\rightarrow$ **Allowed**.

---

## 2. Common Issues & Automated Mitigations

| Issue Observed | Root Cause | Orbital Autonomous Mitigation | Manual Fix (If Needed) |
| :--- | :--- | :--- | :--- |
| **"Accessibility Service Disabled" error** | OS killed background accessibility socket. | `SlashCommandRouter` `/doctor` command alerts user immediately. | Toggle Accessibility Service in Settings. |
| **Screen stays on previous screen after click** | Target view is non-clickable or animation not settled. | `AntiLoopDetector` detects 3 consecutive clicks and attempts `PRESS_BACK` or alternative locators. | Verify app is in foreground and not frozen. |
| **Blocking Promo / Permission Dialog** | First-time app launch popup. | `ObstacleClearanceEngine` scans for positive/negative modal buttons and auto-dismisses. | None (handled automatically). |
| **Repetitive Ping-Pong between 2 screens** | Screen oscillation cycle ($A \rightarrow B \rightarrow A \rightarrow B$). | `AntiLoopDetector` detects cycle length 2 and triggers recovery backstep. | Check query prompt clarity. |
| **Password Screen Screenshot Blank** | `FLAG_SECURE` or `isPassword` active. | `SecurityVaultEngine` masks sensitive coordinates safely before export. | Expected security behavior. |
| **USB Debugging disconnects during adb test** | Cable or sleep timeout. | Run `/doctor` or reconnect Wi-Fi ADB bridge via `adb tcpip 5555`. | Re-plug USB or restart ADB server. |

---

## 3. Quick Terminal Commands for Device Diagnostics

Run these from your development laptop while the phone is connected via USB/Wi-Fi:

### Check Connected Devices:
```bash
adb devices -l
```

### Stream Live Orbital Logs:
```bash
adb logcat -s "OrbitalAccessibilityService:*" "ReActExecutor:*" "AntiLoopDetector:*" "ForemanSupervisor:*"
```

### Install Fresh Debug Build:
```bash
./gradlew installDebug
```

### Check Battery Optimization Status for Orbital:
```bash
adb shell dumpsys deviceidle whitelist | grep com.orbital
```

### Manually Whitelist Orbital from Battery Restrictions via ADB:
```bash
adb shell cmd deviceidle whitelist +com.orbital
```
