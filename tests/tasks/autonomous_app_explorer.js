#!/usr/bin/env node

/**
 * 🛰️ ORBITAL AUTONOMOUS APP EXPLORATION & DEEP COMPREHENSION ENGINE
 * 
 * Provides "Eyes for AI" to autonomously explore, inspect, and analyze any native Android application:
 * 1. Dynamic package discovery and installation verification
 * 2. Spatial UI sector analysis (Header, Right Actions, Main Canvas, Bottom Navigation)
 * 3. Multi-screen graph traversal with SHA-256 state deduplication
 * 4. Comprehensive Product & UX Analysis Report Generation
 */

import http from "http";
import fs from "fs";
import path from "path";
import crypto from "crypto";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, "../../");

const BRIDGE_HOST = process.env.BRIDGE_HOST || "127.0.0.1";
const BRIDGE_PORT = parseInt(process.env.BRIDGE_PORT || "8766");

const log = (emoji, msg) => console.log(`  ${emoji} ${msg}`);
const delay = (ms) => new Promise((res) => setTimeout(res, ms));

async function bridgeRequest(endpoint, payload = null) {
  return new Promise((resolve, reject) => {
    const isPost = !!payload;
    const options = {
      hostname: BRIDGE_HOST,
      port: BRIDGE_PORT,
      path: endpoint,
      method: isPost ? "POST" : "GET",
      headers: isPost ? { "Content-Type": "application/json" } : {}
    };

    const req = http.request(options, (res) => {
      let data = "";
      res.on("data", chunk => { data += chunk; });
      res.on("end", () => {
        try {
          const parsed = JSON.parse(data);
          resolve(parsed);
        } catch (e) {
          resolve({ raw: data });
        }
      });
    });

    req.on("error", (err) => reject(err));
    req.setTimeout(30000, () => {
      req.destroy();
      reject(new Error("Bridge request timeout"));
    });

    if (payload) {
      req.write(JSON.stringify(payload));
    }
    req.end();
  });
}

// Robust Spatial Sector & Structural UI Classifier
function analyzeSpatialLayout(screenState) {
  const width = screenState.screenWidth || 1080;
  const height = screenState.screenHeight || 2400;
  const nodes = screenState.nodes || [];

  const sectors = {
    topBar: [],
    rightActions: [],
    leftNavigation: [],
    centerCanvas: [],
    bottomNavigation: []
  };

  const interactiveElements = [];
  const textElements = [];

  for (const node of nodes) {
    const text = (node.text || node.contentDescription || "").trim();
    if (text) {
      textElements.push(text);
    }

    if (node.isClickable || node.isEditable || node.isScrollable) {
      interactiveElements.push({
        id: node.id,
        text: text,
        className: node.className,
        bounds: node.bounds,
        isClickable: node.isClickable,
        isEditable: node.isEditable
      });
    }

    if (!node.bounds || node.bounds.length < 4) continue;
    const [left, top, right, bottom] = node.bounds;
    const nodeWidth = right - left;
    const nodeHeight = bottom - top;
    const centerY = (top + bottom) / 2;
    const centerX = (left + right) / 2;

    const idLower = (node.id || "").toLowerCase();
    const classLower = (node.className || "").toLowerCase();

    // Strict Bottom Navigation Detection (excludes full-width scrollable list items)
    const isNavClass = classLower.includes("bottomnav") || classLower.includes("navigationbar") || classLower.includes("tablayout");
    const isNavId = idLower.includes("bottom_nav") || idLower.includes("tab_bar") || idLower.includes("navigation_bar");
    const isAnchoredBottomTab = bottom >= height * 0.94 && nodeHeight <= 220 && nodeWidth <= width * 0.45 && node.isClickable;

    if (isNavClass || isNavId || isAnchoredBottomTab) {
      if (text) sectors.bottomNavigation.push({ text, id: node.id, bounds: node.bounds });
    } else if (centerY < height * 0.14) {
      if (text) sectors.topBar.push({ text, id: node.id, bounds: node.bounds });
    } else if (centerX > width * 0.78 && nodeWidth < width * 0.35 && node.isClickable) {
      if (text) sectors.rightActions.push({ text, id: node.id, bounds: node.bounds });
    } else if (centerX < width * 0.22 && nodeWidth < width * 0.35 && node.isClickable) {
      if (text) sectors.leftNavigation.push({ text, id: node.id, bounds: node.bounds });
    } else {
      if (text) sectors.centerCanvas.push({ text, id: node.id, bounds: node.bounds });
    }
  }

  const uniqueTexts = [...new Set(textElements)];
  const screenFingerprint = crypto.createHash("sha256")
    .update((screenState.currentPackage || "") + ":" + uniqueTexts.slice(0, 20).sort().join("|"))
    .digest("hex")
    .slice(0, 16);

  return {
    sectors,
    interactiveElements,
    textElements: uniqueTexts,
    screenFingerprint
  };
}

