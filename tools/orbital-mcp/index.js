#!/usr/bin/env node

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { WebSocketServer } from "ws";
import os from "os";

// 1. Generate 4-digit pairing PIN
const PIN = "ORB-" + Math.floor(1000 + Math.random() * 9000);
const PORT = process.env.PORT ? parseInt(process.env.PORT) : 8765;

// Find local IP address
function getLocalIp() {
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === "IPv4" && !net.internal) {
        return net.address;
      }
    }
  }
  return "127.0.0.1";
}

const LOCAL_IP = getLocalIp();

console.error(`================================================`);
console.error(` 🛰️  Orbital Laptop-to-Mobile AI Bridge Host`);
console.error(`================================================`);
console.error(`🔑 Pairing Code : \x1b[32m${PIN}\x1b[0m`);
console.error(`🌐 Local WS     : \x1b[36mws://${LOCAL_IP}:${PORT}\x1b[0m`);
console.error(`📱 In Orbital App: Open Side Menu -> "Laptop AI Bridge" -> Connect`);
console.error(`================================================\n`);

let activePhoneSocket = null;
const pendingRequests = new Map();

// 2. Start WebSocket Server for Phone Connection
const wss = new WebSocketServer({ port: PORT });

wss.on("connection", (ws) => {
  activePhoneSocket = ws;
  console.error("🟢 Mobile Phone Connected via WebSocket!");

  ws.on("message", (raw) => {
    try {
      const data = JSON.parse(raw.toString());
      if (data.type === "SCREEN_STATE" && pendingRequests.has("INSPECT_SCREEN")) {
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
      }
    } catch (e) {
      console.error("Failed to parse message from phone:", e);
    }
  });

  ws.on("close", () => {
    activePhoneSocket = null;
    console.error("🔴 Mobile Phone Disconnected");
  });
});

// Helper to send command to phone with timeout
function sendToPhone(message, reqKey, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    if (!activePhoneSocket || activePhoneSocket.readyState !== 1) {
      return reject(new Error("No phone currently connected. Please open Orbital on your phone and connect to " + LOCAL_IP));
    }

    const timer = setTimeout(() => {
      pendingRequests.delete(reqKey);
      reject(new Error(`Timeout waiting for phone response (${timeoutMs}ms)`));
    }, timeoutMs);

    pendingRequests.set(reqKey, (result) => {
      clearTimeout(timer);
      resolve(result);
    });

    activePhoneSocket.send(JSON.stringify(message));
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

// Tool 3: Type Text
server.tool(
  "type_phone_text",
  "Type text into the currently focused input field on the phone screen",
  {
    text: z.string().describe("The text string to type into the focused field")
  },
  async ({ text }) => {
    try {
      const actionId = "act-" + Date.now();
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "TYPE_TEXT",
          textToType: text
        }
      };
      const result = await sendToPhone(payload, actionId);
      return {
        content: [{ type: "text", text: `Type Result: ${result.message}` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error typing text: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 4: Open App
server.tool(
  "open_phone_app",
  "Launch any application on the Android phone by package name or app name",
  {
    packageName: z.string().describe("Package name of the app to launch (e.g. 'com.google.android.youtube', 'com.android.settings')")
  },
  async ({ packageName }) => {
    try {
      const actionId = "act-" + Date.now();
      const payload = {
        type: "EXECUTE_ACTION",
        action: {
          actionId,
          actionType: "OPEN_APP",
          packageName
        }
      };
      const result = await sendToPhone(payload, actionId);
      return {
        content: [{ type: "text", text: `Launch Result: ${result.message}` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error launching app: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Tool 5: Press Global Key
server.tool(
  "press_phone_key",
  "Press global Android navigation keys (BACK, HOME, RECENTS)",
  {
    key: z.enum(["BACK", "HOME", "RECENTS"]).describe("Key to press on the Android device")
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
        content: [{ type: "text", text: `Key Result: ${result.message}` }]
      };
    } catch (err) {
      return {
        content: [{ type: "text", text: `Error pressing key: ${err.message}` }],
        isError: true
      };
    }
  }
);

// Connect MCP over Stdio for Claude / Cursor / Antigravity
const transport = new StdioServerTransport();
await server.connect(transport);
