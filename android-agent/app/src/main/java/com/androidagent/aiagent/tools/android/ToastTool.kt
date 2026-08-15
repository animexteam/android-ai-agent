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

import android.widget.Toast

class ToastTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val message = args["message"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'message' is required"))
        return try {
            withContext(Dispatchers.Main) {
                Toast.makeText(service, message, Toast.LENGTH_LONG).show()
            }
            kotlinx.coroutines.delay(500)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("message", message); put("action", "toast_shown") },
                observationRequired = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.toast"
        private const val TAG = "ToastTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Show a brief toast message on screen.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("message", buildJsonObject { put("type", "string"); put("description", "Message to show") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("message")) })
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
        error = ToolError(code = "TOAST_FAILED", message = e.message ?: "Unknown error")
    )
}
