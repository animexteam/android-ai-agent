package com.androidagent.aiagent.tools.android

import android.content.Intent
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ShareContentTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val text = args["text"]?.toString()?.removeSurrounding("\"")
        if (text.isNullOrBlank()) return ToolResult(success = false, toolName = TOOL_NAME,
            error = ToolError(code = "INVALID_INPUT", message = "'text' is required"))
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(Intent.createChooser(intent, "Share via").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("text", text.take(100)) })
        } catch (e: Exception) {
            ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SHARE_FAILED", message = e.message ?: "Could not share"))
        }
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.share"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Share text via Android share sheet. Provide 'text' (string).",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {
                put("text", buildJsonObject { put("type", "string"); put("description", "Text to share") })
            })}, riskLevel = RiskLevel.SAFE, requiresConfirmation = false)
    }
}