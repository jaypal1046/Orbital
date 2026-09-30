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

## 🧰 Available MCP Tools

| Tool | Description | Arguments |
|---|---|---|
| `inspect_phone_screen` | Captures live UI hierarchy and view tree | None |
| `tap_phone_element` | Taps element by visible text, ID, or (x, y) coords | `targetText`, `targetId`, `x`, `y` |
| `type_phone_text` | Enters text into the focused input field | `text` (string) |
| `open_phone_app` | Launches an app on the phone by package name | `packageName` (string) |
| `press_phone_key` | Presses standard Android navigation keys | `key` (`"BACK"`, `"HOME"`, `"RECENTS"`) |

---

## 🔒 Security & Privacy

- All communications run strictly over **local Wi-Fi / P2P WebSockets**.
- No telemetry, cloud servers, or 3rd-party relays are used.
- Full source code available on [GitHub](https://github.com/jaypal1046/Orbital).

---

## 📄 License
MIT License. Free and open source.
