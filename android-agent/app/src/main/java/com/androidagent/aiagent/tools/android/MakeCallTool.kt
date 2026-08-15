package com.androidagent.aiagent.tools.android

import android.content.Intent
import android.net.Uri
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MakeCallTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val number = args["number"]?.toString()?.removeSurrounding("\"")
        if (number.isNullOrBlank()) return ToolResult(success = false, toolName = TOOL_NAME,
            error = ToolError(code = "INVALID_INPUT", message = "'number' is required"))
        return try {
            service.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("number", number) })
        } catch (e: Exception) {
            ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "CALL_FAILED", message = e.message ?: "Could not make call"))
        }
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.make_call"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Make a phone call. Provide 'number' (string). Requires CALL_PHONE permission.",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {
                put("number", buildJsonObject { put("type", "string"); put("description", "Phone number to call") })
            })}, riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true)
    }
}