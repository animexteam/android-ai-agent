package com.androidagent.aiagent.tools.android

import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SetClipboardTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val text = args["text"]?.toString()?.removeSurrounding("\"")
        if (text.isNullOrBlank()) return ToolResult(success = false, toolName = TOOL_NAME,
            error = ToolError(code = "INVALID_INPUT", message = "'text' is required"))
        val clipboard = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            ?: return ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "NO_CLIPBOARD", message = "Clipboard not available"))
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("agent", text))
        return ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("text", text); put("action", "clipboard_set") })
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.set_clipboard"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Set clipboard content directly without needing a field. Provide 'text' (string).",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {
                put("text", buildJsonObject { put("type", "string"); put("description", "Text to put on clipboard") })
            })}, riskLevel = RiskLevel.SAFE, requiresConfirmation = false)
    }
}