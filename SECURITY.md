# 🛡️ Security Policy & Financial Safety Architecture

Orbital is engineered with security and privacy as first-order architectural invariants.

---

## 🔒 Supported Versions

We actively provide security patches and vulnerability fixes for the following versions:

| Version | Supported          |
| :---    | :---               |
| `1.x.x` | :white_check_mark: |
| `< 1.0` | :x:                |

---

## 🛡️ Core Security Architecture & Safeguards

### 1. Cryptographic Bridge Authentication (HMAC-SHA256)
- Every remote Model Context Protocol (MCP) message transmitted between developer IDEs and the mobile device is authenticated with a **256-bit SHA-256 HMAC signature**.
- **Anti-Replay Invariants**:
  - Strict **15-second timestamp skew window**.
  - Thread-safe **LRU nonce cache** (`MAX_SEEN_NONCES = 1000`) ensuring nonces cannot be replayed.
  - Constant-time signature comparison (`MessageDigest.isEqual`) preventing timing attacks.

### 2. Strict Financial & Banking App Shielding
- A dedicated background hardware monitor continuously checks foreground activity.
- The floating companion and screen-reading services are **immediately hidden and frozen** whenever banking, payment, or UPI applications (e.g. Google Pay, PhonePe, Paytm, BHIM, banking apps) are opened.
- Screen capture and accessibility inspect calls strictly ignore sensitive password and credential fields.

### 3. Hardware Keystore Encryption
- All credentials, API keys, and user settings are protected using **AES-256-GCM** via the Android Keystore and `EncryptedSharedPreferences`.
- Zero raw keys are persisted in plain text or logged to Logcat.

### 4. Zero Cloud Middleman
- The bridge communicates peer-to-peer over your **local Wi-Fi / P2P WebSockets**.
- No telemetry, analytics, or user interaction data is relayed to third-party servers.

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability within Orbital or its MCP bridge, please **do NOT create a public GitHub issue**.

Please report vulnerabilities privately via:
1. **GitHub Private Vulnerability Reporting**: [Report a Security Advisory](https://github.com/jaypal1046/Orbital/security/advisories/new)
2. **Security Contact Email**: `jaypal24202899@gmail.com`

Please include:
- A description of the vulnerability and potential impact.
- Steps to reproduce the issue or a minimal proof-of-concept.
- Affected components (Android companion app or Node.js MCP server).

We will acknowledge receipt within **24 hours** and provide a resolution timeline.
