# Deep Technical Plan: OpenJEV Fast "System 1" Decision Engine (Kotlin Native)

## 1. Executive Summary & Source Analysis
- **Source Repository**: [`razorback16/openjev`](https://github.com/razorback16/openjev) (implementing TypeSafe Jev System 1 Decision Protocol).
- **Core Mechanism**: Unlike autoregressive LLMs that sequentially generate text tokens (high latency: 800ms - 3000ms), System 1 decision models evaluate fixed logit probability distributions in a single forward pass (<50ms - 150ms).
- **Orbital Problem Solved**:
  1. Accessibility tree node scoring (picking the single correct button among 100+ UI nodes).
  2. Post-action visual / hierarchy verification (confirming if a tap or swipe changed the screen as expected).
  3. Real-time safety gating (preventing destructive OS actions).
  4. Zero hardcoding: Decision contexts and candidate options are constructed 100% dynamically from the live Android screen hierarchy and user prompt.

---

## 2. Decision Theory & Mathematical Formulation

### 2.1 Binary Decision Logit Evaluation
Given a context $C$ (screen state + task) and a proposition $Q$, the engine extracts unnormalized logits for affirmative ($z_{yes}$) and negative ($z_{no}$) tokens:

$$P(Yes) = \sigma(z_{yes} - z_{no}) = \frac{1}{1 + e^{-(z_{yes} - z_{no})}}$$

A binary decision is accepted if $P(Yes) \ge \tau_{threshold}$ (default $\tau = 0.70$).

### 2.2 Categorical / Choice Selection
Given $K$ candidate UI elements $\{e_1, e_2, \dots, e_K\}$, the score for candidate $i$ is calculated via Softmax over candidate logits:

$$P(e_i | C) = \frac{e^{z_i / T}}{\sum_{j=1}^K e^{z_j / T}}$$

where $T$ is temperature calibration (default $T = 0.5$ for sharpened peak selection).

---

## 3. Kotlin Component Architecture (`com.orbital.decision.jev`)

### 3.1 Package Structure
```
app/src/main/java/com/orbital/decision/jev/
├── JevDecisionContracts.kt      # Core interfaces, generic decision models, confidence types
├── JevDecisionEngine.kt         # Master interface & router (Local LiteRT vs Remote OpenJEV)
├── JevRemoteClient.kt          # High-performance Ktor/OkHttp client for OpenJEV server
├── JevLocalLiteRtEngine.kt     # On-device fallback using LiteRT / MediaPipe embeddings
├── JevPromptFormatter.kt       # Zero-hardcoding dynamic prompt builder from AccessibilityNodeInfo
└── AccessibilityNodeRanker.kt   # Specialised candidate ranker for Android UI automation
```

---

## 4. Complete Kotlin Implementation Blueprint

### 4.1 Data Contracts (`JevDecisionContracts.kt`)

```kotlin
package com.orbital.decision.jev

import kotlinx.serialization.Serializable

@Serializable
enum class DecisionKind {
    BINARY,
    CHOICE,
    SCORE,
    CLASSIFICATION
}

@Serializable
data class DecisionMetadata(
    val latencyMs: Long,
    val modelIdentifier: String,
    val tokenUsage: Int = 0,
    val isLocalExecution: Boolean
)

sealed interface JevDecision<out T> {
    val value: T
    val confidence: Float // 0.0f to 1.0f
    val metadata: DecisionMetadata
    val reasoningHint: String?
}

@Serializable
data class BooleanDecision(
    override val value: Boolean,
    override val confidence: Float,
    override val metadata: DecisionMetadata,
    override val reasoningHint: String? = null
) : JevDecision<Boolean>

data class ChoiceDecision<T>(
    override val value: T,
    val selectedIndex: Int,
    val distribution: Map<T, Float>,
    override val confidence: Float,
    override val metadata: DecisionMetadata,
    override val reasoningHint: String? = null
) : JevDecision<T>

@Serializable
data class ScoreDecision(
    override val value: Float, // Normalized 0.0f to 1.0f
    override val confidence: Float,
    override val metadata: DecisionMetadata,
    override val reasoningHint: String? = null
) : JevDecision<Float>
```

### 4.2 Jev Engine Interface (`JevDecisionEngine.kt`)

```kotlin
package com.orbital.decision.jev

interface JevDecisionEngine {
    /**
     * Evaluates a binary proposition under a confidence threshold.
     */
    suspend fun decideBinary(
        context: String,
        question: String,
        confidenceThreshold: Float = 0.70f
    ): BooleanDecision

    /**
     * Evaluates candidate options and picks the top probability candidate dynamically.
     */
    suspend fun <T : Any> decideChoice(
        context: String,
        prompt: String,
        candidates: List<T>,
        keyExtractor: (T) -> String,
        confidenceThreshold: Float = 0.50f
    ): ChoiceDecision<T>

    /**
     * Produces a continuous confidence score (0.0 to 1.0) for safety or relevance.
     */
    suspend fun scoreRelevance(
        context: String,
        candidate: String
    ): ScoreDecision
}
```

### 4.3 OpenJEV Remote HTTP Client (`JevRemoteClient.kt`)

```kotlin
package com.orbital.decision.jev

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class OpenJevRequest(
    val context: String,
    val query: String,
    val type: String, // "binary", "choice", "score"
    val options: List<String>? = null,
    val temperature: Float = 0.5f
)

@Serializable
data class OpenJevResponse(
    val result: String,
    val confidence: Float,
    val scores: Map<String, Float>? = null,
    val latencyMs: Long,
    val model: String
)

class JevRemoteClient(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : JevDecisionEngine {

    override suspend fun decideBinary(
        context: String,
        question: String,
        confidenceThreshold: Float
    ): BooleanDecision = withContext(Dispatchers.IO) {
        val payload = OpenJevRequest(
            context = context,
            query = question,
            type = "binary"
        )
        val response = executeRequest(payload)
        val isAffirmative = response.result.equals("yes", ignoreCase = true) ||
                response.result.equals("true", ignoreCase = true)

        BooleanDecision(
            value = isAffirmative && response.confidence >= confidenceThreshold,
            confidence = response.confidence,
            metadata = DecisionMetadata(
                latencyMs = response.latencyMs,
                modelIdentifier = response.model,
                isLocalExecution = false
            ),
            reasoningHint = "Evaluated via OpenJev server"
        )
    }

    override suspend fun <T : Any> decideChoice(
        context: String,
        prompt: String,
        candidates: List<T>,
        keyExtractor: (T) -> String,
        confidenceThreshold: Float
    ): ChoiceDecision<T> = withContext(Dispatchers.IO) {
        require(candidates.isNotEmpty()) { "Candidate list cannot be empty" }
        val optionStrings = candidates.map(keyExtractor)
        val payload = OpenJevRequest(
            context = context,
            query = prompt,
            type = "choice",
            options = optionStrings
        )
        val response = executeRequest(payload)
        val selectedIdx = optionStrings.indexOf(response.result).takeIf { it >= 0 } ?: 0
        val distribution = candidates.associateWith { item ->
            response.scores?.get(keyExtractor(item)) ?: 0.0f
        }

        ChoiceDecision(
            value = candidates[selectedIdx],
            selectedIndex = selectedIdx,
            distribution = distribution,
            confidence = response.confidence,
            metadata = DecisionMetadata(
                latencyMs = response.latencyMs,
                modelIdentifier = response.model,
                isLocalExecution = false
            )
        )
    }

    override suspend fun scoreRelevance(context: String, candidate: String): ScoreDecision = withContext(Dispatchers.IO) {
        val payload = OpenJevRequest(
            context = context,
            query = candidate,
            type = "score"
        )
        val response = executeRequest(payload)
        ScoreDecision(
            value = response.confidence,
            confidence = response.confidence,
            metadata = DecisionMetadata(
                latencyMs = response.latencyMs,
                modelIdentifier = response.model,
                isLocalExecution = false
            )
        )
    }

    private fun executeRequest(payload: OpenJevRequest): OpenJevResponse {
        val body = json.encodeToString(OpenJevRequest.serializer(), payload)
            .toRequestBody("application/json".toMediaType())
        val reqBuilder = Request.Builder()
            .url("$baseUrl/v1/systemone")
            .post(body)
        apiKey?.let { reqBuilder.addHeader("Authorization", "Bearer $it") }

        httpClient.newCall(reqBuilder.build()).execute().use { res ->
            if (!res.isSuccessful) {
                throw IllegalStateException("OpenJEV request failed with HTTP code: ${res.code}")
            }
            val resBody = res.body?.string().orEmpty()
            return json.decodeFromString(OpenJevResponse.serializer(), resBody)
        }
    }
}
```

### 4.4 Dynamic Accessibility Node Ranker (`AccessibilityNodeRanker.kt`)

```kotlin
package com.orbital.decision.jev

import android.view.accessibility.AccessibilityNodeInfo

data class ScoredUiNode(
    val node: AccessibilityNodeInfo,
    val description: String,
    val score: Float,
    val boundsString: String
)

class AccessibilityNodeRanker(
    private val jevEngine: JevDecisionEngine
) {
    suspend fun rankCandidatesForIntent(
        userIntent: String,
        screenContext: String,
        rawNodes: List<AccessibilityNodeInfo>
    ): List<ScoredUiNode> {
        if (rawNodes.isEmpty()) return emptyList()

        // 1. Extract dynamic readable descriptions without hardcoding
        val candidatesWithDesc = rawNodes.map { node ->
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            val viewId = node.viewIdResourceName.orEmpty()
            val combined = listOf(text, desc, viewId).filter { it.isNotBlank() }.joinToString(" | ")
            val label = if (combined.isNotBlank()) combined else "Unnamed ${node.className}"
            Pair(node, label)
        }

        // 2. Perform fast decision choice
        val choice = jevEngine.decideChoice(
            context = "Active Screen: $screenContext\nGoal: $userIntent",
            prompt = "Select the UI element that directly executes the goal",
            candidates = candidatesWithDesc,
            keyExtractor = { it.second }
        )

        return candidatesWithDesc.map { (node, desc) ->
            val score = choice.distribution[Pair(node, desc)] ?: if (desc == choice.value.second) choice.confidence else 0.0f
            ScoredUiNode(
                node = node,
                description = desc,
                score = score,
                boundsString = node.run { val r = android.graphics.Rect(); getBoundsInScreen(r); r.toShortString() }
            )
        }.sortedByDescending { it.score }
    }
}
```

---

## 5. Integration Points in Orbital
1. **`AccessibilityAutomationService.kt`**: Replace brute-force text matching with `AccessibilityNodeRanker.rankCandidatesForIntent()`.
2. **`ChatViewModel.kt`**: Run `decideBinary()` before invoking heavy LLM reasoning to check if the current screen already satisfies the request.
3. **Safety Interceptor**: If `scoreRelevance("Destructive operation check", screenText)` detects high risk ($> 0.85$), pause execution and request user approval.
