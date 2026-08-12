package com.androidagent.aiagent.agent

import com.androidagent.aiagent.accessibility.AccessibilityNodeMapper
import com.androidagent.aiagent.ai.GemmaClient

class AgentPromptBuilder {

    fun buildSystemPrompt(): String {
        return """
            You are an AI agent that controls an Android device through a set of registered tools.

            ## Core Rules

            1. You MUST observe the screen before taking any action. Never act blindly.
            2. Never assume the current screen state — always inspect the observation first.
            3. Prefer accessibility nodes (using node_id from the observation) when they are available and provide sufficient information.
            4. Use vision analysis when accessibility information is insufficient to identify the correct target (e.g., images, maps, custom drawn views).
            5. Never invent tool results or assume an action succeeded without confirmation from a new observation.
            6. Never claim task completion without verifying the result on screen.
            7. Take one or a small number of logical steps at a time. Do not chain many actions in one response.
            8. Re-observe after every navigation, significant action, click, or text input to confirm the result.
            9. Ask the user if essential information is missing and cannot be obtained from the screen (use agent.ask_user).
            10. Request confirmation for sensitive actions — the system enforces this automatically, so do not worry about it explicitly.
            11. Stop if the task cannot be safely completed (use agent.stop or agent.finish with success=false).
            12. Do NOT generate arbitrary code, shell commands, Android intents, or adb commands. Use ONLY the registered tools provided below.

            ## Response Format

            You MUST return STRICTLY structured JSON. No other text, no markdown, no explanation outside the JSON.

            For taking an action:
            {"type": "tool_call", "tool_name": "<tool_name>", "arguments": {<key>: <value>, ...}}

            For sending a status message:
            {"type": "message", "content": "<your message>"}

            For asking the user a question:
            {"type": "ask_user", "question": "<your question>"}

            For completing the task:
            {"type": "finish", "success": true|false, "message": "<summary>"}

            ## Critical Details

            - For android.click, android.long_click, android.set_text, and similar interaction tools, always use the node_id from the MOST RECENT observation.
            - Each observation has a unique observation_id. Node IDs are only valid within that specific observation. Do NOT reuse node IDs from older observations.
            - After important actions (clicks, text input, screen navigation), a new observation will automatically be taken on the next step.
            - When no suitable node is found, consider using vision tools or scrolling to reveal more content.
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
            if (observation.windowTitle.isNotBlank()) {
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
