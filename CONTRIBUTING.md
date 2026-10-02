# 🤝 Contributing to Orbital

Thank you for your interest in contributing to **Orbital**! We welcome contributions to the Android application, the Model Context Protocol (MCP) bridge, documentation, and automated tests.

---

## 🏗️ Development Setup

### Prerequisites
- **Android Studio** (Koala / Ladybug or newer)
- **Android SDK** (API 34 / 35 / 36)
- **Node.js** (v20+ for MCP server)
- **Git**

### Clone & Build
```bash
git clone https://github.com/jaypal1046/Orbital.git
cd Orbital

# Run Android Unit Tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug

# Run MCP Server locally
cd tools/orbital-mcp
npm install
npm start
```

---

## 📜 Development Guidelines & Principles

### 1. Strict Zero Hardcoding Rule
- Never hardcode static app package lists, routes, mock response maps, or synthetic trees.
- All app resolutions, action executions, and telemetry must be dynamically resolved via Android `PackageManager` or live runtime state.

### 2. Architecture & Code Style
- **Android**: Modern Jetpack Compose, Kotlin Coroutines, StateFlow, Room DB, Hilt Dependency Injection.
- **MCP Server**: Fast, asynchronous Node.js with `@modelcontextprotocol/sdk` and `zod` parameter schemas.
- **Crypto & Security**: All remote action payloads must be covered by HMAC-SHA256 signature generation and constant-time verification.

---

## 🧪 Testing Checklist Before Submitting a PR

1. Ensure all unit tests pass cleanly:
   ```bash
   ./gradlew testDebugUnitTest
   ```
2. Verify debug build compiles with zero errors:
   ```bash
   ./gradlew assembleDebug
   ```
3. Test MCP bridge locally with `tools/orbital-mcp/test_client.js` or through an AI IDE (Antigravity, Cursor, Claude).

---

## 🚀 Pull Request Process

1. Fork the repository and create a new feature branch:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Commit your changes with clear semantic commit messages:
   ```bash
   git commit -m "feat(mcp): add new gesture action support"
   ```
3. Push to your fork and submit a Pull Request to the `main` branch.
4. Fill out the PR template completely.
