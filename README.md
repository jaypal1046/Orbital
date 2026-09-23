# Orbital - Floating AI Companion

An always-on floating AI companion for Android that lives on your screen and you can talk to anytime.

## Features

- 🎯 **Always Available**: Tap the floating character to chat anytime, no app switching needed
- 🎤 **Voice + Text**: Natural voice input and text-to-speech output
- 🔑 **Bring Your Own API**: Supports OpenAI-compatible providers (Groq, OpenRouter, Together, Fireworks, Gemini Free)
- 🎭 **Multiple Characters**: Choose from different personalities (Aether, Lumy, Volo)
- 🔒 **Safety First**: Hard payment app protection - never interferes with financial transactions
- 🛡️ **Tamper Detection**: Detects and responds to app modifications
- 🎨 **Smooth Animations**: Lightweight Lottie animations for character movements

## Safety Promise

**Orbital will never interfere with financial/payment applications.** 

- Maintains a strict hardcoded blacklist of payment apps (Google Pay, PhonePe, Paytm, BHIM, banking apps, UPI apps)
- When a payment app comes to foreground, Orbital instantly freezes and hides
- No user override possible - this is non-negotiable
- Tamper detection shows warnings and lets users configure response actions

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
2. Go to Settings > API Configuration
3. Select your provider
4. Enter your API key
5. Save

### 5. Start Companion

1. Return to main screen
2. Tap "Start Companion" button
3. The floating bubble will appear on your screen

## API Providers Supported

- **OpenAI**: Use with OpenAI-compatible endpoints
- **Groq**: Fast inference with Llama models
- **OpenRouter**: Mix of open source and proprietary models
- **Together**: Open source models
- **Fireworks**: High-performance inference
- **Gemini Free**: Google's free tier

## Character Personalities

- **Aether**: Clean, minimal floating spirit
- **Lumy**: Short, cute, friendly companion
- **Volo**: Unique and brandable personality

## Architecture

The app is built with:
- **Kotlin Native**: For deep system control and performance
- **Jetpack Compose**: For modern UI
- **OkHttp**: For streaming API requests
- **EncryptedSharedPreferences**: For secure API key storage
- **Lottie**: For lightweight animations

## Development

### Project Structure

```
app/
├── src/main/java/com/orbital/
│   ├── data/           # API clients, encrypted storage
│   ├── overlay/        # Floating service and view management
│   ├── safety/         # Payment protection and tamper detection
│   ├── ui/             # Compose screens
│   ├── voice/          # STT and TTS
│   └── MainApplication.kt
└── res/
```

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install debug build
./gradlew installDebug
```

### Testing

1. Build verification: `./gradlew assembleDebug`
2. Overlay functionality: Install and test floating bubble
3. Safety test: Launch payment app, verify bubble disappears
4. Chat test: Enter API key, test streaming responses
5. Voice test: Test voice input and TTS

## Privacy & Security

- API keys are stored encrypted using Android Keystore
- No telemetry or analytics
- Open source - code can be audited
- Payment protection is hardcoded and non-overridable
- Tamper detection with user-configurable response

## Distribution

- **GitHub**: Open source releases with source code
- **Play Store**: Will be submitted with full safety documentation

## License

MIT License - see LICENSE file for details

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
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
- Try restarting the voice service

## Support

- GitHub Issues: Report bugs and request features
- Documentation: See the Wiki for detailed guides

---

Built with ❤️ for better AI accessibility on Android