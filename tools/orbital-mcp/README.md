# Orbital MCP Server (`orbital-mcp`)

[![npm version](https://img.shields.io/npm/v/orbital-mcp.svg)](https://www.npmjs.com/package/orbital-mcp)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Model Context Protocol (MCP)** server for controlling and inspecting your Android phone in real-time from AI models (Claude Desktop, Cursor IDE, Windsurf, Antigravity, or custom agent scripts) using the [Orbital Android App](https://github.com/jaypal1046/Orbital).

---

## 🚀 Quick Start (Zero Install)

You can run `orbital-mcp` directly without installing:

```bash
npx -y orbital-mcp
```

This will output your local IP and pairing code:
```
================================================
 🛰️  Orbital Laptop-to-Mobile AI Bridge Host
================================================
🔑 Pairing Code : ORB-5543
🌐 Local WS     : ws://192.168.1.5:8765
📱 In Orbital App: Open Side Menu -> "Laptop AI Bridge" -> Connect
================================================
```

---

## 🛠️ AI Configuration

### 1. Claude Desktop
Add to your `claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "orbital-phone": {
      "command": "npx",
      "args": ["-y", "orbital-mcp"]
    }
  }
}
```

### 2. Cursor / Windsurf IDE
In Cursor Settings $\rightarrow$ Features $\rightarrow$ MCP Servers $\rightarrow$ Add New:
- **Name**: `orbital-phone`
- **Type**: `command`
- **Command**: `npx -y orbital-mcp`

### 3. Antigravity IDE (`mcp_config.json`)
```json
{
  "mcpServers": {
    "orbital-phone": {
      "command": "npx",
      "args": ["-y", "orbital-mcp"]
    }
  }
}
```

---

## 📱 Connect Your Android Device

1. Install and open the **[Orbital Android App](https://github.com/jaypal1046/Orbital)**.
2. Grant **Accessibility Service** permission.
3. Open the side menu $\rightarrow$ tap **Laptop AI Bridge**.
4. Enter your laptop's IP address (e.g. `192.168.1.5`) or PIN.
5. Tap **Connect**!

---

## 🎯 Automated App Testing & QA Use Cases

With `orbital-mcp`, developers and AI agents can execute automated End-to-End tests on ANY Android application:

```text
"Open WhatsApp, search for 'Dev Group', send 'Build #42 deployed', and assert that 'Build #42 deployed' is visible."
```

```text
"Launch Amazon app, search for 'Mechanical Keyboard', scroll down twice, click the first product, and verify the Buy Now button exists."
```

```text
"Run a full regression test on Settings -> Display -> Dark Mode toggle and report any UI hierarchy anomalies."
```

---

## 🧰 Available MCP Tools

| Tool | Description | Arguments |
|---|---|---|
| `inspect_phone_screen` | Captures live UI hierarchy and view tree of any open app | None |
| `tap_phone_element` | Taps element by visible text, ID, or (x, y) coords | `targetText`, `targetId` |
| `type_phone_text` | Enters text into the focused input field | `text` (string) |
| `swipe_phone_screen` | Scrolls/swipes screen in a direction | `direction` (`"UP"`, `"DOWN"`) |
| `open_phone_app` | Launches any target app under test by package name | `packageName` (string) |
| `assert_screen_contains` | Asserts that expected text exists on screen (for QA tests) | `expectedText` (string) |
| `press_phone_key` | Presses standard Android navigation keys | `key` (`"BACK"`, `"HOME"`, `"RECENTS"`) |
| `ask_phone_ai` | Delegates high-level task to Phone AI companion for autonomous execution | `prompt` (string) |

---

## 🔒 Security & Privacy

- All communications run strictly over **local Wi-Fi / P2P WebSockets**.
- No telemetry, cloud servers, or 3rd-party relays are used.
- Full source code available on [GitHub](https://github.com/jaypal1046/Orbital).

---

## 📄 License
MIT License. Free and open source.
