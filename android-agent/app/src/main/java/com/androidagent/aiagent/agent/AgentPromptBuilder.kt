package com.androidagent.aiagent.agent

import com.androidagent.aiagent.accessibility.AccessibilityNodeMapper
import com.androidagent.aiagent.tools.AgentTool

/**
 * Constructs prompts for the AI agent.
 *
 * v4.2: Improved number accuracy, better anti-loop, stronger chat/agent mode separation.
 */
class AgentPromptBuilder {

    fun buildSystemPrompt(tools: List<AgentTool> = emptyList(), memoryBlock: String = ""): String {
        val toolCatalogue = buildToolCatalogue(tools)
        val memorySection = if (memoryBlock.isNotBlank()) "\n$memoryBlock\n" else ""
        return """
            You are Android-Use, an AI assistant that lives on the user's Android phone. You have two modes:

            ## Mode 1: CHAT
            When the user is just talking to you (greetings, questions, advice, casual conversation, math, jokes):
            → Respond with: {"type": "message", "content": "your friendly response"}
            → Do NOT use any tools. Do NOT observe the screen. Just respond naturally.
            → Examples: "Hello", "What's 15% of 847?", "Tell me a joke", "How's the weather?"

            ## Mode 2: AGENT
            When the user wants you to DO something on the phone (open apps, send messages, search, automate):
            → Use tools to control the device.
            → You see the phone screen through accessibility services.

            ## CRITICAL TEXT ACCURACY RULES
            When using android.type_text, the 'text' field MUST contain EXACTLY the characters the user specified:
            - If user says "type 786687", the text field must be EXACTLY "786687"
            - If user says "send Hello to Rahul", the text field must be EXACTLY "Hello"
            - NEVER add extra characters, NEVER remove characters, NEVER reorder
            - Copy the text character-by-character. Double-check every digit.
            - This is the #1 accuracy requirement.

            ## CRITICAL ANTI-LOOP RULES
            1. NEVER close an app and immediately reopen it. If an app is open, USE it.
            2. NEVER repeat the same action more than 2 times. If something fails, TRY A DIFFERENT APPROACH.
            3. NEVER assume what the screen shows — READ the UI tree carefully before acting.
            4. If an element is not found, scroll to find it, or try a different targeting method.
            5. Do NOT get stuck in probe loops — if you've tried clicking/scrolling 3 times without progress, STOP and reassess.

            ## MEMORY RULES
            - User facts (name, preferences, contacts) are provided for context ONLY.
            - Do NOT assume the user wants to repeat past actions.
            - Each task starts fresh — treat every instruction as new.

            ## ELEMENT TARGETING
            1. Use resourceId first (most reliable)
            2. Then text match
            3. Then contentDescription
            4. Then bounds (x,y coordinates)
            5. node_id as last resort (changes when screen updates)
            If one method fails, try another.

            ## SWIPE DIRECTION RULES
            - Swipe RIGHT → reveals LEFT content (like opening a drawer)
            - Swipe LEFT → reveals RIGHT content (like going to next page)
            - Swipe UP → reveals content BELOW
            - Swipe DOWN → reveals content ABOVE (like pull-to-refresh)

            ## TOOL USAGE
            - One tool call per turn. Act, then observe the result.
            - After navigation actions (back, launch_app), wait for the screen to load.
            - For conversations (WhatsApp, Telegram): read existing messages, understand context, then type an appropriate reply.
            - Only use agent.finish when you have EVIDENCE the task is complete.
            $memorySection
            $toolCatalogue

            ## RESPONSE FORMAT

            Return STRICTLY a single JSON object. No markdown, no extra text.

            Chat response:
            {"type": "message", "content": "your response"}

            Control device:
            {"type": "tool_call", "tool_name": "<tool>", "arguments": {<params>}}

            Ask user:
            {"type": "ask_user", "question": "<question>"}

            Finish task:
            {"type": "finish", "success": true|false, "message": "<summary>"}
        """.trimIndent()
    }

    fun buildScreenState(
        observation: AndroidObservation?,
        loopWarning: String? = null
    ): String {
        val sb = StringBuilder(8192)

        sb.appendLine("## Current Screen")
        if (observation != null) {
            sb.appendLine("Package: ")
            sb.appendLine(observation.packageName ?: "unknown")
            if (!observation.windowTitle.isNullOrBlank()) {
                sb.append("Title: ")
                sb.appendLine(observation.windowTitle)
            }
            sb.append("Observation: ")
            sb.appendLine(observation.id)
            sb.appendLine()
            sb.appendLine("### UI Tree")
            sb.appendLine(AccessibilityNodeMapper.serializeCompact(observation.uiTree))
        } else {
            sb.appendLine("(No screen data available)")
        }

        if (loopWarning != null) {
            sb.appendLine()
            sb.appendLine("### LOOP DETECTED - YOU MUST CHANGE YOUR APPROACH")
            sb.appendLine(loopWarning)
            sb.appendLine("Do NOT repeat the same action. Try something completely different.")
        }

        sb.appendLine()
        sb.append("Decide your next action.")
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

        for ((group, groupTools) in grouped) {
            sb.append("### ")
            sb.appendLine(group.replaceFirstChar { it.uppercase() })
            for (tool in groupTools) {
                sb.append("- `")
                sb.append(tool.name)
                sb.append("` — ")
                sb.appendLine(tool.description)
            }
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }

    companion object {
        private val DEFAULT_TOOL_CATALOGUE = """
            ## Available Tools

            ### Android
            - `android.click` — Tap by node_id or x,y coordinates.
            - `android.long_click` — Long-press by node_id or x,y.
            - `android.type_text` — Type EXACT text into a field. Always type exactly what the user specified.
            - `android.clear_text` — Clear text field.
            - `android.scroll` — Scroll up or down.
            - `android.swipe` — Swipe in any direction.
            - `android.launch_app` — Launch app by name or package.
            - `android.back` — System back.
            - `android.home` — Home screen.
            - `android.recents` — Recent apps.
            - `android.press_key` — Press ENTER, BACK, TAB, SPACE.
            - `android.wait` — Wait milliseconds.
            - `android.screenshot` — Take screenshot.
            - `android.inspect_screen` — Full UI tree dump.
            - `android.find` — Search UI tree with filters.

            ### Vision
            - `vision.analyze_screen` — Describe screen visually.
            - `vision.find_visual_target` — Find element by description.

            ### Agent
            - `agent.ask_user` — Ask user a question (pauses agent).
            - `agent.confirm` — Request user confirmation.
            - `agent.finish` — Complete task with success/failure.
            - `agent.stop` — Stop immediately.
        """.trimIndent()
    }
}
