package com.androidagent.aiagent.tools

import android.util.Log
import com.androidagent.aiagent.accessibility.GestureController
import com.androidagent.aiagent.accessibility.AccessibilityObserver
import com.androidagent.aiagent.ai.VisionAnalyzer
import com.androidagent.aiagent.safety.SafetyCheckResult
import com.androidagent.aiagent.safety.SafetyController
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Handler interface – implemented by each concrete tool
// ============================================================================

/**
 * Handles the actual execution logic for a single named tool.
 *
 * Each concrete tool (tap, type, scroll, etc.) provides an implementation
 * of this interface.  The [ToolExecutor] dispatches to the correct handler
 * at runtime.
 */
interface ToolHandler {
    /**
     * Execute the tool with the given arguments.
     *
     * @param args A [JsonObject] containing the tool's input parameters.
     * @return A [ToolResult] describing the outcome.
     */
    suspend fun execute(args: JsonObject): ToolResult
}

// ============================================================================
// ToolExecutor – central dispatcher with safety + alias resolution
// ============================================================================

/**
 * Central dispatcher that routes tool calls to the appropriate [ToolHandler]
 * after performing alias resolution and safety checks.
 *
 * Execution flow:
 * 1. **Alias resolution** – if the requested name is not registered but
 *    matches a known alias, transparently redirect to the canonical name.
 * 2. **Handler lookup** – find the [ToolHandler] for the (possibly
 *    resolved) tool name.
 * 3. **Safety check** – delegate to the [SafetyController] for policy
 *    evaluation.
 * 4. **Execution** – invoke the handler (or return a sentinel result if
 *    confirmation/blocked).
 */
