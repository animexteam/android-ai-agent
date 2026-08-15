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

class SplitScreenTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val ok = service.toggleSplitScreen()
        return if (ok) ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("action", "split_screen") })
        else ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SPLIT_FAILED", message = "Could not toggle split screen"))
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.split_screen"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Toggle split-screen mode.",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false)
    }
}