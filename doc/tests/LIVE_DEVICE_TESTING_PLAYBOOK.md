# Orbital Live Device Testing Playbook & Complete Task Catalog

This document provides the complete step-by-step testing manual and exact task prompts to execute on a physical Android device to verify every subsystem of **Orbital**.

---

## 1. Pre-Flight Device Setup & Diagnostics

Before running tasks, verify that the required Android permissions and background services are active.

### Step 1: Install & Launch
1. Build and install the debug APK on your phone:
   ```bash
   ./gradlew installDebug
   ```
2. Open the **Orbital** app on the device.

### Step 2: Grant Core Permissions
- **Accessibility Service:** Go to `Settings > Accessibility > Installed Apps / Downloaded Apps > Orbital Accessibility Service` $\rightarrow$ **Enable**.
- **Display over other apps (Overlay):** Go to `Settings > Apps > Special app access > Display over other apps > Orbital` $\rightarrow$ **Allow**.
- **Notification Listener Access (Optional for Notification Triggers):** `Settings > Apps > Special app access > Device & app notifications > Orbital` $\rightarrow$ **Allow**.
- **Storage / All Files Access:** `Settings > Apps > Special app access > All files access > Orbital` $\rightarrow$ **Allow**.

### Step 3: Verify System Health via Slash Command
In the Orbital Chat prompt, send:
```text
/doctor
```
**Expected Response:**
- Returns full diagnostic health card:
  - Accessibility Service: `✅ Active & Ready`
  - Zero Hardcoding Rule: `Active`
  - Instant OTA Engine: `Enabled`

---

## 2. Comprehensive Live Test Suites & Prompts

---

### Suite A: Slash Commands & System Health

| Test ID | Input Prompt / Command | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **SYS-01** | `/doctor` | Displays instant health check and accessibility status. |
| **SYS-02** | `/help` | Returns list of available slash commands (`/goal`, `/plan`, `/doctor`, `/smoke`, `/benchmark`, `/status`, `/skills`, `/cost`, `/replay`). |
| **SYS-03** | `/smoke` | Executes autonomous 7-point on-device health check and prints markdown test report in chat. |
| **SYS-04** | `/benchmark` | Scans all installed apps and outputs an on-device semantic capability matrix report. |
| **SYS-05** | `/status` | Outputs real-time device health (Battery %, JVM Heap headroom, and Free storage space). |
| **SYS-06** | `/skills` | Lists all procedural markdown skills (`SKILL.md`) installed in the on-device repository. |
| **SYS-07** | `/cost` | Displays token pricing models, context window governor, and compaction limits. |
| **SYS-08** | `/replay` | Lists recorded session transcripts and provides step-by-step trajectory replay reports. |
| **SYS-09** | `/plan Open Settings and check display brightness` | Synthesizes a multi-step structured plan without immediately executing destructive clicks. |
| **SYS-10** | `/goal Check current battery percentage and device storage` | Autonomously executes battery & storage checks and returns real-time metrics. |

---

### Suite B: Hardware Toggles & 1-Hop Intent Acceleration

| Test ID | Input Prompt | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **HW-01** | `Turn on flashlight` | Physical phone LED flashlight turns ON immediately. |
| **HW-02** | `Turn off flashlight` | Physical phone LED flashlight turns OFF immediately. |
| **HW-03** | `Open Wi-Fi settings` | Directly launches Wi-Fi Settings screen (1-hop acceleration via `DeepLinkIntentSynthesizer`). |
| **HW-04** | `Open Bluetooth settings` | Directly launches Bluetooth Pairing screen without navigating from home screen. |
| **HW-05** | `Open Battery Saver settings` | Directly launches Battery & Power Optimization page. |
| **HW-06** | `Open https://github.com in browser` | Directly opens the default web browser and loads GitHub. |
| **HW-07** | `Directions to Central Park New York` | Launches default Maps application with encoded location query. |

---

### Suite C: Universal File & Document Engine (CRUD & Multi-Format)

| Test ID | Input Prompt | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **FILE-01** | `Create a text file called /sdcard/Download/test_orbital.txt with content: "Orbital live testing verified"` | Creates the file in the download directory and confirms write operation. |
| **FILE-02** | `Read the file /sdcard/Download/test_orbital.txt` | Reads and returns the file content via stream buffer. |
| **FILE-03** | `Search for the word "verified" in /sdcard/Download/test_orbital.txt` | Returns exact line match and line number. |
| **FILE-04** | `Edit the file /sdcard/Download/test_orbital.txt by replacing "verified" with "completed successfully"` | Performs in-place text replacement and confirms modification. |
| **FILE-05** | `Create a CSV spreadsheet /sdcard/Download/groceries.csv with headers Item,Qty,Price and 3 sample rows` | Creates valid CSV data with header parsing. |
| **FILE-06** | `Edit spreadsheet /sdcard/Download/groceries.csv by setting row 1 column Price to 4.99` | Modifies cell value without corrupting CSV structure. |

---

### Suite D: Universal Semantic Device Search (Spotlight)

