What it is

Single-module Android app (com.orbital, minSdk 23, targetSdk 35, AGP 8.6, Kotlin 1.9.23). Floating AI companion: always-on overlay bubble + in-app chat, multi-provider LLM router (24 providers, auto-failover, cooldowns), embedded Ktor HTTP server on :3001, Whisper STT (Groq/OpenAI) + Android SpeechRecognizer fallback, TTS, device action executor (open apps, search, navigate, timers, calls, SMS), WorkManager automation, encrypted keystore-backed API key storage, payment-app safety blacklist.

Architecture (real, not planned)

data/        LlmRepository, ProviderRegistry/Config, SecureStorage (EncryptedSP)
overlay/     OverlayService (foreground, TYPE_APPLICATION_OVERLAY), ConnectionStatus
ui/          Compose (InAppChatScreen, MainActivity) + XML overlay_bubble.xml; MascotState/EventBus/AnimatedMascotView
action/      ActionParser, DeviceActionExecutor, AppCapabilityManager, NextStepSuggester
voice/       VoiceManager, WhisperTranscriber
power/       PowerAwareScheduler (WorkManager) — workers are stubs
safety/      SafetyManager — isTampered() returns false (stub)

What's broken / missing

- No tests at all. Zero src/test, zero src/androidTest. Movement engine, state machine, parser, router — none verified.
- SafetyManager.isTampered() is a stub returning false. README advertises tamper detection; it doesn't exist. Same for all three PowerAwareScheduler workers — return Result.success() with no implementation.
- Ktor server is dead code. ServerConfig.enableEmbeddedServer = false by default; startEmbeddedServer() never runs. The /v1/chat/completions route, ChatRequest/ChatResponse models, and kotlinx-serialization plugin are unused.
- Lottie declared, never used. lottie-compose:6.4.1 in deps, no LottieAnimation anywhere. Same for play-integrity — SafetyManager never calls it.
- Two UI systems mixed. overlay_bubble.xml (XML, inflated in service) + Compose InAppChatScreen. MainActivity is Compose; overlay is XML. No shared rendering layer — the "shared engine" from the plan doc was never built. Character sprite switching is a giant when in MascotSpriteHelper, not a state machine.
- MainApplication empty — no DI, no initialization of scheduler/safety.
- README claims MIT license; no LICENSE file.
- gradle.properties / local.properties / .env.example present but .gitignore modified — check what's tracked.
- No signing config, no proguard-rules.pro file (build references proguard-rules.pro, missing).
- Hardcoded deps in app/build.gradle.kts while libs.versions.toml exists and is richer — version mismatch risk (e.g. AGP 8.5 in toml vs 8.6 in build).
- QUERY_ALL_PACKAGES requested with tools:ignore — flagged, needs justification.
- Overlay lifecycle gaps: no handling of onConfigurationChanged, permission revocation mid-run, or Android 15 background-start restrictions. stopOverlay() swallows exceptions and calls stopSelf() after unregistering receiver.
- Action execution debounce is fragile — finalizeResponse() runs on a 1.2s stream-pause timer in both overlay and in-app chat, but they're separate code paths with separate state. Race-prone.

What needs doing (priority order)

1. Delete dead code — Ktor server + routes, unused deps (lottie, play-integrity, ktor*, serialization if unused), unused ServerMode.EXTERNAL_SERVER. Cut ~600 lines.
2. Implement or remove the advertised features — SafetyManager.isTampered() (Play Integrity), the three WorkManager workers. Either build them or strike them from README.
3. Add tests — at minimum JUnit for ActionParser.parse, MovementController-equivalent (there is none yet), NextStepSuggester, provider routing. The plan doc demands it; code has none.
4. Unify rendering — pick XML or Compose for the overlay, or build the shared CharacterRenderer the plan specifies. Currently two parallel implementations of the same mascot logic.
5. Fix overlay lifecycle — config changes, permission revocation, Android 13+/15 foreground-service rules, foregroundServiceType="specialUse" needs a notification + justification.
6. Align versions — use libs.versions.toml in app/build.gradle.kts; pick one AGP version.
7. Add LICENSE or fix README.
8. CI + lint — none configured.

Want me to start with the dead-code sweep and the stub implementations, or the test scaffolding?