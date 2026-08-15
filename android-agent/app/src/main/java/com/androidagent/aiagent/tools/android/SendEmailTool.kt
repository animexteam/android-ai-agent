package com.androidagent.aiagent.tools.android

import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import android.content.Intent
import android.net.Uri

class SendEmailTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val to = args["to"]?.jsonPrimitive?.content
        val subject = args["subject"]?.jsonPrimitive?.content ?: ""
        val body = args["body"]?.jsonPrimitive?.content ?: ""
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$to")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(Intent.createChooser(intent, "Send email").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("to", to ?: ""); put("subject", subject); put("action", "email_chooser_opened") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.send_email"
        private const val TAG = "SendEmailTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Open email composer with pre-filled to, subject, and body.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("to", buildJsonObject { put("type", "string"); put("description", "Recipient email address") })
                    put("subject", buildJsonObject { put("type", "string"); put("description", "Email subject") })
                    put("body", buildJsonObject { put("type", "string"); put("description", "Email body text") })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
        private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "EMAIL_FAILED", message = e.message ?: "Unknown error")
    )
}
