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
import android.provider.Settings

class ClearAppDataTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val packageName = args["package_name"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'package_name' is required"))
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("package", packageName); put("action", "opened_app_settings_for_clear_data") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.clear_app_data"
        private const val TAG = "ClearAppDataTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Open app settings page for clearing app data/cache.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("package_name", buildJsonObject { put("type", "string"); put("description", "Package name") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("package_name")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
        private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "CLEAR_DATA_FAILED", message = e.message ?: "Unknown error")
    )
}
