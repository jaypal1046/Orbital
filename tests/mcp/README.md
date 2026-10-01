# Orbital MCP Bridge Test Suite

Automated regression and end-to-end test suite for verifying the **Orbital Laptop-to-Mobile AI Bridge** and all on-device MCP automation capabilities.

## Running the Tests

Ensure the Orbital app is running on the Android device and connected to the Laptop AI Bridge. Then execute:

```bash
node tests/mcp/orbital_bridge_test_suite.js
```

### Environment Variables (Optional)
- `BRIDGE_HOST` (default: `127.0.0.1`)
- `BRIDGE_HTTP_PORT` (default: `8766`)

## Test Coverage
1. **Bridge Health & Link**: Verifies bidirectional WebSocket connection, PIN, and host registration.
2. **Screen Node Extraction**: Validates live accessibility tree node counts and package resolution.
3. **App Launching**: Tests dynamic package resolution and intent dispatching.
4. **Phone AI Task Delegation**: Validates multi-step natural language goal execution.
5. **System Key Navigation**: Validates `HOME`, `BACK`, and `RECENTS` key dispatches.
6. **Hardware & System Actions**: Validates device status and hardware controls.
7. **Gesture Simulation**: Validates scroll gestures and swipe actions.

## Generated Test Artifacts
- `tests/mcp/test_results.json`: Machine-readable results and timing data.
- `tests/mcp/test_results.md`: Human-readable summary table for QA logs.
