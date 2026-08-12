package com.androidagent.aiagent.tools

import android.util.Log
import com.androidagent.aiagent.accessibility.GestureController
import com.androidagent.aiagent.accessibility.AccessibilityObserver
import com.androidagent.aiagent.ai.VisionAnalyzer
import com.androidagent.aiagent.safety.SafetyCheckResult
import com.androidagent.aiagent.safety.SafetyController
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * Handles the actual execution logic for a single named tool.
 *
 * Each concrete tool (tap, type, scroll, observe, etc.) provides an
 * implementation of this interface. The [ToolExecutor] dispatches to
 * the correct handler at runtime.
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

/**
 * Central dispatcher that routes tool calls to the appropriate [ToolHandler]
 * after performing safety checks.
 *
 * Execution flow:
 * 1. Look up the [ToolHandler] for the requested tool name.
 * 2. Delegate to the [SafetyController] for policy evaluation.
 * 3. If the call is allowed, invoke the handler.
 * 4. If the call requires confirmation, return a sentinel [ToolResult]
 *    with `success = false` and error code `"SAFETY_CONFIRMATION_REQUIRED"`.
 * 5. If the call is blocked, return a sentinel [ToolResult]
 *    with `success = false` and error code `"SAFETY_BLOCKED"`.
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

        /** Error code returned when a tool call requires user confirmation. */
        const val ERROR_CONFIRMATION_REQUIRED = "SAFETY_CONFIRMATION_REQUIRED"

        /** Error code returned when a tool call is blocked by safety policy. */
        const val ERROR_BLOCKED = "SAFETY_BLOCKED"

        /** Error code returned when the requested tool is not registered. */
        const val ERROR_UNKNOWN_TOOL = "UNKNOWN_TOOL"

        /**
         * Common tool-name mistakes the model makes, mapped to the correct
         * canonical name that is actually registered.
         */
        private val TOOL_ALIASES: Map<String, String> = mapOf(
            "android.set_text"       to "android.type_text",
            "android.enter_text"     to "android.type_text",
            "android.type"           to "android.type_text",
            "android.input_text"     to "android.type_text",
            "android.tap"            to "android.click",
            "android.press_back"     to "android.back",
            "android.go_back"        to "android.back",
            "android.navigate_back"  to "android.back",
            "android.long_press"     to "android.long_click",
            "android.long_tap"       to "android.long_click",
            "android.search"         to "android.find",
            "android.look_for"       to "android.find",
            "android.wait"           to "android.wait",
            "android.sleep"          to "android.wait",
            "android.delay"          to "android.wait",
            "android.clear"          to "android.clear_text",
            "android.erase_text"     to "android.clear_text",
            "android.delete_text"    to "android.clear_text",
            "android.press_home"     to "android.home",
            "android.go_home"        to "android.home",
            "android.show_recents"   to "android.recents",
            "android.recent_apps"    to "android.recents",
            "android.key"            to "android.press_key",
            "android.send_key"       to "android.press_key",
            "android.open_app"       to "android.launch_app",
            "android.start_app"      to "android.launch_app",
            "android.inspect"        to "android.inspect_screen",
            "android.analyze"        to "android.analyze_screen",
            "android.find_visual"    to "android.find_visual_target",
            "android.visual_find"    to "android.find_visual_target",
            "agent.ask"              to "agent.ask_user",
            "agent.done"             to "agent.finish"
        )
    }

    /**
     * Execute a tool call end-to-end.
     *
     * @param toolName The name of the tool to invoke.
     * @param args The JSON arguments supplied by the AI model.
     * @param observationId Optional observation ID for tracing/log correlation.
     * @return A [ToolResult] describing the outcome of the call.
     */
    suspend fun execute(
        toolName: String,
        args: JsonObject,
        observationId: String? = null
    ): ToolResult {
        Log.d(TAG, "execute($toolName) observationId=$observationId")

        // 1. Resolve handler
        val handler = toolHandlers[toolName]
        if (handler == null) {
            Log.w(TAG, "No handler registered for tool: $toolName")
            return ToolResult(
                success = false,
                toolName = toolName,
                error = ToolError(
                    code = ERROR_UNKNOWN_TOOL,
                    message = "No handler registered for tool '$toolName'"
                ),
                observationRequired = false
            )
        }

        // 2. Safety check
        val safetyResult = try {
            safetyController.checkToolCall(toolName, args)
        } catch (e: Exception) {
            Log.e(TAG, "SafetyController threw during checkToolCall", e)
            return ToolResult(
                success = false,
                toolName = toolName,
                error = ToolError(
                    code = "SAFETY_CHECK_ERROR",
                    message = "Safety check failed: ${e.message}"
                ),
                observationRequired = false
            )
        }

        // 3. Handle safety verdict
        when (safetyResult) {
            SafetyCheckResult.BLOCKED -> {
                val reason = safetyController.lastReason ?: "Blocked by safety policy"
                Log.w(TAG, "Tool '$toolName' blocked: $reason")
                return ToolResult(
                    success = false,
                    toolName = toolName,
                    error = ToolError(
                        code = ERROR_BLOCKED,
                        message = reason
                    ),
                    observationRequired = false
                )
            }

            SafetyCheckResult.REQUIRES_CONFIRMATION -> {
                val reason = safetyController.lastReason
                    ?: "This action requires user confirmation"
                Log.i(TAG, "Tool '$toolName' requires confirmation: $reason")
                return ToolResult(
                    success = false,
                    toolName = toolName,
                    error = ToolError(
                        code = ERROR_CONFIRMATION_REQUIRED,
                        message = reason
                    ),
                    observationRequired = false
                )
            }

            SafetyCheckResult.ALLOWED -> {
                // Fall through to execution
            }
        }

        // 4. Execute the handler
        return try {
            val startTime = System.currentTimeMillis()
            val result = handler.execute(args)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(
                TAG,
                "Tool '$toolName' executed in ${elapsed}ms, success=${result.success}"
            )
            result
        } catch (e: CancellationException) {
            Log.w(TAG, "Tool '$toolName' execution cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool '$toolName' execution failed", e)
            ToolResult(
                success = false,
                toolName = toolName,
                error = ToolError(
                    code = "EXECUTION_ERROR",
                    message = e.message ?: "Unknown error during tool execution"
                ),
                observationRequired = true
            )
        }
    }

    /**
     * Check whether a handler exists for the given tool name without
     * performing any safety evaluation.
     *
     * @param toolName The tool name to look up.
     * @return `true` if a [ToolHandler] is registered for this name.
     */
    fun hasHandler(toolName: String): Boolean = toolHandlers.containsKey(toolName)

    /**
     * Return the names of all registered tool handlers.
     */
    fun getRegisteredHandlerNames(): Set<String> = toolHandlers.keys

    /**
     * Attempt to find the closest matching registered tool name for a
     * name the model supplied that does not exist.
     *
     * Resolution order:
     * 1. Exact alias lookup via [TOOL_ALIASES].
     * 2. Levenshtein distance ≤ 3 against all registered names.
     * 3. Substring / suffix match (e.g. `"click"` → `"android.click"`).
     *
     * @return The best-matching canonical tool name, or `null` if nothing
     *         is close enough.
     */
    fun findClosestToolName(requestedName: String): String? {
        val normalised = requestedName.trim().lowercase()

        // 1. Alias lookup
        TOOL_ALIASES[normalised]?.let { return it }

        // 2. Levenshtein distance
        var bestName: String? = null
        var bestDist = Int.MAX_VALUE
        for (registered in toolHandlers.keys) {
            val d = levenshtein(normalised, registered.lowercase())
            if (d < bestDist) {
                bestDist = d
                bestName = registered
            }
        }
        if (bestDist <= 3 && bestName != null) return bestName

        // 3. Suffix match: if the requested name ends with the part after
        //    the last dot of a registered name, suggest it.
        val suffix = normalised.substringAfterLast(".", normalised)
        if (suffix != normalised) {
            // already had a dot – try matching the suffix part
            val noDotMatch = toolHandlers.keys.firstOrNull {
                it.substringAfterLast(".") == suffix
            }
            if (noDotMatch != null) return noDotMatch
        }
        // Try matching the whole requested name against registered suffixes
        val suffixMatch = toolHandlers.keys.firstOrNull {
            it.substringAfterLast(".") == normalised
        }
        if (suffixMatch != null) return suffixMatch

        return null
    }

    /** Simple Levenshtein distance between two strings. */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val la = a.length
        val lb = b.length
        if (la == 0) return lb
        if (lb == 0) return la
        var prev = IntArray(lb + 1) { it }
        var curr = IntArray(lb + 1)
        for (i in 1..la) {
            curr[0] = i
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
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
        return prev[lb]
    }
}