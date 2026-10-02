#!/usr/bin/env node

process.on("uncaughtException", (err) => {
  if (err && err.code === "EADDRINUSE") {
    console.error(`\n⚠️  Port busy (EADDRINUSE). Continuing MCP stdio communication...`);
    return;
  }
  console.error("Uncaught Exception:", err);
});

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { WebSocketServer } from "ws";
import https from "https";
import http from "http";
import os from "os";
import crypto from "crypto";
import { Bonjour } from "bonjour-service";
import selfsigned from "selfsigned";
import qrcode from "qrcode-terminal";

// 1. Generate 4-digit pairing PIN and 256-bit Bitcoin-grade cryptographic session secret
const PIN = "ORB-" + Math.floor(1000 + Math.random() * 9000);
const PORT = process.env.PORT ? parseInt(process.env.PORT) : 8765;
const HOSTNAME = os.hostname() || "Laptop";
const AUTH_KEY = crypto.randomBytes(32).toString("hex");
const KEY_FINGERPRINT = crypto.createHash("sha256").update(AUTH_KEY).digest("hex").slice(0, 16).match(/.{1,4}/g).join(":");

// Find local IP address, prioritizing physical Wi-Fi / Ethernet
function getAllLocalIps() {
  const ips = [];
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    const isVirtual = /vmnet|virtual|vbox|vEthernet|loopback/i.test(name);
    for (const net of nets[name]) {
      if (net.family === "IPv4" && !net.internal) {
        ips.push({ name, address: net.address, isVirtual });
      }
    }
  }
  // Sort physical first
  ips.sort((a, b) => (a.isVirtual === b.isVirtual ? 0 : a.isVirtual ? 1 : -1));
  return ips;
}

const ALL_IPS = getAllLocalIps();
const LOCAL_IP = ALL_IPS[0]?.address || "127.0.0.1";

// QR Payload with cryptographic key and connection details
const QR_PAYLOAD = `orbital://pair?v=1&host=${LOCAL_IP}&port=${PORT}&pin=${PIN}&key=${AUTH_KEY}&name=${encodeURIComponent(HOSTNAME)}`;

// Generate ephemeral SSL certificate for local TLS/WSS (Quick Share DTLS style)
const pems = selfsigned.generate([{ name: "commonName", value: LOCAL_IP }], { keySize: 2048, days: 365 });

console.error(`\n================================================================`);
console.error(` 🛰️  ORBITAL LAPTOP-TO-MOBILE AI BRIDGE (SECURE CRYPTO HOST)`);
console.error(`================================================================`);
console.error(`🔑 Pairing PIN   : \x1b[32m\x1b[1m${PIN}\x1b[0m`);
console.error(`🔐 Crypto Key ID : \x1b[33m${KEY_FINGERPRINT}\x1b[0m (256-bit Bitcoin-grade Auth)`);
console.error(`🌐 Primary WSS   : \x1b[36mwss://${LOCAL_IP}:${PORT}\x1b[0m`);
if (ALL_IPS.length > 1) {
  ALL_IPS.slice(1).forEach(ip => {
    console.error(`   Alternate IP  : wss://${ip.address}:${PORT} (${ip.name})`);
  });
}
console.error(`📡 QuickShare NSD: \x1b[35m_orbital-bridge._tcp (${HOSTNAME})\x1b[0m`);
console.error(`📱 Scan the QR Code below with Orbital Mobile App:`);
console.error(`================================================================\n`);

qrcode.generate(QR_PAYLOAD, { small: true }, (qr) => {
  console.error(qr);
  console.error(`\n================================================================\n`);
});

// Helper to sign messages with HMAC-SHA256
function signPayload(content, timestamp) {
  const hmac = crypto.createHmac("sha256", AUTH_KEY);
  hmac.update(`${timestamp}:${content}`);
  return hmac.digest("hex");
}

let activePhoneSocket = null;
const pendingRequests = new Map();

// 2. Start Secure WSS / HTTP Dual Server
async function handleHttpRequest(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.url === "/inspect") {
    try {
      const screenState = await sendToPhone({ type: "INSPECT_SCREEN" }, "INSPECT_SCREEN");
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(screenState, null, 2));
    } catch (err) {
      res.writeHead(500, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: err.message }));
    }
  } else if (req.url === "/action" && req.method === "POST") {
    let body = "";
    req.on("data", chunk => { body += chunk; });
    req.on("end", async () => {
      try {
        const actionPayload = JSON.parse(body);
        const actionId = actionPayload.actionId || "act-" + Date.now();
        const fullPayload = {
          type: "EXECUTE_ACTION",
          action: {
            ...actionPayload,
            actionId
          }
        };
        const result = await sendToPhone(fullPayload, actionId, 15000);
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify(result, null, 2));
      } catch (err) {
        res.writeHead(500, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ error: err.message }));
      }
    });
  } else {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({
      status: "ok",
      name: "Orbital AI Bridge",
      pin: PIN,
      host: HOSTNAME,
      phoneConnected: activePhoneSocket !== null,
      endpoints: ["/inspect", "/action"]
    }, null, 2));
  }
}

