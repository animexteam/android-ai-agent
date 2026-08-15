package com.androidagent.aiagent.agent

import com.androidagent.aiagent.accessibility.AccessibilityNodeMapper
import com.androidagent.aiagent.tools.AgentTool

/**
 * Constructs prompts for the AI agent.
 * v4.3: Complete tool catalogue with 38 tools.
 */
class AgentPromptBuilder {

    fun buildSystemPrompt(tools: List<AgentTool> = emptyList(), memoryBlock: String = ""): String {
        val toolCatalogue = buildToolCatalogue(tools)
        val memorySection = if (memoryBlock.isNotBlank()) "\n$memoryBlock\n" else ""
        return """
            You are Android-Use, an AI assistant that lives on the user's Android phone. You have two modes:

            ## Mode 1: CHAT
            When the user is just talking (greetings, questions, advice, jokes):
            → Respond with: {"type": "message", "content": "your response"}
            → Do NOT use tools.

            ## Mode 2: AGENT
            When the user wants you to DO something on the phone:
            → Use tools to control the device.

            ## CRITICAL TEXT ACCURACY
            When using android.type_text, the 'text' field MUST be EXACTLY what the user specified:
            - If user says "type 786687", the text field must be EXACTLY "786687"
            - NEVER add/remove/reorder characters.

            ## ANTI-LOOP RULES
            1. NEVER close an app and immediately reopen it.
            2. NEVER repeat the same action 3+ times. Try a DIFFERENT approach.
            3. NEVER assume — READ the UI tree before acting.
            4. If element not found, scroll or try different targeting.

            ## ELEMENT TARGETING (priority order)
            1. resourceId (most reliable)
            2. text match
            3. contentDescription
            4. bounds (x,y)
            5. node_id (last resort)

            ## SWIPE DIRECTION
            - Swipe RIGHT → reveals LEFT content
            - Swipe LEFT → reveals RIGHT content
            - Swipe UP → reveals content BELOW
            - Swipe DOWN → reveals content ABOVE

            ## APP LAUNCHING
            - Use android.launch_app with 'app_name' (e.g. "YouTube", "WhatsApp") or 'package' (e.g. "com.whatsapp")
            - Common app names are auto-resolved. If name fails, try 'package'.

            ## TOOL RULES
            - One tool call per turn.
            - After navigation (back/launch), wait for screen load.
            - Only use agent.finish when task is EVIDENTLY complete.
            $memorySection
            $toolCatalogue

            ## RESPONSE FORMAT
            Return STRICTLY a single JSON object. No markdown.
            Chat: {"type": "message", "content": "..."}
            Tool: {"type": "tool_call", "tool_name": "<tool>", "arguments": {<params>}}
            Ask: {"type": "ask_user", "question": "..."}
            Finish: {"type": "finish", "success": true|false, "message": "..."}
        """.trimIndent()
    }

    fun buildScreenState(observation: AndroidObservation?, loopWarning: String? = null): String {
        val sb = StringBuilder(8192)
        sb.appendLine("## Current Screen")
        if (observation != null) {
            sb.appendLine("Package: ").appendLine(observation.packageName ?: "unknown")
            if (!observation.windowTitle.isNullOrBlank()) { sb.append("Title: ").appendLine(observation.windowTitle) }
            sb.append("Observation: ").appendLine(observation.id).appendLine()
            sb.appendLine("### UI Tree").appendLine(AccessibilityNodeMapper.serializeCompact(observation.uiTree))
        } else { sb.appendLine("(No screen data)") }
        if (loopWarning != null) {
            sb.appendLine().appendLine("### LOOP DETECTED").appendLine(loopWarning).appendLine("Try something completely different.")
        }
        sb.appendLine().append("Decide your next action.")
        return sb.toString()
    }

    private fun buildToolCatalogue(tools: List<AgentTool>): String {
        if (tools.isEmpty()) return DEFAULT_TOOL_CATALOGUE
        val sb = StringBuilder()
        sb.appendLine("## Available Tools").appendLine()
        val grouped = tools.sortedBy { it.name }.groupBy { it.name.substringBefore('.') }
        for ((group, groupTools) in grouped) {
            sb.append("### ").appendLine(group.replaceFirstChar { it.uppercase() })
            for (tool in groupTools) { sb.append("- `").append(tool.name).append("` — ").appendLine(tool.description) }
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    companion object {
        private val DEFAULT_TOOL_CATALOGUE = """
            ## Available Tools

            ### Touch Gestures
            - `android.click` — Tap by node_id or x,y coordinates.
            - `android.double_click` — Double-tap (for zoom, maps). Provide x,y or node_id.
            - `android.long_click` — Long-press by node_id or x,y.
            - `android.type_text` — Type EXACT text into a field. Use node_id if available.
            - `android.clear_text` — Clear text field.
            - `android.scroll` — Scroll up or down.
            - `android.swipe` — Swipe in any direction.
            - `android.drag` — Drag from point A to B (slower, for moving elements).
            - `android.pinch_zoom` — Pinch to zoom. Provide center_x, center_y, scale (>1=in, <1=out).
            - `android.fling` — Fast fling gesture. Provide direction (up/down/left/right).

            ### Navigation
            - `android.back` — System back.
            - `android.home` — Home screen.
            - `android.recents` — Recent apps.
            - `android.press_key` — Press ENTER, BACK, TAB, SPACE etc.
            - `android.wait` — Wait milliseconds.
            - `android.launch_app` — Launch by app_name or package.

            ### System Controls
            - `android.open_notifications` — Open notification shade.
            - `android.open_quick_settings` — Open quick settings panel.
            - `android.power_menu` — Show power menu.
            - `android.lock_screen` — Lock the screen.
            - `android.split_screen` — Toggle split-screen.
            - `android.volume` — Control volume (direction: up/down/mute, or level: int).

            ### Text Operations
            - `android.select_all` — Select all text in focused field.
            - `android.copy_text` — Copy selected text to clipboard.
            - `android.paste_text` — Paste from clipboard.
            - `android.set_clipboard` — Set clipboard content directly.

            ### Intents
            - `android.open_url` — Open URL in browser.
            - `android.make_call` — Dial a phone number.
            - `android.send_sms` — Open SMS with pre-filled message.
            - `android.share` — Share text via Android share sheet.

            ### Screen
            - `android.screenshot` — Take screenshot.
            - `android.inspect_screen` — Full UI tree dump.
            - `android.find` — Search UI tree with filters.
            - `vision.analyze_screen` — Describe screen visually.
            - `vision.find_visual_target` — Find element by visual description.

            ### Agent
            - `agent.ask_user` — Ask user a question (pauses agent).
            - `agent.confirm` — Request user confirmation.
            - `agent.finish` — Complete task.
            - `agent.stop` — Stop immediately.
            """.trimIndent()
    }
}
