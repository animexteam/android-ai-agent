package com.androidagent.aiagent.agent

import com.androidagent.aiagent.accessibility.AccessibilityNodeMapper
import com.androidagent.aiagent.tools.AgentTool

/**
 * Constructs every prompt that the agent sends to the AI model.
 *
 * Design: System prompt is built once per task (tool catalogue is static).
 * User message is rebuilt every turn with live screen data.
 *
 * Prompt engineering inspired by Minitap mobile-use Cortex architecture,
 * adapted for single-agent on-device execution.
 */
class AgentPromptBuilder {

    fun buildSystemPrompt(tools: List<AgentTool> = emptyList()): String {
        val toolCatalogue = buildToolCatalogue(tools)
        return """
            You are Android-Use, an AI agent that controls an Android phone on behalf of the user. You see the screen through accessibility services and interact via gestures and node actions.

            ## Your Two Senses

            1. **UI Hierarchy** — A snapshot of all visible UI elements. Each node has: node_id, text, contentDescription, resourceId, className, bounds (x,y,width,height), and flags (isClickable, isEditable, isScrollable, isFocusable).
            2. **Screenshot** — A visual snapshot of the screen (included when the accessibility tree is insufficient).

            ## Element Targeting (MANDATORY FORMAT)

            When targeting an element, you MUST provide the most specific identifier available:
            - **resourceId** (most reliable) — e.g. "com.whatsapp:id/send"
            - **text** — the visible text label on the element
            - **bounds** — {"x": N, "y": N, "width": N, "height": N}
            - **node_id** — from the current observation's UI tree (ONLY valid within that observation)

            Always prefer resourceId > text > bounds > node_id.

            ## Fallback Strategy

            If an action fails with NODE_NOT_FOUND:
            1. The system automatically re-observes and retries once — do NOT manually retry.
            2. If it still fails, try a DIFFERENT targeting method (e.g. switch from node_id to bounds coordinates).
            3. If all methods fail, the element may not be visible — try scrolling first.

            ## Critical Rules

            1. **Observe before acting** — The UI tree is provided each turn. Use it to find correct targets.
            2. **Never reuse old node_ids** — When the screen changes, ALL previous node_ids become invalid.
            3. **One logical step at a time** — Do not chain many actions. Observe the result before the next step.
            4. **Never assume success** — Verify results on screen before claiming completion.
            5. **Isolate unpredictable actions** — `back`, `launch_app`, `stop_app`, `open_link` must be the ONLY action in that turn. Wait for the screen to settle after these.
            6. **Never repeat failed actions** — If an action fails, understand WHY before trying again. Try a different approach.
            7. **Complete goals ONLY on evidence** — Only finish when you have OBSERVED the desired result on screen.
            8. **Swipe physics** — Swiping RIGHT reveals content from the LEFT. Swiping DOWN reveals content below.
            9. **Form filling** — Scroll through the entire form before concluding a field is missing.
            10. **Use Settings app** — For system configurations, launch Settings via `android.launch_app` (app_name: "Settings") and navigate with UI tools.
            11. **Vision as fallback** — Use vision tools ONLY when accessibility info is insufficient (images, maps, custom views).
            12. **Loop detection** — If you detect you are repeating actions, try a completely different strategy.

            $toolCatalogue

            ## Response Format

            Return STRICTLY a single JSON object. No markdown, no explanation, no extra text.

            To call a tool:
            {"type": "tool_call", "tool_name": "<exact tool name>", "arguments": {<key>: <value>, ...}}

            To send a status message (does NOT end the turn):
            {"type": "message", "content": "<your message>"}

            To ask the user a question (pauses agent):
            {"type": "ask_user", "question": "<your question>"}

            To finish the task:
            {"type": "finish", "success": true|false, "message": "<summary>"}
        """.trimIndent()
    }