const serverHttps = https.createServer({
  key: pems.private,
  cert: pems.cert
}, handleHttpRequest);

const wss = new WebSocketServer({ noServer: true });
wss.on("connection", (ws) => setupWebSocket(ws, "WSS (Encrypted TLS)"));

serverHttps.on("upgrade", (request, socket, head) => {
  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit("connection", ws, request);
  });
});

serverHttps.on("error", (err) => {
  console.error("HTTPS Server warning:", err.message);
});

const serverHttp = http.createServer(handleHttpRequest);
const wssHttp = new WebSocketServer({ noServer: true });
wssHttp.on("connection", (ws) => setupWebSocket(ws, "WS"));

serverHttp.on("upgrade", (request, socket, head) => {
  wssHttp.handleUpgrade(request, socket, head, (ws) => {
    wssHttp.emit("connection", ws, request);
  });
});

serverHttp.on("error", (err) => {
  console.error("HTTP Server warning:", err.message);
});

function setupWebSocket(ws, protocol) {
  activePhoneSocket = ws;
  console.error(`🟢 Mobile Phone Connected via ${protocol}!`);

  ws.on("message", (raw) => {
    try {
      const data = JSON.parse(raw.toString());
      if (data.type === "PAIRING") {
        const pairingAck = {
          type: "PAIRING_ACK",
          token: AUTH_KEY,
          authFingerprint: KEY_FINGERPRINT,
          rawText: "Pairing acknowledged by Host"
        };
        ws.send(JSON.stringify(pairingAck));
        console.error("🔒 Sent PAIRING_ACK with session cryptographic key to mobile device");
      } else if (data.type === "SCREEN_STATE" && pendingRequests.has("INSPECT_SCREEN")) {
        const resolve = pendingRequests.get("INSPECT_SCREEN");
        pendingRequests.delete("INSPECT_SCREEN");
        resolve(data.screenState);
      } else if (data.type === "ACTION_RESULT" && data.result) {
        const actionId = data.result.actionId;
        if (pendingRequests.has(actionId)) {
          const resolve = pendingRequests.get(actionId);
          pendingRequests.delete(actionId);
          resolve(data.result);
        }
      } else if (data.type === "SECURITY_ALERT") {
        console.error("⚠️ Security alert received from mobile:", data.rawText);
        for (const [key, callback] of pendingRequests.entries()) {
          pendingRequests.delete(key);
          callback({ success: false, message: `Security Alert: ${data.rawText}` });
        }
      }
    } catch (e) {
      console.error("Failed to parse message from phone:", e);
    }
  });

  ws.on("close", () => {
    if (activePhoneSocket === ws) {
      activePhoneSocket = null;
    }
    console.error(`🔴 Mobile Phone Disconnected (${protocol})`);
  });
}

try {
  serverHttps.listen(PORT, "0.0.0.0", () => {
    try {
      const bonjour = new Bonjour();
      bonjour.publish({
        name: `${HOSTNAME} (Orbital Bridge)`,
        type: "orbital-bridge",
        port: PORT,
        txt: {
          pin: PIN,
          host: HOSTNAME,
          ip: LOCAL_IP,
          ver: "1.0.0",
          fingerprint: KEY_FINGERPRINT
        }
      });
      console.error(`📡 Broadcasting mDNS service: _orbital-bridge._tcp on local network.`);
    } catch (err) {
      console.error(`mDNS broadcast warning: ${err.message}`);
    }
  });
} catch (e) {
  console.error("Could not start HTTPS listener:", e.message);
}

try {
  serverHttp.listen(PORT + 1, "0.0.0.0");
} catch (e) {}

// Helper to send command to phone with HMAC-SHA256 signature and timeout
function sendToPhone(message, reqKey, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    if (!activePhoneSocket || activePhoneSocket.readyState !== 1) {
      return reject(new Error("No phone currently connected. Please open Orbital on your phone and scan the QR code to connect to " + LOCAL_IP));
    }

    const timer = setTimeout(() => {
      pendingRequests.delete(reqKey);
      reject(new Error(`Timeout waiting for phone response (${timeoutMs}ms)`));
    }, timeoutMs);

    pendingRequests.set(reqKey, (result) => {
      clearTimeout(timer);
      resolve(result);
    });

    const timestamp = Date.now();
    const actionKey = message.action ? message.action.actionId : message.type;
    const signature = signPayload(actionKey, timestamp);

    const signedMessage = {
      ...message,
      timestamp,
      token: AUTH_KEY,
      signature,
      authFingerprint: KEY_FINGERPRINT
    };

    activePhoneSocket.send(JSON.stringify(signedMessage));
  });
}

