package com.androidagent.aiagent.agent

import com.androidagent.aiagent.accessibility.AccessibilityNodeMapper
import com.androidagent.aiagent.tools.AgentTool

/**
 * Constructs every prompt that the agent sends to the AI model.
 *
 * This class is the **single source of truth** for prompt content.  The
 * [AgentRuntime] never builds prompt strings inline – it always delegates
 * to this builder.
 *
 * Design principles:
 * - The **system prompt** contains the agent's persona, tool catalogue,
 *   and rules.  It is built once per task and includes the actual
 *   registered tool definitions so the model always sees the current
 *   tool set.
 * - The **user message** contains the live screen state, recent history,
 *   and loop warnings.  It is rebuilt every turn.
 * - History is embedded as a compact text block inside the user message
 *   (not as separate chat messages) to keep the context window compact.
 */
class AgentPromptBuilder {

    // -----------------------------------------------------------------------
    // System prompt
    // -----------------------------------------------------------------------

    /**
     * Build the system prompt including the current tool catalogue.
     *
     * @param tools The tools currently registered in the [ToolRegistry].
     * @param deviceInfo Optional override for the device description line.
     */
    fun buildSystemPrompt(tools: List<AgentTool> = emptyList()): String {
        val toolCatalogue = buildToolCatalogue(tools)
        return """
            You are an AI agent controlling an Android phone. You interact with the device through accessibility services and gesture APIs. You MUST use ONLY the tools listed below — never invent tools, shell commands, intents, or adb commands.

            ## Device Context

            - You see the screen through an accessibility tree: a snapshot of all visible UI elements (nodes). Each node has a unique node_id, text, contentDescription, resourceId, className, bounds, and boolean flags (isClickable, isEditable, isScrollable, isFocusable).
            - Each observation has a unique observation_id. Node IDs are ONLY valid within that specific observation. When the screen changes (navigation, click, scroll, animation), ALL old node IDs become invalid.
            - The user message includes the full UI hierarchy. Use it to find the correct node_id before acting.
            - Some elements (images, maps, custom views) may lack accessibility info. Use vision tools as fallback for those.

            $toolCatalogue

            ## Critical Rules

            1. ALWAYS re-observe after any action that might change the screen (clicks, navigation, text input, scrolling, launching apps). Never reuse node IDs from a previous observation.
            2. NEVER reuse node_ids from old observations. If a click/interaction fails with NODE_NOT_FOUND, the system will automatically re-observe and retry once. Do not manually retry — just proceed to your next decision after seeing the result.
            3. Observe the screen before every action. Never act blindly.
            4. Never assume an action succeeded without confirmation from a new observation.
            5. Never claim task completion without verifying the result on screen.
            6. Take one or a small number of logical steps at a time. Do not chain many actions in one response.
            7. For alarms, settings, and system configurations, prefer launching the Settings app via `android.launch_app` (app_name: "Settings") and navigating with UI tools, rather than searching the home screen.
            8. Use vision tools (vision.analyze_screen, vision.find_visual_target) ONLY when accessibility info is insufficient — for images, maps, custom drawn views, or when nodes cannot be found.
            9. If you get stuck in a loop, try a different approach or use agent.stop / agent.finish to end gracefully.
            10. Ask the user if essential information is missing and cannot be obtained from the screen.

            ## Response Format

            You MUST return STRICTLY a single JSON object. No markdown, no explanation, no extra text.

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

    // -----------------------------------------------------------------------
    // User message (rebuilt every turn)
    // -----------------------------------------------------------------------

    /**
     * Build the user message for the current agent turn.
     *
     * This is the **only** method the runtime should call to build the
     * per-turn prompt.  It embeds the goal, screen state, UI tree, recent
     * history, and an optional loop warning into a single string.
     *
     * @param goal The user's original task instruction.
     * @param observation The latest screen observation (may be null on first turn).
     * @param history Recent event history for context.
     * @param loopWarning Optional loop-detection warning to inject.
     * @param maxHistoryEvents How many recent events to include (default 12).
     */
    fun buildUserMessage(
        goal: String,
        observation: AndroidObservation?,
        history: List<AgentEvent>,
        loopWarning: String? = null,
        maxHistoryEvents: Int = 12
    ): String {
        val sb = StringBuilder(8192)

        // --- Task ---
        sb.appendLine("## Current Task")
        sb.appendLine(goal)
        sb.appendLine()

        // --- Screen state ---
        if (observation != null) {
            sb.appendLine("## Current Screen State")
            sb.appendLine("Package: ${observation.packageName ?: "unknown"}")
            sb.appendLine("Activity: ${observation.activityName ?: "unknown"}")
            if (!observation.windowTitle.isNullOrBlank()) {
                sb.appendLine("Window Title: ${observation.windowTitle}")
            }
            sb.appendLine("Observation ID: ${observation.id}")
            sb.appendLine()

            sb.appendLine("### UI Hierarchy")
            sb.appendLine(AccessibilityNodeMapper.serializeCompact(observation.uiTree))
            sb.appendLine()
        } else {
            sb.appendLine("## Current Screen State")
            sb.appendLine("(No observation available – use a tool to observe the screen first.)")
            sb.appendLine()
        }

        // --- Recent history ---
        val recentEvents = history.takeLast(maxHistoryEvents)
        if (recentEvents.isNotEmpty()) {
            sb.appendLine("### Recent History")
            for (event in recentEvents) {
                sb.appendLine(formatEventForHistory(event))
            }
            sb.appendLine()
        }

        // --- Loop warning ---
        if (loopWarning != null) {
            sb.appendLine("### WARNING: Loop Detected")
            sb.appendLine(loopWarning)
            sb.appendLine("You MUST choose a different action.")
            sb.appendLine()
        }

        // --- Instruction ---
        sb.appendLine("### Instruction")
        sb.append("Decide your next action. Return a single JSON object.")

        return sb.toString()
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Build the tool catalogue section of the system prompt.
     * When [tools] is empty, falls back to a static hard-coded list so
     * the prompt is still useful during early init.
     */
    private fun buildToolCatalogue(tools: List<AgentTool>): String {
        if (tools.isEmpty()) {
            return DEFAULT_TOOL_CATALOGUE
        }

        val sb = StringBuilder()
        sb.appendLine("## Available Tools")
        sb.appendLine()

        // Group by namespace prefix (e.g. "android.", "vision.", "agent.")
        val grouped = tools.sortedBy { it.name }.groupBy { tool ->
            val dot = tool.name.indexOf('.')
            if (dot >= 0) tool.name.substring(0, dot) else "other"
        }

        val sectionTitles = mapOf(
            "android" to "### Navigation & Interaction",
            "vision"  to "### Vision (fallback for inaccessible elements)",
            "agent"   to "### Agent Control",
            "other"   to "### Other Tools"
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

    /**
     * Format a single [AgentEvent] as a one-line history entry.
     */
    private fun formatEventForHistory(event: AgentEvent): String {
        return when (event) {
            is AgentEvent.ToolExecution -> {
                val status = if (event.result.success) "SUCCESS" else "FAILED"
                val detail = event.result.error?.let { ": ${it.message}" } ?: ""
                "Step ${event.stepNumber}: ${event.toolName} → $status$detail"
            }
            is AgentEvent.Observation ->
                "Step ${event.stepNumber}: Observed ${event.summary}"
            is AgentEvent.ModelResponse ->
                "Step ${event.stepNumber}: Model → ${event.decisionType}: ${event.content}"
            is AgentEvent.UserMessage ->
                "Step ${event.stepNumber}: User said: ${event.text}"
            is AgentEvent.Error ->
                "Step ${event.stepNumber}: ERROR: ${event.message}"
            is AgentEvent.StatusChange ->
                "Step ${event.stepNumber}: Status ${event.from} → ${event.to}"
        }
    }

    companion object {
        /** Static fallback when the tool registry has not been populated yet. */
        private val DEFAULT_TOOL_CATALOGUE = """
            ## Available Tools

            ### Navigation & Interaction
            - `android.launch_app` — Launch an app by package name or app_name.
            - `android.back` — Press the system back button.
            - `android.home` — Press the home button.
            - `android.recents` — Open the recent apps / task switcher.
            - `android.inspect_screen` — Capture the full accessibility UI tree.
            - `android.find` — Search the UI tree for nodes by text, description, resource ID, etc.
            - `android.screenshot` — Take a screenshot (returns base64 JPEG).
            - `android.click` — Tap a UI element by node_id or x,y coordinates.
            - `android.long_click` — Long-press a UI element.
            - `android.type_text` — Type text into a field.
            - `android.clear_text` — Clear text from a field.
            - `android.scroll` — Scroll a list/container up or down.
            - `android.swipe` — Swipe gesture in any direction.
            - `android.press_key` — Press a system key (ENTER, BACK, TAB, ESCAPE, SPACE).
            - `android.wait` — Wait for a specified time in milliseconds.

            ### Vision
            - `vision.analyze_screen` — Take a screenshot and describe visible UI elements.
            - `vision.find_visual_target` — Find a visual element on screen by description.

            ### Agent Control
            - `agent.ask_user` — Ask the user a question (pauses agent).
            - `agent.confirm` — Request user confirmation for a sensitive action.
            - `agent.finish` — Signal task completion.
            - `agent.stop` — Stop the agent immediately.
        """.trimIndent()
    }
}
