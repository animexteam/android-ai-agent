package com.androidagent.aiagent.tools.android

import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import kotlinx.coroutines.delay

class OpenNotificationsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val ok = service.openNotifications()
        delay(400)
        return if (ok) ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("action", "opened_notifications") })
        else ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "NOTIF_FAILED", message = "Could not open notification shade"))
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.open_notifications"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Open the notification shade to see and interact with notifications.",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false)
    }
}