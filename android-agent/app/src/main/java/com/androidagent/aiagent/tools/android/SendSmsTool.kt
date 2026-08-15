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

class SendSmsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val number = args["number"]?.toString()?.removeSurrounding("\"")
        val message = args["message"]?.toString()?.removeSurrounding("\"")
        if (number.isNullOrBlank()) return ToolResult(success = false, toolName = TOOL_NAME,
            error = ToolError(code = "INVALID_INPUT", message = "'number' is required"))
        return try {
            val uri = Uri.parse("smsto:$number")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message ?: "")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("number", number) })
        } catch (e: Exception) {
            ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SMS_FAILED", message = e.message ?: "Could not send SMS"))
        }
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.send_sms"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Open SMS app with a pre-filled message. Provide 'number' (string), optional 'message' (string).",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {
                put("number", buildJsonObject { put("type", "string"); put("description", "Phone number") })
                put("message", buildJsonObject { put("type", "string"); put("description", "SMS body text") })
            })}, riskLevel = RiskLevel.SENSITIVE, requiresConfirmation = true)
    }
}