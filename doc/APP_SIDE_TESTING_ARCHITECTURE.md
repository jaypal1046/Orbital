# 🛰️ Orbital Android App-Side Deep Testing Architecture

**Architecture Scope:** Native Android (`com.orbital.*`)  
**Frameworks:** JUnit 5 / JUnit 4, Kotlinx Coroutines Test, Turbine, MockK, Robolectric, Compose Testing Rule  
**Target Coverage Goal:** 90%+ Branch & Line Coverage across all Core Engines  

---

## 🏛️ 1. Domain-Driven Test Package Structure

To ensure rigorous quality and maintainability, the test architecture strictly mirrors the production domain packages in `app/src/main/java/com/orbital/`:

```
app/src/
├── main/java/com/orbital/            # Production Code (19 Domain Modules)
└── test/java/com/orbital/            # Fast JVM Unit & Integration Tests
    ├── action/                       # Action execution, parsing, heuristic fallbacks
    │   ├── ActionParserTest.kt
    │   ├── DeviceActionExecutorTest.kt
    │   └── HeuristicMatcherTest.kt
    ├── automation/                   # Routine scheduling & background automation
    │   ├── AutomationEngineTest.kt
    │   └── PowerAwareSchedulerTest.kt
    ├── bridge/                       # MCP client, crypto auth & WebSocket routing
    │   ├── OrbitalBridgeClientTest.kt
    │   ├── OrbitalCryptoAuthTest.kt
    │   └── BridgeActionDispatcherTest.kt
    ├── chat/                         # Chat state, streaming markdown, telemetry cards
    │   ├── ChatViewModelTest.kt
    │   ├── StreamingParserTest.kt
    │   └── TimelineMetricsTest.kt
    ├── cron/                         # Background WorkManager cron triggers
    │   └── PeriodicTaskWorkerTest.kt
    ├── data/                         # Repositories, Room DB, Encrypted Keystore
    │   ├── SecureStorageTest.kt
    │   └── ConversationRepositoryTest.kt
    ├── decision/                     # Auto-Router, speed-tiering, fallback chains
    │   ├── ProviderRouterTest.kt
    │   ├── FallbackPolicyTest.kt
    │   └── CooldownEngineTest.kt
    ├── foreman/                      # Multi-Agent orchestrator & step planning
    │   ├── AgentSupervisorTest.kt
    │   └── ExecutionPlanValidatorTest.kt
    ├── media/                        # Screen capture & audio encoding
    │   └── ScreenBitmapRecyclerTest.kt
    ├── memory/                       # Episodic memory & conversation context
    │   ├── ContextWindowTrimmerTest.kt
    │   └── SemanticMemoryStoreTest.kt
    ├── overlay/                      # Mascot physics, emotion engine & state machines
    │   ├── MascotViewModelTest.kt
    │   ├── EmotionEngineTest.kt
    │   └── OverlayPhysicsEngineTest.kt
    ├── power/                        # Battery, thermal & charging listeners
    │   └── BatteryThermalMonitorTest.kt
    ├── safety/                       # Gatekeeper, banking shield & sensitive masks
    │   ├── PaymentAppShieldTest.kt
    │   ├── SensitiveDataMaskerTest.kt
    │   └── ActionRiskEvaluatorTest.kt
    ├── skills/                       # Dynamic mobile skills & prompt injection
    │   ├── MobileSkillsManagerTest.kt
    │   └── DynamicPromptInjectorTest.kt
    ├── ui/                           # Compose UI unit semantics & view models
    │   ├── InAppChatUiStateTest.kt
    │   └── SideNavUiStateTest.kt
    ├── updater/                      # In-app hot-patching & release verifier
    │   └── GitHubReleaseParserTest.kt
    └── voice/                        # Voice pitch synthesis & mascot audio
        └── VoicePitchModifierTest.kt
```

---

## 🔬 2. Detailed Package-by-Package Test Specifications

---

### 📦 1. `com.orbital.action` (Action Dispatch & Heuristics)

#### Key Responsibilities
- Parses natural language inputs into actionable commands (e.g. `DEVICE_STATUS`, `OPEN_APP`, `SWIPE`, `TAP`).
- Dispatches hardware calls (Battery, Temperature, Torch, Volume, Storage) without root access.
- Executes accessibility node interactions.

#### Core Test Classes & Scenarios