export async function exploreAndAnalyzeApp(targetAppName, options = {}) {
  if (!targetAppName || typeof targetAppName !== "string" || targetAppName.trim().length === 0) {
    throw new Error("Target application name is required for dynamic exploration (Zero Hardcoding).");
  }

  const cleanTargetName = targetAppName.trim();
  const startTime = Date.now();
  console.log("\n================================================================================");
  console.log(`  🛰️  ORBITAL DEEP APP EXPLORATION & PRODUCT COMPREHENSION ENGINE`);
  console.log(`  Target Application: "${cleanTargetName}"`);
  console.log("================================================================================\n");

  const report = {
    targetApp: cleanTargetName,
    timestamp: new Date().toISOString(),
    status: "IN_PROGRESS",
    installed: false,
    packageName: null,
    screensDiscovered: [],
    navigationTopology: [],
    spatialBreakdown: {},
    featureCatalog: [],
    userValueProposition: null,
    attractionFactors: [],
    frictionPoints: [],
    durationMs: 0
  };

  const visitedFingerprints = new Set();

  try {
    // 1. Launch App dynamically
    log("🚀", `Launching application: '${cleanTargetName}'...`);
    await bridgeRequest("/action", {
      actionType: "OPEN_APP",
      packageName: cleanTargetName,
      targetText: cleanTargetName
    });

    await delay(1800); // Allow app surface & activity to render

    // 2. Inspect Initial Surface (Landing / Launch Screen)
    log("👁️", "Inspecting launch screen & spatial hierarchy...");
    const initialScreen = await bridgeRequest("/inspect");
    const initialPkg = initialScreen.currentPackage || "unknown";
    report.packageName = initialPkg;

    if (!initialPkg || initialPkg === "unknown" || initialPkg.includes("launcher") || initialPkg.includes("systemui")) {
      log("⚠️", `App '${cleanTargetName}' is not active in foreground (Current: ${initialPkg}).`);
    } else {
      report.installed = true;
      log("✅", `Active in foreground: ${initialPkg}`);
    }

    const launchSpatial = analyzeSpatialLayout(initialScreen);
    visitedFingerprints.add(launchSpatial.screenFingerprint);
    report.spatialBreakdown.launchScreen = launchSpatial;

    report.screensDiscovered.push({
      screenName: "Launch / Main Surface",
      package: initialPkg,
      fingerprint: launchSpatial.screenFingerprint,
      interactiveNodesCount: launchSpatial.interactiveElements.length,
      visibleTexts: launchSpatial.textElements.slice(0, 15),
      topBarElements: launchSpatial.sectors.topBar.map(n => n.text).filter(Boolean),
      rightActionElements: launchSpatial.sectors.rightActions.map(n => n.text).filter(Boolean),
      bottomNavElements: launchSpatial.sectors.bottomNavigation.map(n => n.text).filter(Boolean)
    });

    log("📍", `Discovered Top Bar Controls: [${launchSpatial.sectors.topBar.map(n => n.text).filter(Boolean).join(", ") || "Not observed"}]`);
    log("📍", `Discovered Right-Side Controls: [${launchSpatial.sectors.rightActions.map(n => n.text).filter(Boolean).join(", ") || "None"}]`);
    log("📍", `Discovered Bottom Navigation: [${launchSpatial.sectors.bottomNavigation.map(n => n.text).filter(Boolean).join(", ") || "Not observed"}]`);

    // 3. Explore Navigation Tabs & Gateways (Deduplicated)
    const potentialTabs = launchSpatial.sectors.bottomNavigation
      .filter(n => n.text && n.text.trim().length > 1)
      .slice(0, 4);

    for (const tab of potentialTabs) {
      log("🧭", `Navigating to tab / gateway: '${tab.text}'...`);
      await bridgeRequest("/action", {
        actionType: "CLICK_NODE",
        targetText: tab.text
      });
      await delay(1200);

      const tabScreen = await bridgeRequest("/inspect");
      const tabSpatial = analyzeSpatialLayout(tabScreen);

      // Skip duplicate / unmoving screens
      if (!visitedFingerprints.has(tabSpatial.screenFingerprint)) {
        visitedFingerprints.add(tabSpatial.screenFingerprint);
        report.screensDiscovered.push({
          screenName: `Tab: ${tab.text}`,
          package: tabScreen.currentPackage,
          fingerprint: tabSpatial.screenFingerprint,
          interactiveNodesCount: tabSpatial.interactiveElements.length,
          visibleTexts: tabSpatial.textElements.slice(0, 15)
        });
        report.navigationTopology.push({
          from: "Main Surface",
          to: tab.text,
          transition: "BOTTOM_TAB_CLICK"
        });
      } else {
        log("ℹ️", `Screen for '${tab.text}' already visited or unchanged.`);
      }
    }

    // 4. Synthesize Deep Feature Catalog & Value Proposition
    log("🧠", "Synthesizing product intelligence and UX assessment...");
    const perceptionAvailable = initialPkg !== "unknown" && launchSpatial.textElements.length > 0;
    report.userValueProposition = perceptionAvailable
      ? `Observed mobile workflows for '${cleanTargetName}'.`
      : "Not assessed: the bridge did not provide a package identity or accessible UI hierarchy.";
    report.attractionFactors = perceptionAvailable ? [
      `Accessible UI hierarchy exposed by ${report.packageName}`,
      `Interactive direct-touch surface with ${launchSpatial.interactiveElements.length} detected targets`
    ] : ["No product-perception claims made without live UI evidence."];

    report.frictionPoints = [
      launchSpatial.interactiveElements.length === 0 ? "High friction: Surface lacks detectable accessibility nodes" : "Low friction: Interactive elements properly mapped",
      report.screensDiscovered.length > 1 ? "Multi-tab hierarchy requires tab-switching navigation" : "Single-page direct interaction"
    ];

    report.status = perceptionAvailable ? "COMPLETED" : "INCONCLUSIVE";
    report.durationMs = Date.now() - startTime;

    // Save Markdown Analysis Artifact
    const reportMd = generateMarkdownReport(report);
    const reportPath = path.join(ROOT_DIR, "doc", `APP_ANALYSIS_${cleanTargetName.toUpperCase().replace(/[^A-Z0-9]/g, "_")}.md`);
    fs.writeFileSync(reportPath, reportMd);
    log("📝", `Saved comprehensive analysis report to: ${reportPath}`);

    return report;
  } catch (err) {
    report.status = "FAILED";
    report.error = err.message;
    report.durationMs = Date.now() - startTime;
    log("❌", `App exploration encountered error: ${err.message}`);
    return report;
  }
}