    fun buildUserMessage(
        goal: String,
        observation: AndroidObservation?,
        history: List<AgentEvent>,
        loopWarning: String? = null,
        maxHistoryEvents: Int = 8
    ): String {
        val sb = StringBuilder(8192)

        // Task
        sb.appendLine("## Task")
        sb.appendLine(goal)
        sb.appendLine()

        // Screen state
        if (observation != null) {
            sb.appendLine("## Screen")
            sb.appendLine("Package: ${observation.packageName ?: "unknown"}")
            if (!observation.windowTitle.isNullOrBlank()) {
                sb.appendLine("Title: ${observation.windowTitle}")
            }
            sb.appendLine("Observation: ${observation.id}")
            sb.appendLine()

            sb.appendLine("### UI Tree")
            sb.appendLine(AccessibilityNodeMapper.serializeCompact(observation.uiTree))
            sb.appendLine()
        } else {
            sb.appendLine("## Screen")
            sb.appendLine("(No observation available)")
            sb.appendLine()
        }

        // Compact action history
        val recentActions = history
            .filterIsInstance<AgentEvent.ToolExecution>()
            .takeLast(maxHistoryEvents)
        if (recentActions.isNotEmpty()) {
            sb.appendLine("### Action History")
            for (event in recentActions) {
                val status = if (event.result.success) "OK" else "FAIL"
                val detail = event.result.error?.let { ": ${it.message}" } ?: ""
                sb.appendLine("- ${event.toolName} -> $status$detail")
            }
            sb.appendLine()
        }

        // Loop warning
        if (loopWarning != null) {
            sb.appendLine("### WARNING: Loop Detected")
            sb.appendLine(loopWarning)
            sb.appendLine("You MUST choose a DIFFERENT action immediately.")
            sb.appendLine()
        }

        sb.append("Decide your next action. Return a single JSON object.")

        return sb.toString()
    }

    private fun buildToolCatalogue(tools: List<AgentTool>): String {
        if (tools.isEmpty()) return DEFAULT_TOOL_CATALOGUE

        val sb = StringBuilder()
        sb.appendLine("## Available Tools")
        sb.appendLine()

        val grouped = tools.sortedBy { it.name }.groupBy { tool ->
            val dot = tool.name.indexOf('.')
            if (dot >= 0) tool.name.substring(0, dot) else "other"
        }

        val sectionTitles = mapOf(
            "android" to "### Device Control",
            "vision"  to "### Vision (fallback only)",
            "agent"   to "### Agent Control",
            "other"   to "### Other"
        )

        for ((group, groupTools) in grouped) {
            sb.appendLine(sectionTitles[group] ?: "### $group")
            for (tool in groupTools) {
                sb.appendLine("- `${tool.name}` — ${tool.description}")
            }
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }

    companion object {
        private val DEFAULT_TOOL_CATALOGUE = """
            ## Available Tools

            ### Device Control
            - `android.click` — Tap a UI element by node_id or x,y coordinates.
            - `android.long_click` — Long-press a UI element by node_id or x,y coordinates.
            - `android.type_text` — Type text into a field (multiple fallback strategies).
            - `android.clear_text` — Clear text from a field.
            - `android.scroll` — Scroll a container or screen up/down.
            - `android.swipe` — Swipe gesture in any direction.
            - `android.launch_app` — Launch an app by package name or app_name.
            - `android.back` — Press system back button.
            - `android.home` — Press home button.
            - `android.recents` — Open recent apps.
            - `android.press_key` — Press a system key (ENTER, BACK, TAB, SPACE).
            - `android.wait` — Wait for a specified time in milliseconds.
            - `android.screenshot` — Take a screenshot (returns base64 JPEG).
            - `android.inspect_screen` — Capture the full accessibility UI tree.
            - `android.find` — Search the UI tree for nodes by text, description, or resource ID.

            ### Vision
            - `vision.analyze_screen` — Describe visible UI elements via screenshot.
            - `vision.find_visual_target` — Find a visual element on screen by description.

            ### Agent Control
            - `agent.ask_user` — Ask the user a question (pauses agent).
            - `agent.confirm` — Request user confirmation for a sensitive action.
            - `agent.finish` — Signal task completion.
            - `agent.stop` — Stop the agent immediately.
        """.trimIndent()
    }
}
