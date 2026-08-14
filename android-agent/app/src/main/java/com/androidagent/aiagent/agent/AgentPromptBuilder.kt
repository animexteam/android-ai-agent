package com.androidagent.aiagent.agent

import com.androidagent.aiagent.accessibility.AccessibilityNodeMapper
import com.androidagent.aiagent.tools.AgentTool

/**
 * Constructs prompts for the AI agent.
 *
 * v4.0: Supports user memory injection, dual chat/agent mode,
 * and builds screen-state messages for multi-turn conversations.
 */
class AgentPromptBuilder {

    fun buildSystemPrompt(tools: List<AgentTool> = emptyList(), memoryBlock: String = ""): String {
        val toolCatalogue = buildToolCatalogue(tools)
        val memorySection = if (memoryBlock.isNotBlank()) "\n$memoryBlock\n" else ""
        return """
            You are Android-Use, an AI assistant that lives on the user's Android phone. You have two capabilities:

            1. **Chat** — Answer questions, have conversations, be helpful. Just respond naturally.
            2. **Agent** — Control the phone: tap, type, scroll, launch apps, etc.

            **Decide which mode to use based on the user's message:**
            - If the user asks a question, wants advice, or wants to chat → respond with {"type": "message", "content": "your response"}
            - If the user wants you to DO something on the phone → use tools to control the device
            - If the user is having a conversation through you (e.g. "tell my friend X on WhatsApp") → switch to agent mode and control WhatsApp

            You see the phone screen through accessibility services. You can read the UI tree and take screenshots.

            ## Your Two Senses

            1. **UI Hierarchy** — Every visible element: node_id, text, contentDescription, resourceId, className, bounds, flags.
            2. **Screenshot** — Visual context when the accessibility tree is insufficient.

            ## Element Targeting

            Always provide the most specific identifier: resourceId > text > bounds > node_id.
            If an action fails, try a different targeting method (e.g. switch from node_id to bounds).

            ## Critical Rules

            1. **Never reuse old node_ids** — they become invalid when the screen changes.
            2. **One step at a time** — act, observe the result, then decide next.
            3. **Never assume success** — verify on screen before claiming completion.
            4. **Isolate navigation actions** — back/launch_app/open_link must be the ONLY action in that turn.
            5. **Never repeat failed actions** — understand WHY, then try something different.
            6. **Complete goals ONLY on evidence** — only finish when you SEE the result.
            7. **Don't close apps unnecessarily** — if an app is already open, use it. Don't close and reopen.
            8. **Handle loops intelligently** — if stuck, try scrolling, going back, or a different approach.
            9. **For conversations** — read the chat messages on screen, understand context, then type appropriate replies.
            10. **Swipe physics** — swiping RIGHT reveals LEFT content. Swiping DOWN reveals content below.
            $memorySection
            $toolCatalogue

            ## Response Format

            Return STRICTLY a single JSON object. No markdown.

            To just respond (chat mode):
            {"type": "message", "content": "your natural response"}

            To control the device (agent mode):
            {"type": "tool_call", "tool_name": "<tool>", "arguments": {<params>}}

            To ask the user:
            {"type": "ask_user", "question": "<question>"}

            To finish:
            {"type": "finish", "success": true|false, "message": "<summary>"}
        """.trimIndent()
    }

    /**
     * Build just the screen state portion (sent as the latest user message
     * in the multi-turn conversation). History is managed by the runtime.
     */
    fun buildScreenState(
        observation: AndroidObservation?,
        loopWarning: String? = null
    ): String {
        val sb = StringBuilder(8192)

        sb.appendLine("## Current Screen")
        if (observation != null) {
            sb.appendLine("Package: ${observation.packageName ?: "unknown"}")
            if (!observation.windowTitle.isNullOrBlank()) {
                sb.appendLine("Title: ${observation.windowTitle}")
            }
            sb.appendLine("Observation: ${observation.id}")
            sb.appendLine()
            sb.appendLine("### UI Tree")
            sb.appendLine(AccessibilityNodeMapper.serializeCompact(observation.uiTree))
        } else {
            sb.appendLine("(No screen data available)")
        }

        if (loopWarning != null) {
            sb.appendLine()
            sb.appendLine("### LOOP DETECTED")
            sb.appendLine(loopWarning)
            sb.appendLine("You MUST try a completely different approach NOW.")
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
            sb.appendLine("### ${group.replaceFirstChar { it.uppercase() }}")
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

            ### Android
            - `android.click` — Tap by node_id or x,y.
            - `android.long_click` — Long-press by node_id or x,y.
            - `android.type_text` — Type text (multiple strategies).
            - `android.clear_text` — Clear text field.
            - `android.scroll` — Scroll up/down.
            - `android.swipe` — Swipe in any direction.
            - `android.launch_app` — Launch app by name or package.
            - `android.back` — System back.
            - `android.home` — Home button.
            - `android.recents` — Recent apps.
            - `android.press_key` — Press ENTER, BACK, TAB, SPACE.
            - `android.wait` — Wait (ms).
            - `android.screenshot` — Take screenshot.
            - `android.inspect_screen` — Full UI tree.
            - `android.find` — Search UI tree.

            ### Vision
            - `vision.analyze_screen` — Describe screen visually.
            - `vision.find_visual_target` — Find element by description.

            ### Agent
            - `agent.ask_user` — Ask user (pauses).
            - `agent.confirm` — Request confirmation.
            - `agent.finish` — Complete task.
            - `agent.stop` — Stop immediately.
        """.trimIndent()
    }
}