function generateMarkdownReport(report) {
  const launch = report.screensDiscovered[0];
  const unavailable = "Not observed — live hierarchy unavailable";
  return `# 📱 Autonomous App Deep Analysis Report: ${report.targetApp}

**Generated by:** Orbital AI Companion & Mobile Perception Engine  
**Timestamp:** ${report.timestamp}  
**Execution Duration:** ${report.durationMs}ms  
**Package:** \`${report.packageName || "N/A"}\`  
**Installation Status:** ${report.installed ? "✅ Verified Installed on Device" : "⚠️ Not in Foreground / Requires Verification"}

---

## 1. Executive Summary & Value Proposition
- **App Identity:** ${report.targetApp}
- **Core Utility:** ${report.userValueProposition}
- **Why Users Choose It:** ${report.status === "COMPLETED" ? "Observed from the live UI hierarchy." : "Not assessed without live UI evidence."}

---

## 2. Spatial Screen Architecture & Layout Analysis

| Sector | Detected Elements | Functionality |
| :--- | :--- | :--- |
| **Top Bar / Header** | ${launch?.topBarElements?.join(", ") || unavailable} | Observed controls only |
| **Right-Side Utility** | ${launch?.rightActionElements?.join(", ") || unavailable} | Observed controls only |
| **Center Canvas** | ${launch?.visibleTexts?.join(", ") || unavailable} | Observed content only |
| **Bottom Navigation** | ${launch?.bottomNavElements?.join(", ") || unavailable} | Observed controls only |

---

## 3. Discovered Screens & Navigation Topology
Total Unique Screens Explored: **${report.screensDiscovered.length}**

${report.screensDiscovered.map((s, idx) => `
### Screen ${idx + 1}: ${s.screenName}
- **Package:** \`${s.package}\`
- **Fingerprint:** \`${s.fingerprint}\`
- **Interactive Targets:** ${s.interactiveNodesCount} clickable/editable nodes
- **Visible Content Highlights:**
${s.visibleTexts.map(t => `  - "${t}"`).join("\n")}
`).join("\n")}

---

## 4. User Attraction & Retention Factors
${report.attractionFactors.map(f => `- **${f}**`).join("\n")}

---

## 5. UX Friction Assessment & Usability Observations
${report.frictionPoints.map(f => `- ${f}`).join("\n")}

---

*Report autonomously generated by Orbital MCP Bridge on connected physical Android device.*
`;
}

// Direct CLI Execution (Enforces targetAppName parameter)
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const target = process.argv[2];
  if (!target) {
    console.error("Usage: node tests/tasks/autonomous_app_explorer.js <AppName>");
    process.exit(1);
  }
  exploreAndAnalyzeApp(target).then(() => process.exit(0)).catch(err => {
    console.error(err);
    process.exit(1);
  });
}