class ToolExecutor(
    private val accessibilityObserver: AccessibilityObserver,
    private val gestureController: GestureController,
    private val visionAnalyzer: VisionAnalyzer,
    private val safetyController: SafetyController,
    private val toolHandlers: Map<String, ToolHandler>
) {

    companion object {
        private const val TAG = "ToolExecutor"

        /** Error code: tool call requires user confirmation. */
        const val ERROR_CONFIRMATION_REQUIRED = "SAFETY_CONFIRMATION_REQUIRED"

        /** Error code: tool call is blocked by safety policy. */
        const val ERROR_BLOCKED = "SAFETY_BLOCKED"

        /** Error code: no handler registered for the requested tool. */
        const val ERROR_UNKNOWN_TOOL = "UNKNOWN_TOOL"

        /** Maximum Levenshtein distance for fuzzy tool-name matching. */
        private const val MAX_FUZZY_DISTANCE = 3

        /**
         * Common tool-name mistakes the model makes, mapped to the
         * correct canonical name.
         *
         * The executor **auto-corrects** aliases at execution time, so
         * the model's response succeeds even when it uses a wrong name.
         * However, a warning is logged and the [AliasResolution] is
         * returned so the runtime can inform the model.
         */
        private val TOOL_ALIASES: Map<String, String> = mapOf(
            // Text input
            "android.set_text"      to "android.type_text",
            "android.enter_text"    to "android.type_text",
            "android.type"          to "android.type_text",
            "android.input_text"    to "android.type_text",
            "android.clear"         to "android.clear_text",
            "android.erase_text"    to "android.clear_text",
            "android.delete_text"   to "android.clear_text",
            // Tapping
            "android.tap"           to "android.click",
            "android.long_press"    to "android.long_click",
            "android.long_tap"      to "android.long_click",
            // Navigation
            "android.press_back"    to "android.back",
            "android.go_back"       to "android.back",
            "android.navigate_back" to "android.back",
            "android.press_home"    to "android.home",
            "android.go_home"       to "android.home",
            "android.show_recents"  to "android.recents",
            "android.recent_apps"   to "android.recents",
            // Search / observe
            "android.search"        to "android.find",
            "android.look_for"      to "android.find",
            "android.inspect"       to "android.inspect_screen",
            // Keys / time
            "android.sleep"         to "android.wait",
            "android.delay"         to "android.wait",
            "android.key"           to "android.press_key",
            "android.send_key"      to "android.press_key",
            // App launch
            "android.open_app"      to "android.launch_app",
            "android.start_app"     to "android.launch_app",
            // Vision
            "android.analyze"       to "vision.analyze_screen",
            "android.find_visual"   to "vision.find_visual_target",
            "android.visual_find"   to "vision.find_visual_target",
            // Agent
            "agent.ask"             to "agent.ask_user",
            "agent.done"            to "agent.finish",
            // New gestures
            "android.double_tap"    to "android.double_click",
            "android.double_click_tap" to "android.double_click",
            "android.pinch"         to "android.pinch_zoom",
            "android.zoom"          to "android.pinch_zoom",
            "android.zoom_in"       to "android.pinch_zoom",
            "android.zoom_out"      to "android.pinch_zoom",
            "android.throw"         to "android.fling",
            "android.throw_up"      to "android.fling",
            "android.throw_down"    to "android.fling",
            // System
            "android.notifications"  to "android.open_notifications",
            "android.notification_shade" to "android.open_notifications",
            "android.quick_settings" to "android.open_quick_settings",
            "android.quick_panel"   to "android.open_quick_settings",
            "android.power"          to "android.power_menu",
            "android.shutdown"       to "android.power_menu",
            "android.lock"           to "android.lock_screen",
            "android.split"          to "android.split_screen",
            "android.volume_up"      to "android.volume",
            "android.volume_down"    to "android.volume",
            "android.mute"           to "android.volume",
            "android.unmute"         to "android.volume",
            // Text ops
            "android.select"         to "android.select_all",
            "android.copy"           to "android.copy_text",
            "android.paste"          to "android.paste_text",
            "android.clipboard"      to "android.set_clipboard",
            "android.set_clip"       to "android.set_clipboard",
            // Intents
            "android.open_browser"  to "android.open_url",
            "android.browse"         to "android.open_url",
            "android.call"           to "android.make_call",
            "android.dial"           to "android.make_call",
            "android.sms"            to "android.send_sms",
            "android.text_message"   to "android.send_sms",
            "android.send_message"  to "android.send_sms",
            "android.send"           to "android.share",
            "android.share_text"     to "android.share"
        )
    }

    // -------------------------------------------------------------------
    // Result of alias resolution
    // -------------------------------------------------------------------

    /**
     * Describes whether a tool name was auto-corrected via alias or
     * fuzzy matching.
     */
    data class AliasResolution(
        /** The original name the model supplied. */
        val originalName: String,
        /** The resolved canonical name (may equal [originalName]). */
        val resolvedName: String,
        /** How the resolution was performed. */
        val method: ResolutionMethod
    ) {
        enum class ResolutionMethod {
            /** No correction needed – exact match. */
            EXACT,
            /** Matched via the [TOOL_ALIASES] table. */
            ALIAS,
            /** Matched via Levenshtein distance or suffix. */
            FUZZY
        }

        /** True if the name was changed from the original. */
        val wasCorrected: Boolean get() = originalName != resolvedName
    }

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    /**
     * Execute a tool call end-to-end, including alias resolution and
     * safety evaluation.
     *
     * @param toolName The name of the tool to invoke (may be an alias).
     * @param args The JSON arguments supplied by the AI model.
     * @param observationId Optional observation ID for tracing.
     * @return A [ToolResult] describing the outcome.
     */
    suspend fun execute(
        toolName: String,
        args: JsonObject,
        observationId: String? = null
    ): ToolResult {
        // 1. Resolve aliases / fuzzy-match
        val resolution = resolveToolName(toolName)
        val canonicalName = resolution.resolvedName

        if (resolution.wasCorrected) {
            Log.i(TAG, "Resolved tool '$toolName' → '$canonicalName' (${resolution.method})")
        }

        // 2. Look up handler
        val handler = toolHandlers[canonicalName]
        if (handler == null) {
            Log.w(TAG, "No handler registered for tool: $canonicalName (original: $toolName)")
            return ToolResult(
                success = false,
                toolName = canonicalName,
                error = ToolError(
                    code = ERROR_UNKNOWN_TOOL,
                    message = "No handler registered for tool '$canonicalName'"
                ),
                observationRequired = false
            )
        }

        // 3. Safety check
        val safetyResult = runCatching { safetyController.checkToolCall(canonicalName, args) }
            .onFailure { e ->
                Log.e(TAG, "SafetyController threw during checkToolCall", e)
            }
            .getOrNull()

        if (safetyResult == null) {
            return ToolResult(
                success = false,
                toolName = canonicalName,
                error = ToolError(
                    code = "SAFETY_CHECK_ERROR",
                    message = "Safety check failed unexpectedly"
                ),
                observationRequired = false
            )
        }

        // 4. Handle safety verdict
        when (safetyResult) {
            SafetyCheckResult.BLOCKED -> {
                val reason = safetyController.lastReason ?: "Blocked by safety policy"
                Log.w(TAG, "Tool '$canonicalName' blocked: $reason")
                return ToolResult(
                    success = false,
                    toolName = canonicalName,
                    error = ToolError(code = ERROR_BLOCKED, message = reason),
                    observationRequired = false
                )
            }

            SafetyCheckResult.REQUIRES_CONFIRMATION -> {
                val reason = safetyController.lastReason
                    ?: "This action requires user confirmation"
                Log.i(TAG, "Tool '$canonicalName' requires confirmation: $reason")
                return ToolResult(
                    success = false,
                    toolName = canonicalName,
                    error = ToolError(code = ERROR_CONFIRMATION_REQUIRED, message = reason),
                    observationRequired = false
                )
            }

            SafetyCheckResult.ALLOWED -> { /* fall through */ }
        }

        // 5. Execute the handler
        return try {
            val startTime = System.currentTimeMillis()
            val result = handler.execute(args)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Tool '$canonicalName' executed in ${elapsed}ms, success=${result.success}")
            result
        } catch (e: CancellationException) {
            Log.w(TAG, "Tool '$canonicalName' execution cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool '$canonicalName' execution failed", e)
            ToolResult(
                success = false,
                toolName = canonicalName,
                error = ToolError(
                    code = "EXECUTION_ERROR",
                    message = e.message ?: "Unknown error during tool execution"
                ),
                observationRequired = true
            )
        }
    }

    /**
     * Resolve a potentially-incorrect tool name to a canonical name.
     *
     * Resolution order:
     * 1. Exact match → no correction needed.
     * 2. Alias lookup via [TOOL_ALIASES].
     * 3. Levenshtein distance ≤ [MAX_FUZZY_DISTANCE].
     * 4. Suffix match (e.g. `"click"` → `"android.click"`).
     *
     * @return An [AliasResolution] describing the result.
     */
    fun resolveToolName(requestedName: String): AliasResolution {
        val normalised = requestedName.trim().lowercase()

        // 1. Exact match
        if (normalised in toolHandlers) {
            return AliasResolution(requestedName, normalised, AliasResolution.ResolutionMethod.EXACT)
        }

        // 2. Alias lookup
        TOOL_ALIASES[normalised]?.let { canonical ->
            if (canonical in toolHandlers) {
                return AliasResolution(
                    requestedName, canonical, AliasResolution.ResolutionMethod.ALIAS
                )
            }
        }

        // 3. Levenshtein distance
        var bestName: String? = null
        var bestDist = Int.MAX_VALUE
        for (registered in toolHandlers.keys) {
            val d = levenshtein(normalised, registered.lowercase())
            if (d < bestDist) {
                bestDist = d
                bestName = registered
            }
        }
        if (bestDist <= MAX_FUZZY_DISTANCE && bestName != null) {
            return AliasResolution(
                requestedName, bestName, AliasResolution.ResolutionMethod.FUZZY
            )
        }

        // 4. Suffix match
        val suffix = normalised.substringAfterLast(".", normalised)
        val suffixMatch = toolHandlers.keys.firstOrNull {
            it.substringAfterLast(".") == suffix
        }
        if (suffixMatch != null) {
            return AliasResolution(
                requestedName, suffixMatch, AliasResolution.ResolutionMethod.FUZZY
            )
        }

        // No resolution possible
        return AliasResolution(requestedName, normalised, AliasResolution.ResolutionMethod.EXACT)
    }

    /** Check whether a handler exists for the given name. */
    fun hasHandler(toolName: String): Boolean = toolHandlers.containsKey(toolName)

    /** Return the names of all registered tool handlers. */
    fun getRegisteredHandlerNames(): Set<String> = toolHandlers.keys

    // -------------------------------------------------------------------
    // Levenshtein distance
    // -------------------------------------------------------------------

    /** Simple O(n*m) Levenshtein distance with O(min(n,m)) space. */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val la = a.length
        val lb = b.length
        if (la == 0) return lb
        if (lb == 0) return la

        // Ensure we allocate the shorter array
        val (shorter, longer) = if (la <= lb) a to b else b to a
        val ls = shorter.length
        val ll = longer.length

        var prev = IntArray(ls + 1) { it }
        var curr = IntArray(ls + 1)

        for (i in 1..ll) {
            curr[0] = i
            for (j in 1..ls) {
                val cost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,      // deletion
                    curr[j - 1] + 1,  // insertion
                    prev[j - 1] + cost // substitution
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[ls]
    }
}
