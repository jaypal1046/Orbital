# Orbital - Floating AI Companion

An always-on floating AI companion for Android that lives on your screen and you can talk to anytime.

## Features

- 🎯 **Always Available**: Tap the floating character to chat anytime, no app switching needed
- 🎤 **Voice + Text**: Natural voice input (Whisper STT via Groq/OpenAI + Android SpeechRecognizer fallback) and text-to-speech output
- 🔑 **Bring Your Own API**: Supports 24+ LLM providers (Groq, Gemini, Cerebras, OpenRouter, NVIDIA NIM, GitHub Models, Mistral, Zhipu, HuggingFace, Cloudflare, Cohere, Ollama, Pollinations, Kilo, OVH, LLM7, Agnes, Routeway, Sail, AMD Radeon, ModelScope, AI Horde, Custom)
- 🎭 **Multiple Characters**: Choose from different personalities (Aether, Lumy, Volo) with custom sprites
- 🔒 **Safety First**: Hard payment app protection - never interferes with financial transactions (Google Pay, PhonePe, Paytm, BHIM, banking apps, UPI apps)
- 🛡️ **Tamper Detection**: Local integrity checks (debuggable flag, test-only flag, signature verification, installer check)
- 🎨 **Smooth Animations**: Floating/bouncing animations with state-driven sprite transitions (idle, jump, thinking, working, celebrating, happy, curious, sleep, sad, etc.)
- ⚡ **Device Actions**: Open apps, search web/YouTube/Spotify/Gmail, navigate, set timers, compose emails, send WhatsApp/SMS, check device status, open settings
- 🤖 **Automation**: WorkManager-powered background tasks (daily summaries when charging at night, voice memo transcription, memory cleanup)
- 🔄 **Multi-Provider Routing**: Auto-failover across providers with cooldowns, fast/frontier/pinned routing modes
- 🌐 **Embedded API Server**: Optional Ktor HTTP server on port 3001 exposing OpenAI-compatible `/v1/chat/completions`

## Safety Promise

**Orbital will never interfere with financial/payment applications.**

- Maintains a strict hardcoded blacklist of payment apps
- When a payment app comes to foreground, Orbital instantly freezes and hides
- No user override possible - this is non-negotiable
- Local tamper detection with configurable response

## Requirements

- Android 6.0+ (API 23+)
- Overlay permission (`SYSTEM_ALERT_WINDOW`)
- Usage stats permission (for safety monitoring)
- Internet connection for AI API calls
- Microphone permission for voice input

## Getting Started

### 1. Build the App

```bash
./gradlew assembleDebug
```

### 2. Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Grant Permissions

1. **Overlay Permission**: 
   - Open Orbital app
   - Go to Settings > Apps > Orbital > Special app access > Draw over other apps
   - Enable permission

2. **Usage Stats Permission**:
   - Open Orbital app
   - Go to Settings > Apps > Orbital > Special app access > Usage access
   - Enable permission

3. **Microphone Permission**:
   - Open Orbital app
   - Grant when prompted or in App Permissions

### 4. Configure API Key

1. Open Orbital app
2. Go to Settings > Keys & Providers
3. Select your provider
4. Enter your API key
5. Save

### 5. Start Companion

1. Return to main screen
2. Tap "Start Companion" button (play icon in top bar)
3. The floating bubble will appear on your screen

## API Providers Supported

| Provider | Models | Free Tier | Notes |
|----------|--------|-----------|-------|
| **Groq** | Llama 3.3 70B, Llama 4 Scout, DeepSeek R1, Qwen 2.5 Coder | 30 RPM, 14.4k RPD | Ultra-fast inference |
| **Gemini** | Gemini 3.6 Flash/Pro, 2.0 Flash, Gemma 2 | 15 RPM, 1.5k RPD, 1M TPM | 1M+ context window |
| **Cerebras** | Llama 3.1/3.3 70B, Qwen 3 Coder 480B | 30 RPM, 1.8k+ tok/s | Ultra-fast hardware |
| **OpenRouter** | DeepSeek V3.1, Kimi K2, Qwen3 Coder, GLM 4.5, Llama 3.3 | Free tier available | Multi-model aggregation |
| **NVIDIA NIM** | Llama 3.3 70B, DeepSeek R1, Nemotron 4 340B | 1000 free credits | GPU accelerated |
| **GitHub Models** | GPT-5, GPT-4o, o1/o3-mini, Llama 3.1 70B | Free with PAT | Azure-hosted |
| **And 17 more...** | | | Keyless: Kilo, OVH, Pollinations, AI Horde |

## Character Personalities

- **Aether**: Clean, minimal floating spirit (cosmic traveler theme)
- **Lumy**: Short, cute, friendly companion (gentle light spirit theme)  
- **Volo**: Unique and brandable personality (swift wind rider theme)

## Architecture

The app is built with:
- **Kotlin Native**: For deep system control and performance
- **Jetpack Compose**: For modern in-app UI
- **XML + View System**: For overlay (WindowManager)
- **OkHttp**: For streaming API requests
- **EncryptedSharedPreferences**: For secure API key storage (Android Keystore-backed)
- **WorkManager**: For power-aware background automation
- **Kotlinx Serialization**: For JSON handling
- **Ktor**: For embedded HTTP server (optional)

### Project Structure

```
app/
├── src/main/java/com/orbital/
│   ├── data/           # LLM clients, provider registry, encrypted storage
│   ├── overlay/        # Floating service (WindowManager), connection status
│   ├── safety/         # Payment protection, tamper detection
│   ├── ui/             # Compose screens + XML overlay layout
│   ├── action/         # Action parsing, device executor, app capabilities, next-step suggestions
│   ├── voice/          # Whisper STT (Groq/OpenAI) + Android SpeechRecognizer, TTS
│   ├── power/          # WorkManager automation scheduler
│   └── MainApplication.kt
└── res/
```

## Development

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install debug build
./gradlew installDebug

# Run unit tests
./gradlew test
```

### Testing

1. Build verification: `./gradlew assembleDebug`
2. Unit tests: `./gradlew test` (46 tests: ActionParser, NextStepSuggester, ProviderRegistry/LlmRepository)
3. Overlay functionality: Install and test floating bubble
4. Safety test: Launch payment app, verify bubble disappears
5. Chat test: Enter API key, test streaming responses
6. Voice test: Test voice input (Whisper STT) and TTS

## Privacy & Security

- API keys stored encrypted using Android Keystore (EncryptedSharedPreferences)
- No telemetry or analytics
- Open source - code can be audited
- Payment protection is hardcoded and non-overridable
- Local tamper detection (debuggable/test-only flags, signature check, installer verification)

## Distribution

- **GitHub**: Open source releases with source code
- **Play Store**: Will be submitted with full safety documentation

## License

MIT License - see [LICENSE](LICENSE) file for details

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly (`./gradlew test assembleDebug`)
5. Submit a pull request

## Troubleshooting

### Overlay not showing
- Ensure overlay permission is granted
- Check if other overlay apps are conflicting
- Try restarting the device

### Payment app not detected
- Ensure usage stats permission is granted
- Check if the payment app is in the blacklist
- Test with a known payment app (Google Pay)

### Voice not working
- Check microphone permission
- Ensure device supports speech recognition
- For Whisper STT: configure Groq or OpenAI API key
- Fallback: Android SpeechRecognizer (no API key needed)

### Tests failing
- Run `./gradlew clean test` to clear caches
- Tests require Robolectric for Android framework classes (JSONObject, etc.)

## Support

- GitHub Issues: Report bugs and request features
- Documentation: See the Wiki for detailed guides

---

Built with ❤️ for better AI accessibility on Android