| Test Class | Test Target | Test Scenarios & Edge Cases | Mocking / Tools |
| :--- | :--- | :--- | :--- |
| **`ActionParserTest`** | `ActionParser.kt` | • **Typo Resilience:** `"battry status"`, `"chekc storage"`, `"toche on"`.<br>• **Regex Fallbacks:** Zero-LLM instant matching for 15+ native intents in <1ms.<br>• **Param Extraction:** `"set timer for 25m"`, `"open youtube and search lofi beats"`. | Pure JVM / JUnit 5 |
| **`DeviceActionExecutorTest`** | `DeviceActionExecutor.kt` | • **Battery & Thermal:** Verifies `BatteryManager` broadcast intent decoding.<br>• **Storage Calculation:** Validates `StatFs` available & total byte calculation.<br>• **Torch / Camera:** Verifies `CameraManager.setTorchMode` exceptions (Torch unavailable). | MockK (`Context`, `BatteryManager`, `CameraManager`) |
| **`AccessibilityDispatcherTest`** | `AccessibilityActionDispatcher.kt` | • **Node Lookup:** Resolves nodes by ID, text, and Content-Description.<br>• **Action Injection:** Simulates `ACTION_CLICK`, `ACTION_SET_TEXT`, and `ACTION_SCROLL_FORWARD`. | MockK (`AccessibilityNodeInfo`) |

---

### 📦 2. `com.orbital.bridge` (MCP Wireless AI Bridge & Security)

#### Key Responsibilities
- 256-bit ECDSA token generation, HMAC-SHA256 signature verification, anti-replay validation.
- Bidirectional WebSocket communication with laptop IDEs (Google Antigravity, Claude Desktop, Cursor).
- Dual-mode routing (`/inspect`, `/action` on port 8766).

#### Core Test Classes & Scenarios

| Test Class | Test Target | Test Scenarios & Edge Cases | Mocking / Tools |
| :--- | :--- | :--- | :--- |
| **`OrbitalCryptoAuthTest`** | `OrbitalCryptoAuth.kt` | • **Token Verification:** Validates matching HMAC-SHA256 signature with shared secret.<br>• **Anti-Replay Window:** Rejects timestamps older than 30 seconds.<br>• **Tamper Defense:** Modifying 1 byte in payload invalidates token signature. | Pure JVM / Android Crypto |
| **`OrbitalBridgeClientTest`** | `OrbitalBridgeClient.kt` | • **Socket Lifecycle:** Connection open, heartbeat ping/pong, disconnect handling.<br>• **Exponential Backoff:** Retries reconnect after 1s, 2s, 4s, up to 15s.<br>• **Message Serialization:** Serializes node hierarchy into compact JSON. | MockWebServer / Coroutine TestScope |
| **`BridgeActionDispatcherTest`** | `BridgeActionDispatcher.kt` | • **Tool Routing:** Routes `OPEN_APP`, `PRESS_KEY`, `SWIPE`, `TAP_ELEMENT`, `CUSTOM_PROMPT`.<br>• **Error Handling:** Returns structured error JSON on unknown tool or missing permissions. | Coroutines Test + Turbine |

---

### 📦 3. `com.orbital.chat` (Conversational State & Telemetry)

#### Key Responsibilities
- Manages `ChatViewModel` state, message streams, markdown thought blocks, and step timelines.
- Synchronizes mascot emotion states with conversational lifecycle.

#### Core Test Classes & Scenarios

| Test Class | Test Target | Test Scenarios & Edge Cases | Mocking / Tools |
| :--- | :--- | :--- | :--- |
| **`ChatViewModelTest`** | `ChatViewModel.kt` | • **StateFlow Emission:** User message -> `ThinkingState` -> `StreamingState` -> `CompletedState`.<br>• **Step Timeline:** Records microsecond execution duration and step count.<br>• **Error Recovery:** Displays retry prompt on provider network failure. | Turbine + TestDispatcher |
| **`StreamingParserTest`** | `StreamingParser.kt` | • **Thought Block Extraction:** Extracts `<thought>...</thought>` tags cleanly.<br>• **Code Block Parsing:** Identifies fenced code blocks and formatting tokens in real-time. | Pure JVM |

---

### 📦 4. `com.orbital.decision` (Provider Auto-Router & Speed Tiers)

#### Key Responsibilities
- Intelligently routes queries across 24+ AI providers (Gemini, Groq, OpenAI, Claude, DeepSeek, Local).
- Manages provider health, cooldown timers, and speed-tier fallbacks.

#### Core Test Classes & Scenarios

| Test Class | Test Target | Test Scenarios & Edge Cases | Mocking / Tools |
| :--- | :--- | :--- | :--- |
| **`ProviderRouterTest`** | `ProviderRouter.kt` | • **Tier 1 Routing:** Routes fast queries to Groq / Gemini 2.5 Flash.<br>• **Failover Chain:** Automatically falls back from Groq to Gemini on HTTP 429 in <150ms.<br>• **Offline Fallback:** Selects Edge/Local model when network is disconnected. | MockK (Provider Clients) |
| **`CooldownEngineTest`** | `CooldownEngine.kt` | • **Rate Limit Cooldown:** Marks provider inactive for 60s upon receiving 429 Too Many Requests.<br>• **Auto-Reactivation:** Automatically re-enables provider once cooldown timer expires. | TestCoroutineScheduler |

