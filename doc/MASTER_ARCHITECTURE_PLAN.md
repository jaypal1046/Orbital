# Master Architecture & Technical Integration Blueprint for Orbital

## 1. System Topology & Unified Dataflow

```mermaid
flowchart TB
    subgraph UI_Layer [1. Android UI & Input Layer]
        UserChat[User Natural Language Prompt]
        AttachPicker[AttachmentPickerSheet: PDFs / Docs / Images]
    end

    subgraph Memory_Layer [2. Hindsight Memory Subsystem]
        Hindsight[HindsightMemoryEngine: Retain - Recall - Reflect]
        RoomDB[(Room SQLite DB: MemoryEntity & Vector Embeddings)]
        Hindsight <--> RoomDB
    end

    subgraph Doc_Layer [3. Document Intelligence Pipeline]
        DocPipeline[HybridDocumentPipeline]
        LocalPDF[Local DocumentReader]
        MinerU[MinerU Remote Parser]
        DocPipeline --> LocalPDF
        DocPipeline --> MinerU
    end

    subgraph Reasoning_Layer [4. Core LLM Reasoning Engine]
        LLM[Gemini / Claude Hybrid Planner]
    end

    subgraph Decision_Layer [5. OpenJEV System 1 Engine]
        OpenJEV[JevDecisionEngine: Non-Autoregressive Evaluator]
        NodeRanker[AccessibilityNodeRanker]
        OpenJEV --> NodeRanker
    end

    subgraph Supervisor_Layer [6. Foreman Supervision & Watchdog]
        Foreman[ForemanSupervisor & Anti-Loop Watchdog]
        StateHash[Screen State Hash Vector Engine]
        Foreman --> StateHash
    end

    subgraph OS_Execution_Layer [7. Android Device Automation]
        Accessibility[AccessibilityAutomationService & Gestures]
    end

    %% Wiring
    UserChat --> Hindsight
    Hindsight -- Recalled Habits & Context --> LLM
    AttachPicker --> DocPipeline
    DocPipeline -- Clean Markdown & Tables --> LLM
    LLM -- Multi-Step Execution Plan --> Foreman
    Foreman -- Step Candidate Scoring --> OpenJEV
    OpenJEV -- High-Confidence Target Node --> Accessibility
    Accessibility -- Pre/Post Screen Delta --> StateHash
    StateHash -- Loop & Progress Verdict --> Foreman
    Foreman -- Post-Task Reflection & Success --> Hindsight
```

---

## 2. Comprehensive Repository Decision Rationale