| Test ID | Input Prompt | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **SRC-01** | `Find Calculator app` | Resolves `com.google.android.calculator` or manufacturer calculator package. |
| **SRC-02** | `Find the file groceries.csv` | Locates `/sdcard/Download/groceries.csv` using file index matcher. |
| **SRC-03** | `Search for torch setting` | Matches synonym "torch" $\rightarrow$ "Toggle Flashlight" action. |

---

### Suite E: Autonomous ReAct UI Automation & Obstacle Clearance

| Test ID | Input Prompt | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **REACT-01** | `Open Calculator, calculate 250 plus 175, and tell me the result` | 1. Launches Calculator app.<br>2. Inspects accessibility DOM tree.<br>3. Taps `2`, `5`, `0`, `+`, `1`, `7`, `5`, `=`.<br>4. Reads result node (`425`) and speaks/chats answer. |
| **REACT-02** | `Open Clock app and switch to the Timer tab` | 1. Launches Clock.<br>2. Finds "Timer" tab using semantic synonym matching.<br>3. Taps Timer tab and validates state change via `DeltaDomEngine`. |
| **REACT-03** | `Open YouTube and search for NASA live stream` | 1. Launches YouTube.<br>2. Clears any initial promo/sign-in popups via `ObstacleClearanceEngine`.<br>3. Taps Search icon.<br>4. Types query and submits. |

---

### Suite F: Anti-Loop & Cyclic Deadlock Protection

| Test ID | Scenario | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **LOOP-01** | Trigger a repetitive tap on an unresponsive non-clickable view | `AntiLoopDetector` logs warning after 3 attempts and triggers automatic `PRESS_BACK` recovery instead of hanging indefinitely. |
| **LOOP-02** | Oscillate between two screens ($A \rightarrow B \rightarrow A \rightarrow B$) | `AntiLoopDetector` detects 2-step cycle and applies alternative navigation or escalates gracefully. |

---

### Suite G: Privacy Vault & Sensitive Screen Masking

| Test ID | Scenario | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **SEC-01** | Open Lock Screen / Password Entry screen | `SecurityVaultEngine` flags `PASSWORD` / `PIN` field and suppresses/masks bounding box in exported screenshots. |
| **SEC-02** | Open Payment / Card Checkout form | Detects CVV / Card Number nodes and requests user biometric confirmation before proceeding. |

---

### Suite H: Smart Autofill & Form Automation

| Test ID | Input Prompt | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **AUTO-01** | `Autofill contact form with Name: Jane Doe, Email: jane@example.com, Phone: 555-0199` | Classifies editable fields on screen (Name, Email, Phone) and fills them in one batch without hardcoded element IDs. |

---

### Suite I: Live Voice & Visual HUD Narration

| Test ID | Scenario | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **VOICE-01** | Execute a multi-step task with voice enabled | `LiveStatusNarrator` speaks short, human-friendly cues (*"Checking screen state"*, *"Action verified"*, *"Dismissing popup"*) throttled at 1.2s intervals. |

---

### Suite J: Transactional Rollback & Checkpoint Recovery

| Test ID | Scenario | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **ROLLBACK-01** | Agent begins multi-step task, edits a local file, and gets aborted mid-way | `TransactionalRollbackHarness` restores original file backup and navigates back to initial application state. |

---

### Suite K: Mobile Accessibility & Usability Linter

| Test ID | Input Prompt | Expected Behavior on Live Device |
| :--- | :--- | :--- |
| **AUDIT-01** | `Audit current screen accessibility` | Executes `AccessibilityAuditorEngine`, scans active screen for touch targets (<48dp) and missing labels, and outputs health score (0-100) with recommendations. |

---

## 3. Live Testing Execution Checklist

- [ ] **1. Pre-Flight & Health:** Verify `/doctor`, `/status`, and `/smoke` return healthy green.
- [ ] **2. Quick Actuation:** Verify Flashlight ON/OFF, Wi-Fi, and Settings 1-hop shortcuts.
- [ ] **3. Document & Files:** Verify `.txt` and `.csv` creation, reading, and in-place search/edit.
- [ ] **4. Semantic Search:** Verify Spotlight app, setting, and file search.
- [ ] **5. Autonomous UI ReAct:** Verify Calculator calculation and Clock tab switching.
- [ ] **6. Deadlock & Anti-Loop:** Confirm 3-retry warning and cyclic backtracking.
- [ ] **7. Privacy Masking:** Confirm password and PIN bounding boxes are masked.
- [ ] **8. Smart Autofill:** Verify multi-field form classification and batch autofill.
- [ ] **9. Voice Narration:** Confirm live speech cues during multi-step execution.
- [ ] **10. Transactional Rollback:** Confirm state and file backup restoration.
- [ ] **11. Accessibility Linter:** Confirm screen audit score and touch-target checks.

---

## 4. Test Reporting Template

When documenting test results, record the outcome in the following format:

```markdown
### Live Device Test Report
- **Device Model:** [e.g. Pixel 8 / Samsung S24 / OnePlus 12]
- **Android Version:** [e.g. Android 14 / 15 / 16]
- **App Build:** Debug (Latest Main Commit)
- **Total Tests Executed:** [Number]
- **Passed:** [Number]
- **Failed / Blocked:** [Number]
- **Notes / Observations:** [Any device-specific UI quirks observed]
```
