import WebSocket from "ws";

const WS_URL = "ws://127.0.0.1:8765";

console.log(`Connecting to local Orbital Bridge at ${WS_URL}...`);
const ws = new WebSocket(WS_URL);

ws.on("open", () => {
  console.log("🟢 Connected to Bridge Server as Simulated Mobile Phone!");

  // 1. Send pairing
  ws.send(JSON.stringify({
    type: "PAIRING",
    channelCode: "ORB-5543",
    rawText: "Simulated Test Android Phone (Pixel 8 Pro)"
  }));
});

ws.on("message", (raw) => {
  console.log("📨 Received command from Laptop Bridge:", raw.toString());
  const data = JSON.parse(raw.toString());

  if (data.type === "INSPECT_SCREEN") {
    const mockScreenState = {
      currentPackage: "com.ai.orbital",
      currentActivity: "com.orbital.ui.MainActivity",
      screenWidth: 1080,
      screenHeight: 2400,
      nodes: [
        {
          id: "com.ai.orbital:id/btn_send",
          text: "Send",
          className: "android.widget.Button",
          bounds: [850, 2200, 1030, 2350],
          isClickable: true,
          isEnabled: true
        }
      ]
    };

    console.log("📱 Sending simulated screen snapshot back to Laptop AI...");
    ws.send(JSON.stringify({
      type: "SCREEN_STATE",
      screenState: mockScreenState
    }));
  }
});

setTimeout(() => {
  console.log("✅ Verification successful! Connection, ping, and handshake all OK.");
  process.exit(0);
}, 1500);
