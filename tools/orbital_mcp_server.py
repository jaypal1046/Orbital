#!/usr/bin/env python3
"""
Orbital Laptop-to-Mobile AI Bridge MCP Server (Python)
Exposes Android UI inspection and device actions to Claude / Cursor / Antigravity via Model Context Protocol (MCP).
"""

import asyncio
import json
import socket
import sys
import time
from typing import Any, Dict, Optional

try:
    import websockets
except ImportError:
    print("Please install websockets: pip install websockets", file=sys.stderr)
    sys.exit(1)

PORT = 8765
PIN = f"ORB-{int(time.time()) % 9000 + 1000}"

def get_local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

LOCAL_IP = get_local_ip()
active_phone_ws = None
pending_responses: Dict[str, asyncio.Future] = {}

async def ws_handler(websocket):
    global active_phone_ws
    active_phone_ws = websocket
    print(f"\n🟢 Mobile Phone Connected from {websocket.remote_address}!", file=sys.stderr)

    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                msg_type = data.get("type")
                if msg_type == "SCREEN_STATE" and "INSPECT_SCREEN" in pending_responses:
                    fut = pending_responses.pop("INSPECT_SCREEN")
                    if not fut.done():
                        fut.set_result(data.get("screenState"))
                elif msg_type == "ACTION_RESULT" and "result" in data:
                    action_id = data["result"].get("actionId")
                    if action_id in pending_responses:
                        fut = pending_responses.pop(action_id)
                        if not fut.done():
                            fut.set_result(data["result"])
            except Exception as e:
                print(f"Error parsing message: {e}", file=sys.stderr)
    except Exception as e:
        print(f"WebSocket closed: {e}", file=sys.stderr)
    finally:
        active_phone_ws = None
        print("🔴 Mobile Phone Disconnected", file=sys.stderr)

async def send_to_phone(payload: Dict[str, Any], key: str, timeout_sec: float = 8.0) -> Any:
    global active_phone_ws
    if not active_phone_ws:
        raise RuntimeError(f"No phone currently connected. Connect phone to ws://{LOCAL_IP}:{PORT}")

    loop = asyncio.get_running_loop()
    fut = loop.create_future()
    pending_responses[key] = fut

    await active_phone_ws.send(json.dumps(payload))
    return await asyncio.wait_for(fut, timeout=timeout_sec)

async def handle_stdio_mcp():
    """Handles standard JSON-RPC MCP calls over stdio."""
    print("================================================", file=sys.stderr)
    print(" 🛰️  Orbital Laptop-to-Mobile Python MCP Bridge", file=sys.stderr)
    print("================================================", file=sys.stderr)
    print(f"🔑 Pairing Code : \033[32m{PIN}\033[0m", file=sys.stderr)
    print(f"🌐 Local WS     : \033[36mws://{LOCAL_IP}:{PORT}\033[0m", file=sys.stderr)
    print("📱 In Orbital App: Side Menu -> 'Laptop AI Bridge' -> Connect", file=sys.stderr)
    print("================================================\n", file=sys.stderr)

    # Start WebSocket background server
    server = await websockets.serve(ws_handler, "0.0.0.0", PORT)

    # Process stdio JSON-RPC loop
    loop = asyncio.get_event_loop()
    reader = asyncio.StreamReader()
    protocol = asyncio.StreamReaderProtocol(reader)
    await loop.connect_read_pipe(lambda: protocol, sys.stdin)

    while True:
        line = await reader.readline()
        if not line:
            break
        try:
            req = json.loads(line.decode().strip())
            req_id = req.get("id")
            method = req.get("method")

            if method == "tools/list":
                resp = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "tools": [
                            {
                                "name": "inspect_phone_screen",
                                "description": "Inspect the live UI elements and package currently visible on the phone screen",
                                "inputSchema": { "type": "object", "properties": {} }
                            },
                            {
                                "name": "tap_phone_element",
                                "description": "Tap a button or UI element on the phone screen by label text",
                                "inputSchema": {
                                    "type": "object",
                                    "properties": { "targetText": { "type": "string" } },
                                    "required": ["targetText"]
                                }
                            },
                            {
                                "name": "type_phone_text",
                                "description": "Type text into focused field on the phone screen",
                                "inputSchema": {
                                    "type": "object",
                                    "properties": { "text": { "type": "string" } },
                                    "required": ["text"]
                                }
                            },
                            {
                                "name": "open_phone_app",
                                "description": "Launch an app on the phone by package name",
                                "inputSchema": {
                                    "type": "object",
                                    "properties": { "packageName": { "type": "string" } },
                                    "required": ["packageName"]
                                }
                            }
                        ]
                    }
                }
                sys.stdout.write(json.dumps(resp) + "\n")
                sys.stdout.flush()

            elif method == "tools/call":
                params = req.get("params", {})
                tool_name = params.get("name")
                args = params.get("arguments", {})

                if tool_name == "inspect_phone_screen":
                    state = await send_to_phone({"type": "INSPECT_SCREEN"}, "INSPECT_SCREEN")
                    result_text = json.dumps(state, indent=2)
                elif tool_name == "tap_phone_element":
                    act_id = f"act-{int(time.time()*1000)}"
                    res = await send_to_phone({
                        "type": "EXECUTE_ACTION",
                        "action": { "actionId": act_id, "actionType": "CLICK_NODE", "targetText": args.get("targetText") }
                    }, act_id)
                    result_text = f"Clicked: {res.get('message')}"
                elif tool_name == "type_phone_text":
                    act_id = f"act-{int(time.time()*1000)}"
                    res = await send_to_phone({
                        "type": "EXECUTE_ACTION",
                        "action": { "actionId": act_id, "actionType": "TYPE_TEXT", "textToType": args.get("text") }
                    }, act_id)
                    result_text = f"Typed: {res.get('message')}"
                elif tool_name == "open_phone_app":
                    act_id = f"act-{int(time.time()*1000)}"
                    res = await send_to_phone({
                        "type": "EXECUTE_ACTION",
                        "action": { "actionId": act_id, "actionType": "OPEN_APP", "packageName": args.get("packageName") }
                    }, act_id)
                    result_text = f"Launched: {res.get('message')}"
                else:
                    result_text = f"Unknown tool {tool_name}"

                resp = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": { "content": [{ "type": "text", "text": result_text }] }
                }
                sys.stdout.write(json.dumps(resp) + "\n")
                sys.stdout.flush()
        except Exception as e:
            err_resp = {
                "jsonrpc": "2.0",
                "id": req.get("id"),
                "error": { "code": -32603, "message": str(e) }
            }
            sys.stdout.write(json.dumps(err_resp) + "\n")
            sys.stdout.flush()

if __name__ == "__main__":
    asyncio.run(handle_stdio_mcp())