// 3. Create Model Context Protocol (MCP) Server
const server = new McpServer({
  name: "orbital-mobile-controller",
  version: "1.0.0"
});

// Tool 1: Inspect Screen
server.tool(
  "inspect_phone_screen",
  "Inspect the live UI hierarchy, buttons, text fields, and package currently visible on the Android phone screen",
  {},
  async () => {
    try {
      const screenState = await sendToPhone({ type: "INSPECT_SCREEN" }, "INSPECT_SCREEN");
      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(screenState, null, 2)
          }
        ]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error inspecting screen: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 2: Tap UI Element
server.tool(
  "tap_phone_element",
  "Tap/click a specific button or text element on the phone screen",
  {
    targetText: z.string().describe("Text or label of the element to tap (e.g. 'Wi-Fi', 'Search', 'Send')"),
    targetId: z.string().optional().describe("Optional resource ID of the view (e.g. 'com.android.settings:id/title')")
  },
  async ({ targetText, targetId }) => {
    try {
      const actionId = "act-" + Date.now();
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "CLICK_NODE",
          targetText,
          targetId: targetId || null
        }
      };
      const result = await sendToPhone(payload, actionId);
      return {
        content: [{ type: "text", text: `Action Result: ${result.message} (success=${result.success})` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error tapping element: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 3: Tap Coordinates (Exact X, Y Pixel Tap)
server.tool(
  "tap_phone_coordinates",
  "Tap exact (x, y) pixel coordinates on the phone screen",
  {
    x: z.number().describe("X coordinate in pixels (e.g. 540)"),
    y: z.number().describe("Y coordinate in pixels (e.g. 1100)")
  },
  async ({ x, y }) => {
    try {
      const actionId = "act-" + Date.now();
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "CLICK_COORDINATES",
          coordinates: [Math.round(x), Math.round(y)]
        }
      };
      const result = await sendToPhone(payload, actionId);
      return {
        content: [{ type: "text", text: `Tap Coordinates Result: ${result.message} (success=${result.success})` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error tapping coordinates: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 4: Type Text
server.tool(
  "type_phone_text",
  "Type text into the currently focused or specified input field on the phone screen",
  {
    text: z.string().describe("The text string to type into the field"),
    targetLabel: z.string().optional().describe("Optional label or hint of the target text box")
  },
  async ({ text, targetLabel }) => {
    try {
      const actionId = "act-" + Date.now();
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "TYPE_TEXT",
          textToType: text,
          targetText: targetLabel || null
        }
      };
      const result = await sendToPhone(payload, actionId);
      return {
        content: [{ type: "text", text: `Type Result: ${result.message} (success=${result.success})` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error typing text: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 5: Open App
server.tool(
  "open_phone_app",
  "Launch any application on the Android phone by package name (e.g. 'com.google.android.youtube') or common app name (e.g. 'Settings', 'YouTube', 'WhatsApp', 'Chrome', 'Camera')",
  {
    packageName: z.string().describe("Name of the app (e.g. 'Settings', 'YouTube') or exact package name (e.g. 'com.android.settings')")
  },
  async ({ packageName }) => {
    try {
      const actionId = "act-" + Date.now();
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "OPEN_APP",
          packageName: packageName,
          targetText: packageName
        }
      };
      const result = await sendToPhone(payload, actionId);
      return {
        content: [{ type: "text", text: `Launch Result: ${result.message} (success=${result.success})` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error launching app: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 6: Swipe Screen
server.tool(
  "swipe_phone_screen",
  "Scroll or swipe the phone screen in a specified direction (UP, DOWN, LEFT, RIGHT) or custom start/end coordinates",
  {
    direction: z.enum(["UP", "DOWN", "LEFT", "RIGHT"]).describe("Direction to swipe/scroll"),
    startX: z.number().optional().describe("Optional custom gesture start X"),
    startY: z.number().optional().describe("Optional custom gesture start Y"),
    endX: z.number().optional().describe("Optional custom gesture end X"),
    endY: z.number().optional().describe("Optional custom gesture end Y")
  },
  async ({ direction, startX, startY, endX, endY }) => {
    try {
      const actionId = "act-" + Date.now();
      const hasCoords = startX !== undefined && startY !== undefined && endX !== undefined && endY !== undefined;
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "SWIPE",
          swipeDirection: direction,
          startCoordinates: hasCoords ? [Math.round(startX), Math.round(startY)] : null,
          endCoordinates: hasCoords ? [Math.round(endX), Math.round(endY)] : null
        }
      };
      const result = await sendToPhone(payload, actionId);
      return {
        content: [{ type: "text", text: `Swipe Result: ${result.message} (success=${result.success})` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error swiping screen: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 7: Press Global Key
server.tool(
  "press_phone_key",
  "Press global Android navigation and system keys (BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, TAKE_SCREENSHOT)",
  {
    key: z.enum(["BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS", "LOCK_SCREEN", "TAKE_SCREENSHOT"]).describe("Key to press on the Android device")
  },
  async ({ key }) => {
    try {
      const actionId = "act-" + Date.now();
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "PRESS_KEY",
          keyCode: key
        }
      };
      const result = await sendToPhone(payload, actionId);
      return {
        content: [{ type: "text", text: `Key Result: ${result.message} (success=${result.success})` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error pressing key: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 8: Execute Device Hardware & System Action
server.tool(
  "execute_device_action",
  "Execute native Android system and hardware actions (FLASHLIGHT, DEVICE_STATUS, SET_SOUND_MODE, OPEN_SETTING, SET_TIMER, SEARCH_WEB, OPEN_URL)",
  {
    action: z.enum([
      "FLASHLIGHT",
      "DEVICE_STATUS",
      "SET_SOUND_MODE",
      "OPEN_SETTING",
      "SET_TIMER",
      "SEARCH_WEB",
      "OPEN_URL"
    ]).describe("The action to perform"),
    target: z.string().optional().describe("Parameter for the action (e.g. 'on'/'off' for FLASHLIGHT, 'NORMAL'/'VIBRATE'/'SILENT' for SOUND_MODE, 'WIFI'/'BLUETOOTH' for OPEN_SETTING, URL for OPEN_URL)"),
    query: z.string().optional().describe("Search query for SEARCH_WEB or label for SET_TIMER"),
    enabled: z.boolean().optional().describe("Boolean state (e.g. true for flashlight on)")
  },
  async ({ action, target, query, enabled }) => {
    try {
      const actionId = "act-" + Date.now();
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "DEVICE_ACTION",
          deviceAction: action,
          target: target || null,
          query: query || null,
          enabled: enabled !== undefined ? enabled : null
        }
      };
      const result = await sendToPhone(payload, actionId);
      return {
        content: [{ type: "text", text: `Device Action Result: ${result.message} (success=${result.success})` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error executing device action: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 9: Assert Screen Contains (App Testing / QA Verification)
server.tool(
  "assert_screen_contains",
  "Verify and assert that specific text or element exists on the phone screen (useful for automated app testing & QA)",
  {
    expectedText: z.string().describe("The text that MUST be present on screen for test to pass")
  },
  async ({ expectedText }) => {
    try {
      const screenState = await sendToPhone({ type: "INSPECT_SCREEN" }, "INSPECT_SCREEN");
      const found = screenState.nodes?.some(node =>
        node.text?.toLowerCase().includes(expectedText.toLowerCase()) ||
        node.contentDescription?.toLowerCase().includes(expectedText.toLowerCase())
      );

      if (found) {
        return {
          content: [{ type: "text", text: ` Assertion PASSED: Found '${expectedText}' in ${screenState.currentPackage}` }]
        };
      } else {
        const visibleTexts = screenState.nodes?.map(n => n.text).filter(Boolean).slice(0, 10).join(", ");
        return {
          content: [{ type: "text", text: `❌ Assertion FAILED: Expected '${expectedText}', but visible texts are: [${visibleTexts}]` }],
          isError: true
        };
      }
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error asserting screen content: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 10: Ask Phone AI (AI-to-AI Autonomous Task Delegation)
server.tool(
  "ask_phone_ai",
  "Delegate a high-level task or query to the on-device Orbital Phone AI. The Phone AI autonomously routes through local/cloud LLMs and executes device actions (apps, settings, workflows) and returns its full result.",
  {
    prompt: z.string().describe("The high-level natural language instruction for the Phone AI (e.g. 'Turn on flashlight and check battery', 'Book a ride to Central Station', 'Summarize my recent messages')")
  },
  async ({ prompt }) => {
    try {
      const actionId = "act-" + Date.now();
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "CUSTOM_PROMPT",
          customPrompt: prompt
        }
      };
      const result = await sendToPhone(payload, actionId, 25000); // 25s timeout for AI reasoning & action execution
      return {
        content: [
          {
            type: "text",
            text: `📱 Phone AI Response: ${result.aiResponse || result.message}\n⏱️ Execution Time: ${result.executionDurationMs}ms`
          }
        ]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error delegating to Phone AI: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Connect MCP over Stdio for Claude / Cursor / Antigravity
const transport = new StdioServerTransport();
await server.connect(transport);

