package com.androidagent.aiagent.ai

import android.util.Log
import kotlinx.serialization.json.*

/**
 * Provides vision-analysis capabilities by delegating to [GemmaClient] with
 * vision-specific system prompts and parsing structured JSON responses.
 *
 * All public methods are `suspend` functions and must be called from a
 * coroutine scope.
 */
class VisionAnalyzer(private val gemmaClient: GemmaClient) {

    companion object {
        private const val TAG = "VisionAnalyzer"

        private val DESCRIBE_SYSTEM_PROMPT =
            """You are a visual analyzer for Android screens. Describe what you see, identify UI elements, their approximate positions, and any text visible. Be precise about locations.
            When listing elements, try to provide their approximate pixel coordinates (x, y) if possible, and a confidence score from 0.0 to 1.0.
            Format your response as a JSON object with two fields:
            - "description": a concise overall description of the screen
            - "elements": an array of objects, each with:
              - "description": what the element is
              - "x": approximate x coordinate (integer, null if unknown)
              - "y": approximate y coordinate (integer, null if unknown)
              - "confidence": float 0.0-1.0
            Return ONLY the JSON object, no extra text.""".trimIndent()

        private val FIND_TARGET_SYSTEM_PROMPT =
            """You are a visual target finder for Android screens.
You will be given a screenshot and a description of an element to find.
You MUST return ONLY a JSON object with exactly these fields:
- "found": boolean, whether the described element was found
- "x": float 0.0-1.0, the NORMALIZED horizontal position of the element's center (0 = left edge, 1 = right edge)
- "y": float 0.0-1.0, the NORMALIZED vertical position of the element's center (0 = top edge, 1 = bottom edge)
- "confidence": float 0.0-1.0, how confident you are in this detection
Return ONLY the JSON object, no markdown, no extra text.""".trimIndent()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Analyses a screenshot and returns a [VisionObservation] describing
     * visible UI elements, their positions and any readable text.
     *
     * If the model call or JSON parsing fails, an error observation is
     * returned instead of propagating the exception.
     *
     * @param screenshotBase64 Base-64 encoded PNG of the screen.
     * @param query             Optional query to focus the analysis (e.g. "What buttons are visible?").
     * @return A parsed [VisionObservation].
     */
    suspend fun analyzeScreen(
        screenshotBase64: String,
        query: String,
    ): VisionObservation {
        return try {
            val rawResponse = gemmaClient.generate(
                systemPrompt = DESCRIBE_SYSTEM_PROMPT,
                userMessage = query,
                tools = emptyList(),
                screenshotBase64 = screenshotBase64,
            )
            parseVisionObservation(rawResponse)
        } catch (t: Throwable) {
            Log.e(TAG, "analyzeScreen failed", t)
            VisionObservation(
                description = "Vision analysis failed: ${t.localizedMessage}",
                elements = emptyList(),
                rawResponse = "",
            )
        }
    }

    /**
     * Asks the model to locate a specific visual element on screen and
     * returns pixel coordinates.
     *
     * If the model call or JSON parsing fails, a "not found" result is
     * returned instead of propagating the exception.
     *
     * @param screenshotBase64 Base-64 encoded PNG of the screen.
     * @param description      Human-readable description of the element to locate.
     * @param screenWidth      Width of the screen in pixels (used to denormalize x).
     * @param screenHeight     Height of the screen in pixels (used to denormalize y).
     * @return A [VisualTargetResult] with pixel coordinates if found.
     */
    suspend fun findVisualTarget(
        screenshotBase64: String,
        description: String,
        screenWidth: Int,
        screenHeight: Int,
    ): VisualTargetResult {
        return try {
            val rawResponse = gemmaClient.generate(
                systemPrompt = FIND_TARGET_SYSTEM_PROMPT,
                userMessage = "Find this element on the screen: $description",
                tools = emptyList(),
                screenshotBase64 = screenshotBase64,
            )
            parseVisualTargetResult(rawResponse, screenWidth, screenHeight)
        } catch (t: Throwable) {
            Log.e(TAG, "findVisualTarget failed", t)
            VisualTargetResult(
                found = false,
                description = "Visual target search failed: ${t.localizedMessage}",
            )
        }
    }

    // ── Parsing ─────────────────────────────────────────────────────────────

    private fun parseVisionObservation(raw: String): VisionObservation {
        val cleaned = stripMarkdownJson(raw)
        val obj = try {
            json.parseToJsonElement(cleaned).jsonObject
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse vision observation JSON, returning raw", e)
            return VisionObservation(
                description = raw,
                elements = emptyList(),
                rawResponse = raw,
            )
        }

        val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val elements = obj["elements"]?.jsonArray?.mapNotNull { element ->
            runCatching {
                val elemObj = element.jsonObject
                VisualElement(
                    description = elemObj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                    x = elemObj["x"]?.jsonPrimitive?.intOrNull,
                    y = elemObj["y"]?.jsonPrimitive?.intOrNull,
                    confidence = elemObj["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f,
                )
            }.getOrNull()
        } ?: emptyList()

        return VisionObservation(
            description = description,
            elements = elements,
            rawResponse = raw,
        )
    }

    private fun parseVisualTargetResult(
        raw: String,
        screenWidth: Int,
        screenHeight: Int,
    ): VisualTargetResult {
        val cleaned = stripMarkdownJson(raw)
        val obj = try {
            json.parseToJsonElement(cleaned).jsonObject
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse visual target JSON, returning not-found", e)
            return VisualTargetResult(
                found = false,
                description = "Failed to parse model response",
            )
        }

        val found = obj["found"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!found) {
            return VisualTargetResult(
                found = false,
                description = obj["description"]?.jsonPrimitive?.contentOrNull
                    ?: "Element not found",
            )
        }

        val normalizedX = obj["x"]?.jsonPrimitive?.floatOrNull ?: 0.5f
        val normalizedY = obj["y"]?.jsonPrimitive?.floatOrNull ?: 0.5f
        val confidence = obj["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f

        val pixelX = (normalizedX * screenWidth).toInt().coerceIn(0, screenWidth)
        val pixelY = (normalizedY * screenHeight).toInt().coerceIn(0, screenHeight)

        return VisualTargetResult(
            found = true,
            x = pixelX,
            y = pixelY,
            confidence = confidence,
            description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    /**
     * Strips markdown code fences (```json ... ```) from the model output,
     * if present, so the remaining string can be parsed as JSON.
     */
    private fun stripMarkdownJson(raw: String): String {
        val trimmed = raw.trim()
        // Remove ```json ... ``` or ``` ... ``` wrapping
        val codeBlockRegex = Regex("""^```(?:json)?\s*\n?(.*?)\n?\s*```$""", RegexOption.DOT_MATCHES_ALL)
        return codeBlockRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed
    }

    // ── Data classes ────────────────────────────────────────────────────────

    /**
     * Result of a full-screen visual analysis.
     *
     * @property description  A human-readable summary of the screen content.
     * @property elements     Individual UI elements detected on the screen.
     * @property rawResponse  The unmodified model output.
     */
    data class VisionObservation(
        val description: String,
        val elements: List<VisualElement>,
        val rawResponse: String,
    )

    /**
     * A single UI element detected during vision analysis.
     *
     * @property description What the element is (e.g. "Submit button").
     * @property x           Approximate horizontal position in pixels, or null if unknown.
     * @property y           Approximate vertical position in pixels, or null if unknown.
     * @property confidence  Detection confidence from 0.0 (guess) to 1.0 (certain).
     */
    data class VisualElement(
        val description: String,
        val x: Int? = null,
        val y: Int? = null,
        val confidence: Float,
    )

    /**
     * Result of a targeted visual search for a specific element.
     *
     * @property found       Whether the element was located on screen.
     * @property x           Horizontal center of the element in pixels (null if not found).
     * @property y           Vertical center of the element in pixels (null if not found).
     * @property confidence  Detection confidence from 0.0 to 1.0.
     * @property description Additional context about the result.
     */
    data class VisualTargetResult(
        val found: Boolean,
        val x: Int? = null,
        val y: Int? = null,
        val confidence: Float = 0f,
        val description: String = "",
    )
}
