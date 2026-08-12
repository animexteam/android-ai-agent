package com.androidagent.aiagent.agent

import com.androidagent.aiagent.accessibility.AccessibilityNodeMapper
import com.androidagent.aiagent.ai.GemmaClient

class AgentPromptBuilder {

    fun buildSystemPrompt(): String {
        return """
            You are an AI agent controlling an OPPO Android 15 phone (ColorOS). You interact with the device through accessibility services and gesture APIs. You MUST use ONLY the tools listed below — never invent tools, shell commands, intents, or adb commands.

            ## Device Context

            - Device: OPPO, Android 15, ColorOS
            - You see the screen through an accessibility tree: a snapshot of all visible UI elements (nodes). Each node has a unique node_id, text, contentDescription, resourceId, className, bounds, and boolean flags (isClickable, isEditable, isScrollable, isFocusable).
            - Each observation has a unique observation_id. Node IDs are ONLY valid within that specific observation. When the screen changes (navigation, click, scroll, animation), ALL old node IDs become invalid.
            - The user message includes the full UI hierarchy. Use it to find the correct node_id before acting.
            - Some elements (images, maps, custom views) may lack accessibility info. Use vision tools as fallback for those.

            ## Available Tools

            ### Navigation
            - `android.launch_app` — Launch an app by package name (e.g. "com.android.chrome") or app_name (e.g. "Chrome", "Settings"). Prefer this over searching the home screen.
            - `android.back` — Press the system back button. No arguments needed.
            - `android.home` — Press the home button to return to the launcher. No arguments needed.
            - `android.recents` — Open the recent apps / task switcher. No arguments needed.

            ### Observation & Search
            - `android.inspect_screen` — Capture the full accessibility UI tree as structured JSON. No arguments needed. Use when you need complete node details.
            - `android.find` — Search the current UI tree for nodes. Filter by text, text_contains, content_description, resource_id, class_name, clickable, editable, scrollable. Returns matching nodes with IDs and bounds.
            - `android.screenshot` — Take a screenshot (returns base64 JPEG). No arguments needed.
            - `vision.analyze_screen` — Take a screenshot and describe visible UI elements, text, buttons, icons, and their positions. Optional: query for a specific question.
            - `vision.find_visual_target` — Find a visual element on screen by description (e.g. "the red submit button"). Returns x, y coordinates. Use as fallback when accessibility cannot find the target.

            ### Interaction
            - `android.click` — Tap a UI element. Provide node_id from the most recent observation, OR x,y coordinates as fallback.
            - `android.long_click` — Long-press a UI element. Provide node_id or x,y.
            - `android.type_text` — Type text into a field. Provide text (required) and optionally node_id of the editable field.
            - `android.clear_text` — Clear text from a field. Optionally provide node_id.
            - `android.scroll` — Scroll a list/container. Provide direction ("up" or "down"), optionally amount (0.0-1.0, default 0.7) and node_id of scrollable container.
            - `android.swipe` — Swipe gesture. Provide direction ("up"/"down"/"left"/"right") or explicit startX/startY/endX/endY coordinates. Optionally duration_ms (default 300).
            - `android.press_key` — Press a system key. Key must be one of: ENTER, BACK, TAB, ESCAPE, SPACE.
            - `android.wait` — Wait for a specified time. Provide milliseconds (default 1000, max 30000). Use after actions that need time to settle.

            ### Agent Control
            - `agent.ask_user` — Ask the user a question. Provide question (required). Agent pauses until the user responds.
            - `agent.confirm` — Request user confirmation for a sensitive action. Provide action (required) and reason.
            - `agent.finish` — Signal task completion. Provide success (boolean, required) and message. Use when the goal is achieved or cannot be achieved.
            - `agent.stop` — Stop the agent immediately. Provide an optional reason.

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

    fun buildUserMessage(
        goal: String,
        observation: AndroidObservation?,
        history: List<AgentEvent>,
        includeScreenshot: Boolean = false
    ): String {
        val sb = StringBuilder()

        sb.appendLine("## Current Task")
        sb.appendLine(goal)
        sb.appendLine()

        if (observation != null) {
            sb.appendLine("## Current Screen State")
            sb.appendLine("Package: ${observation.packageName}")
            sb.appendLine("Activity: ${observation.activityName}")
            if (!observation.windowTitle.isNullOrBlank()) {
                sb.appendLine("Window Title: ${observation.windowTitle}")
            }
            sb.appendLine()

            sb.appendLine("### UI Hierarchy")
            val compactTree = AccessibilityNodeMapper.serializeCompact(observation.uiTree)
            sb.appendLine(compactTree)
            sb.appendLine()
        } else {
            sb.appendLine("## Current Screen State")
            sb.appendLine("No observation available. You should use agent.observe first.")
            sb.appendLine()
        }

        sb.appendLine("### Recent History")
        val recentEvents = history
            .filterIsInstance<AgentEvent.ToolExecution>()
            .takeLast(10)
        if (recentEvents.isEmpty()) {
            sb.appendLine("(No previous actions)")
        } else {
            for (event in recentEvents) {
                val status = if (event.result.success) "SUCCESS" else "FAILED"
                sb.appendLine("Step ${event.stepNumber}: ${event.toolName} → $status")
            }
        }
        sb.appendLine()

        if (observation != null) {
            sb.appendLine("### Available Observation ID")
            sb.appendLine(observation.id)
            sb.appendLine()
        }

        sb.appendLine("### Instruction")
        sb.append("Decide your next action. Return a single JSON object.")

        return sb.toString()
    }

    fun buildHistoryMessages(
        events: List<AgentEvent>,
        maxEvents: Int = 20
    ): List<GemmaClient.ChatMessage> {
        val recentEvents = events.takeLast(maxEvents)
        val messages = mutableListOf<GemmaClient.ChatMessage>()

        for (event in recentEvents) {
            when (event) {
                is AgentEvent.ToolExecution -> {
                    val argsSummary = event.arguments.let { args ->
                        if (args.length > 50) args.take(50) + "..." else args
                    }
                    val resultObj = event.result.result
                    val resultSummary = resultObj?.let { result ->
                        val entries = result.entries.take(2).joinToString(", ") {
                            "${it.key}=${it.value}"
                        }
                        if (result.size > 2) "$entries, ..." else entries
                    }
                    val errorSummary = event.result.error?.let { "Error: ${it.message}" }
                    val outcome = when {
                        errorSummary != null -> errorSummary
                        resultSummary != null -> resultSummary
                        event.result.success -> "Success"
                        else -> "Completed without output"
                    }
                    messages.add(
                        GemmaClient.ChatMessage(
                            role = "assistant",
                            content = "Executed ${event.toolName}($argsSummary): $outcome"
                        )
                    )
                }

                is AgentEvent.Observation -> {
                    messages.add(
                        GemmaClient.ChatMessage(
                            role = "user",
                            content = "[Observation] ${event.summary}"
                        )
                    )
                }

                is AgentEvent.ModelResponse -> {
                    val truncated = if (event.content.length > 200) {
                        event.content.take(200) + "..."
                    } else {
                        event.content
                    }
                    messages.add(
                        GemmaClient.ChatMessage(
                            role = "assistant",
                            content = "[Model] $truncated"
                        )
                    )
                }

                is AgentEvent.UserMessage -> {
                    messages.add(
                        GemmaClient.ChatMessage(
                            role = "user",
                            content = "[User] ${event.text}"
                        )
                    )
                }

                is AgentEvent.StatusChange -> {
                    // Status changes are informational, include as system-like context
                    messages.add(
                        GemmaClient.ChatMessage(
                            role = "user",
                            content = "[Status] Changed to ${event.to}"
                        )
                    )
                }

                is AgentEvent.Error -> {
                    messages.add(
                        GemmaClient.ChatMessage(
                            role = "user",
                            content = "[Error at step ${event.stepNumber}] ${event.message}"
                        )
                    )
                }
            }
        }

        return messages
    }
}
