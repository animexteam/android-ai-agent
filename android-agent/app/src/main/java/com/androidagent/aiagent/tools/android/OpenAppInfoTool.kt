package com.androidagent.aiagent.tools.android

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class OpenAppInfoTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance
            ?: return noService()

        val packageName = args["package_name"]?.jsonPrimitive?.content
            ?: return ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'package_name' is required")
            )

        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("package", packageName)
                    put("action", "opened_app_info")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app info for $packageName", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "OPEN_APP_INFO_FAILED", message = "Could not open app info: ${e.message}")
            )
        }
    }

    private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    companion object {
        internal const val TOOL_NAME = "android.open_app_info"
        private const val TAG = "OpenAppInfoTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Open the system App Info settings page for a given package name. Useful for clearing cache, data, or checking permissions.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("package_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Package name of the app")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("package_name")) })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}