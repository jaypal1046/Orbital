# Project Guidelines: Orbital

## Zero Hardcoding Rule (Strict)
- **Unless and until explicitly asked by the user, DO NOT hardcode anything.**
- Never hardcode mock values, sample app names (e.g. "Where is My Train"), mock station codes, test strings, fallback query shortcuts, or synthetic action traces.
- All actions, app resolutions, and execution steps must remain 100% dynamic, generic, and derived purely from user input, dynamic device package managers, or live runtime state.

## No Shizuku Rule (Strict)
- **DO NOT use Shizuku.** Shizuku dependencies, providers, and bindings are prohibited.
- All privileged shell interactions (touch injection, key events, screen dumps) must run exclusively through our **native privileged local loopback daemon / shell bridge socket (`127.0.0.1:19876`)**.

## Zero AccessibilityService Rule (Strict)
- Keep the codebase 100% free of `AccessibilityService`.
- Guarantees full Google Play Store compliance and complete safety with banking, UPI, and financial security apps.

## Script-Based On-Device Execution
- Autonomous tasks must synthesize self-contained shell execution scripts (`.sh`) handled cleanly on our side on the device.
- Scripts execute directly through the privileged shell daemon and capture structured execution results, stdout, stderr, and UI state.

## Financial Data & Document Mapping Pipeline
- Extract and compute core financial metrics: **PAT (Profit After Tax)**, **EBITDA / EBITA**, **Sales / Turnover**, **P&L (Profit & Loss)**, and **Balance Sheet** with **90%+ mathematical accuracy**.
- **Images**: Handled primarily on the phone side (on-device OCR & preprocessing) before asking AI.
- **Documents**: Structured document mapping for P&L and Balance Sheet with cross-verification of accounting equations (Assets = Liabilities + Equity, EBITDA = EBIT + D&A).

## Live Task Execution & Progress Visibility Rule (Strict)
- For every execution or automation step, always provide clear, transparent, step-by-step live tracking of what task is currently running (e.g. `> Task :<task_name> [IN_PROGRESS / SUCCESS / FAILED]`).
- Show the synthesized script, execution parameters, timing, and real-time execution outputs so the user and system have full transparency into what is happening on our side at every stage.

## Isolated & Parallel Processing Rule (Strict)
- **Zero Main Thread Choking**: File decompression (Office .xlsx, .docx, .pptx), PDF text extraction, OCR preprocessing, RAG chunking, and Markdown block parsing MUST run isolated from the Main/UI thread on `Dispatchers.IO` and `Dispatchers.Default`.
- **Parallel Chunk Execution**: Multi-chunk document scoring and table parsing must execute concurrently across CPU cores using parallel coroutine workers (`async` / `awaitAll`).
- **Clean Chat Bubble Separation**: Chat bubbles rendered in Compose UI must ONLY contain clean user prompts. Never dump raw binary files or multi-thousand character RAG context strings into UI message bubbles; route augmented payloads exclusively to the backend LLM execution stream.

## Email Smart Staging Rule (Co-Pilot Mode)
- **Zero Notification Permissions**: Do not use `NotificationListenerService`.
- **Privileged Screen Context**: Inspect active email threads directly on screen via the native shell loopback daemon (`127.0.0.1:19876`).
- **Co-Pilot In-App Staging**: Navigate to the reply field, paste/type the synthesized draft cleanly into the message body, and display a non-blocking floating Smart Staging HUD (`[ Send Now ]`, `[ Refine ]`, `[ Dismiss ]`) so the user retains 100% control and zero risk of accidental sending.