---

### 📦 5. `com.orbital.safety` (Financial Shield & Gatekeeper)

#### Key Responsibilities
- Real-time detection of financial/payment apps and instant companion freezing.
- Risk level evaluation and human approval dialog interception.

#### Core Test Classes & Scenarios

| Test Class | Test Target | Test Scenarios & Edge Cases | Mocking / Tools |
| :--- | :--- | :--- | :--- |
| **`PaymentAppShieldTest`** | `PaymentAppShield.kt` | • **Package Detection:** Blocks automation on Google Pay (`com.google.android.apps.nbu.paisa.user`), Paytm (`net.one97.paytm`), PhonePe (`com.phonepe.app`), Banking apps.<br>• **Screen Masking:** Blanks companion overlay within 5ms of package change. | Pure JVM / Package List |
| **`SensitiveDataMaskerTest`** | `SensitiveDataMasker.kt` | • **Card Masking:** Regex masks 16-digit credit card numbers (`****-****-****-1234`).<br>• **OTP Masking:** Masks 4-6 digit numeric codes in screen dumps. | Pure JVM |
| **`ActionRiskEvaluatorTest`** | `ActionRiskEvaluator.kt` | • **Risk Classification:** Evaluates `SEND_SMS`, `MAKE_CALL`, `DELETE_FILE` as `RISK_HIGH`.<br>• **Mode Enforcement:** Rejects high-risk actions under `Smart Safe` without user approval. | Pure JVM |

---

### 📦 6. `com.orbital.overlay` (Floating Mascot Physics & Persona Engine)

#### Key Responsibilities
- 5 AI Companions: Aether (Cosmic), Lumy (Light), Nexus (Cybernetic), Spark (Lightning), Volo (Sky).
- WindowManager floating physics, drag-to-snap edge calculation, and emotion state machine.

#### Core Test Classes & Scenarios

| Test Class | Test Target | Test Scenarios & Edge Cases | Mocking / Tools |
| :--- | :--- | :--- | :--- |
| **`MascotViewModelTest`** | `MascotViewModel.kt` | • **Persona Switch:** Switches active companion and updates aura shader instantly.<br>• **Emotion State Machine:** Transitions: `Idle` -> `Thinking` -> `Executing` -> `Success` / `Failed`. | Turbine |
| **`OverlayPhysicsEngineTest`** | `OverlayPhysicsEngine.kt` | • **Edge Snapping:** Calculates nearest horizontal screen boundary (left vs. right).<br>• **Screen Rotation:** Recalculates X/Y bounds when screen rotates between Portrait and Landscape. | Robolectric / MockK |

---

### 📦 7. `com.orbital.skills` (Mobile Skills Hub & Dynamic Prompting)

#### Key Responsibilities
- Modular skill activation and dynamic injection into active LLM session prompts.

#### Core Test Classes & Scenarios

| Test Class | Test Target | Test Scenarios & Edge Cases | Mocking / Tools |
| :--- | :--- | :--- | :--- |
| **`MobileSkillsManagerTest`** | `MobileSkillsManager.kt` | • **Skill Toggling:** Enables/disables skills (`Android Device Automator`, `Live Vision Reader`).<br>• **Persistence:** Verifies active skill preferences survive app process restart. | SecureStorage Mock |
| **`DynamicPromptInjectorTest`** | `DynamicPromptInjector.kt` | • **Prompt Composition:** Assembles system prompt with active skill signatures.<br>• **Token Budgeting:** Verifies injected prompt stays within target context window. | Pure JVM |

---

## ⚡ 3. Unified Test Execution & CI Automation Matrix

| Command | Layer | Purpose | Target Execution Time |
| :--- | :--- | :--- | :--- |
| `./gradlew testDebugUnitTest` | **Tier 1: JVM Unit Tests** | Runs all 19 domain package unit tests | **< 15 seconds** |
| `./gradlew connectedDebugAndroidTest` | **Tier 2: UI Instrumented Tests** | Runs Compose UI & Accessibility tests on device | **~ 2 minutes** |
| `node tests/mcp/orbital_bridge_test_suite.js` | **Tier 3: E2E Bridge Harness** | Tests all 10 MCP tools and records latency | **~ 600ms** |

---

## 🚀 4. Next Implementation Step

We can now systematically implement the unit test suites for any specific package (e.g. `safety/PaymentAppShieldTest`, `action/ActionParserTest`, `decision/ProviderRouterTest`) to achieve 100% automated coverage across the entire Orbital codebase.