| Repository | Focus | Verdict | Justification & Architectural Role |
| :--- | :--- | :--- | :--- |
| **[`razorback16/openjev`](https://github.com/razorback16/openjev)** | Fast "System 1" Decision Engine | **ADOPT (Core)** | Replaces slow full-LLM text generations with sub-100ms non-autoregressive logit scoring for live UI node selection and post-tap verification. |
| **[`thruwire/foreman`](https://github.com/thruwire/foreman)** | Deterministic Agent Supervisor | **ADOPT (Core)** | Prevents loops, deadlocks, and repetitive gestures; calculates screen state hashes and provides deterministic recovery steering directives. |
| **[`vectorize-io/hindsight`](https://github.com/vectorize-io/hindsight)** | Semantic Long-term Memory | **ADOPT (Core)** | Implements a 4-way hybrid retrieval model (Vector, Keyword, Graph, Temporal) for habit learning and app quirk recall with zero hardcoding. |
| **[`opendatalab/MinerU`](https://github.com/opendatalab/MinerU)** | Complex Document Parsing | **ADOPT (Hybrid)** | Upgrades `DocumentReader.kt` and `AttachmentPickerSheet.kt` to extract structured Markdown tables from multi-column PDFs. |
| **[`hydra-db/open-glean`](https://github.com/hydra-db/open-glean)** | Enterprise Workplace Search | **SKIP** | Built for corporate multi-user SaaS (Notion, Slack, Jira); too heavyweight for on-device personal assistant. |
| **[`mvschwarz/openrig`](https://github.com/mvschwarz/openrig)** | CLI/tmux Multi-agent Swarm | **SKIP** | Geared towards terminal-based coding agents in tmux sessions; redundant with Foreman. |
| **[`MxCorpIn/Repolyze`](https://github.com/MxCorpIn/Repolyze)** | Git Contributor Analytics | **NOT NEEDED** | Out of scope (developer Git commit heatmaps). |
| **[`debpalash/VoiceStudio`](https://github.com/debpalash/VoiceStudio)** | Local TTS / STT Studio | **DEFERRED** | Preserved for upcoming dedicated voice interaction milestone. |

---

## 3. Dedicated Plan Index & File Links

1. ⚡ **[PLAN_OPENJEV_DECISION_ENGINE.md](file:///c:/Jay/dev/Orbital/doc/PLAN_OPENJEV_DECISION_ENGINE.md)**
   * Mathematical logit formulas ($P(Yes)$, Softmax candidate distributions).
   * Complete Kotlin classes (`BooleanDecision`, `ChoiceDecision<T>`, `JevRemoteClient`, `AccessibilityNodeRanker`).
   * Dynamic, zero-hardcoded UI node ranking from `AccessibilityNodeInfo`.

2. 🛡️ **[PLAN_FOREMAN_SUPERVISOR.md](file:///c:/Jay/dev/Orbital/doc/PLAN_FOREMAN_SUPERVISOR.md)**
   * State hash algorithm $\mathcal{H}(S_t)$ over visible accessibility nodes.
   * Watchdog stall ($N=2$) and 2-state oscillation ($A \rightarrow B \rightarrow A \rightarrow B$) detectors.
   * Complete Kotlin state machine (`SteeringDirective`, `ForemanWatchdog`, `ForemanSupervisor`).

3. 🧠 **[PLAN_HINDSIGHT_AGENT_MEMORY.md](file:///c:/Jay/dev/Orbital/doc/PLAN_HINDSIGHT_AGENT_MEMORY.md)**
   * 4-way hybrid scoring equation ($S_{hybrid} = w_v \cdot \text{Sim}_v + w_k \cdot \text{BM25} + w_t \cdot e^{-\lambda \Delta t} + w_r \cdot \text{Conf}$).
   * Full Room schema (`MemoryEntity`, `HindsightDao`, vector cosine calculation).
   * Pre-turn habit injection and post-turn reflection into `ChatViewModel.kt`.

4. 📄 **[PLAN_MINERU_DEEP_PARSER.md](file:///c:/Jay/dev/Orbital/doc/PLAN_MINERU_DEEP_PARSER.md)**
   * Document layout segmentation and reading order reconstructor.
   * Table-to-Markdown matrix generator (`TableMarkdownFormatter`).
   * Hybrid routing between local Android PDF reader and deep remote parser.

---

## 4. Phased Implementation Sequence

```mermaid
gantt
    title Orbital Engine Integration Timeline
    dateFormat  YYYY-MM-DD
    section Phase 1: Decisions & Scoring
    OpenJEV Data Contracts & Models       :p1_1, 2026-10-01, 2d
    OpenJEV Remote & Local Evaluator      :p1_2, after p1_1, 3d
    Accessibility Node Ranker             :p1_3, after p1_2, 2d
    section Phase 2: Supervisor & Watchdog
    State Hash & Foreman Watchdog         :p2_1, after p1_3, 2d
    Foreman Supervisor Loop               :p2_2, after p2_1, 3d
    section Phase 3: Semantic Memory
    Room Schema & Vector Math             :p3_1, after p2_2, 3d
    Hindsight Retain-Recall-Reflect Engine:p3_2, after p3_1, 3d
    section Phase 4: Document Pipeline
    Table Markdown Formatter & Pipeline   :p4_1, after p3_2, 3d
    Integration with Attachment Picker    :p4_2, after p4_1, 2d
```
