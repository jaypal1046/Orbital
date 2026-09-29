Viewed build.gradle.kts:1-100
Viewed AndroidManifest.xml:1-97

Here is the complete, step-by-step guide to prepare, sign, bundle, and publish **Orbital** on the **Google Play Store**.

---

### Step 1: Generate your Production Keystore (`.jks`)

Google Play requires all release builds to be cryptographically signed with a private key.

Run this command in your PowerShell terminal to generate your release keystore:

```powershell
keytool -genkey -v -keystore orbital-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias orbital-key
```
> [!IMPORTANT]
> **Store this keystore file and your password securely.** If you lose your keystore file or password, you will not be able to push updates to your app.

---

### Step 2: Configure Release Signing & ProGuard

In [app/build.gradle.kts](file:///c:/Jay/dev/Orbital/app/build.gradle.kts), configure the release signing configuration so Gradle signs your app automatically:

```kotlin
android {
    ...
    signingConfigs {
        create("release") {
            storeFile = file("orbital-release-key.jks") // Or path to your keystore
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "your-keystore-password"
            keyAlias = "orbital-key"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "your-key-password"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

---

### Step 3: Build the Android App Bundle (`.aab`)

Google Play accepts **`.aab` (Android App Bundle)** files rather than `.apk`.

Run the bundle command:
```powershell
.\gradlew.bat bundleRelease
```
The output file will be generated at:
`app/build/outputs/bundle/release/app-release.aab`

---

### Step 4: Stored Store Graphics & Assets (`doc/playstore_assets/`)

All required assets have been created and saved in [doc/playstore_assets/](file:///c:/Jay/dev/Orbital/doc/playstore_assets/):

| Asset | Saved File Path | Dimensions |
| :--- | :--- | :--- |
| **App Icon (Primary - Planet)** | [`doc/playstore_assets/app_icon_512x512.jpg`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/app_icon_512x512.jpg) | **512 x 512 px** |
| **App Icon (Alternative - Geometric O)** | [`doc/playstore_assets/app_icon_alternative_512x512.jpg`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/app_icon_alternative_512x512.jpg) | **512 x 512 px** |
| **Feature Graphic (Banner)** | [`doc/playstore_assets/feature_graphic_1024x500.jpg`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/feature_graphic_1024x500.jpg) | **1024 x 500 px** |
| **Screenshot 1 (Home & Chat)** | [`doc/playstore_assets/screenshot_1_home_chat.png`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/screenshot_1_home_chat.png) | **Phone (1080x2400)** |
| **Screenshot 2 (AI Companions)** | [`doc/playstore_assets/screenshot_2_ai_companions.png`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/screenshot_2_ai_companions.png) | **Phone (1080x2400)** |
| **Screenshot 3 (Action Approval Modes)**| [`doc/playstore_assets/screenshot_3_action_modes.png`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/screenshot_3_action_modes.png) | **Phone (1080x2400)** |
| **Screenshot 4 (Confirmation Card)** | [`doc/playstore_assets/screenshot_4_action_confirmation.png`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/screenshot_4_action_confirmation.png) | **Phone (1080x2400)** |
| **Screenshot 5 (Floating HUD Overlay)** | [`doc/playstore_assets/screenshot_5_floating_hud.png`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/screenshot_5_floating_hud.png) | **Phone (1080x2400)** |
| **Screenshot 6 (Navigation & Tools)** | [`doc/playstore_assets/screenshot_6_drawer_tools.png`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/screenshot_6_drawer_tools.png) | **Phone (1080x2400)** |
| **Screenshot 7 (Live Screen Reader)** | [`doc/playstore_assets/screenshot_7_live_screen_inspection.png`](file:///c:/Jay/dev/Orbital/doc/playstore_assets/screenshot_7_live_screen_inspection.png) | **Phone (1080x2400)** |

---

### Step 5: Legal & Policy Declarations (Zero Accessibility Policy Friction)

1. **Privacy Policy URL (Mandatory for Play Console)**:
   * **Live Hosted URL**: `https://jaypal1046.github.io/orbital_policy/`
   * **GitHub Repo**: `https://github.com/jaypal1046/orbital_policy`
   * **Local Sources**: [`doc/privacy/index.html`](file:///c:/Jay/dev/Orbital/doc/privacy/index.html)
2. **Foreground Service & Overlay Declaration**:
   * **Special Use / Floating Companion**: Orbital runs an interactive floating mascot HUD companion when requested by the user.
3. **Data Safety Form**:
   * **Audio / Voice**: Collected when using voice chat (ephemeral, not shared).
   * **App Performance / Crash Logs**: Basic diagnostics.

---

### Step 6: Google Play Console Release Flow

1. **Create App**:
   * Go to [Google Play Console](https://play.google.com/console).
   * Click **Create App** → Name: `Orbital` → Free / Paid → Accept Declarations.
2. **Store Presence**:
   * Fill in **Short description** (up to 80 chars) & **Full description** (up to 4000 chars).
   * Upload `app_icon_512x512.jpg`, `feature_graphic_1024x500.jpg`, and the screenshots from `doc/playstore_assets/`.
3. **App Content & Declarations**:
   * Complete **Privacy Policy**, **Ads Declaration** (No ads), **App Access**, **Content Rating Questionnaire**, **Target Audience** (13+), and **Data Safety**.
4. **Create Release**:
   * Go to **Testing** → **Internal Testing** (or **Closed Testing**).
   * Click **Create new release** → Upload [`app/build/outputs/bundle/release/app-release.aab`](file:///c:/Jay/dev/Orbital/app/build/outputs/bundle/release/app-release.aab).
   * Add release notes and click **Review & Rollout**.