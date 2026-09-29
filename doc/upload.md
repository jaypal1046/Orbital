Browser task: "Inspect Play Console Dashboard Requirements"

Here is the complete, organized breakdown of **all details and forms** required on Google Play Console for **Orbital**, with ready-to-use answers you can copy & paste:

---

### 📋 **1. App Content & Policy Declarations**
*(Found in the left sidebar under: **Policy & programs** ➔ **App content** or on the **Dashboard** checklist)*

| Task / Section | What to Select / Enter |
| :--- | :--- |
| **Privacy Policy** | URL: `https://orbital-lime-three.vercel.app/privacy` |
| **App Access** | Select: **"All functionality is available without special access"** (or "All features are available without credentials"). |
| **Ads** | Select: **"No, my app does not contain ads"**. |
| **Content Rating** | • Enter your email address.<br>• Category: **Utility, Productivity, Communication, or Other**.<br>• Answer **"No"** to all questions (violence, sexuality, offensive language, illegal substance promotion, etc.).<br>• Summary rating will be **Everyone / 3+ / PEGI 3**. |
| **Target Audience & Content** | • Target age: Check **18 and over** (and 13+ if desired; selecting 18+ avoids COPPA children's policy restrictions).<br>• Appeal to children: **"No"**. |
| **News Apps** | Select: **"No"** (Orbital is not a news app). |
| **COVID-19 Contact Tracing** | Select: **"My app is not a publicly available COVID-19 contact tracing or status app"**. |
| **Data Safety** | • Does your app collect or share data? Select: **Yes** (if diagnostic / voice), or **No** (if zero remote data storage).<br>• Data encryption: **Yes, data is encrypted in transit**.<br>• Account deletion: **No accounts required**.<br>• Data types: If asked about audio, mark **Voice recordings (ephemeral processing for voice chat, not shared/not stored permanently)**. |
| **Government Apps** | Select: **"No"** (Not a government entity). |
| **Financial Features** | Select: **"My app does not provide any financial features"**. |

---

### 🎨 **2. Main Store Listing (Graphics & Descriptions)**
*(Found under: **Grow users** ➔ **Store presence** ➔ **Main store listing**)*

#### **Text Metadata:**
* **App Name:** `Orbital: AI Companion & Agent`
* **Short Description (max 80 chars):**  
  `Autonomous AI companion with floating overlay HUD and intelligent workflow tools.`
* **Full Description:**
  ```markdown
  Meet Orbital — your autonomous AI companion and workflow assistant designed to make everyday mobile interactions seamless and intelligent.

  KEY FEATURES:
  • Interactive AI Personas: Engage with dynamic companions tailored for productivity, creative writing, research, and deep conversations.
  • Floating Mascot HUD: Access quick AI assistance, summaries, and smart shortcuts right over your active workflow whenever you need it.
  • Multi-Provider AI Support: Connect top-tier models and your own custom API keys directly with full privacy and zero vendor lock-in.
  • Power-Aware Intelligence: Automatically optimizes processing based on battery level, thermal state, and network connectivity.
  • Privacy First: All local data stays strictly on your device. No unwanted tracking, telemetry, or hidden data collection.

  Experience the next generation of mobile AI assistance with Orbital.
  ```

#### **Visual Assets to Upload:**
*(All files are generated, sized, and ready in [`c:/Jay/dev/Orbital/doc/playstore_assets/`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/))*
1. **App Icon (512x512):** Upload [`app_icon_512x512.jpg`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/app_icon_512x512.jpg)
2. **Feature Graphic (1024x500):** Upload [`feature_graphic_1024x500.jpg`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/feature_graphic_1024x500.jpg)
3. **Phone Screenshots (minimum 2, up to 8):** Upload the screenshots from [`c:/Jay/dev/Orbital/doc/playstore_assets/`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/):
   * `screenshot_1_home_chat.png`
   * `screenshot_2_ai_companions.png`
   * `screenshot_3_action_modes.png`
   * `screenshot_4_action_confirmation.png`
   * `screenshot_5_floating_hud.png`
   * `screenshot_6_drawer_tools.png`

---

### 📦 **3. App Release (Production or Internal Testing)**
*(Found under: **Release** ➔ **Production** or **Internal testing** ➔ **Create new release**)*

* **App Bundle (.aab):**  
  Upload file:  
  `c:\Jay\dev\Orbital\app\build\outputs\bundle\release\app-release.aab`
* **Release Name:** `1.0.0 (1)`
* **Release Notes:**
  ```
  Initial public release of Orbital:
  - Dynamic AI chat with multi-model support
  - Floating Mascot companion HUD
  - Power-aware background optimization
  - Local privacy-first architecture
  ```