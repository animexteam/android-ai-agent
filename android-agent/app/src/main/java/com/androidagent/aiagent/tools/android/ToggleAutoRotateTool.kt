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

import android.provider.Settings

class ToggleAutoRotateTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val state = args["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
        return try {
            withContext(Dispatchers.IO) {
                if (state != null) {
                    Settings.System.putInt(service.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (state) 1 else 0)
                } else {
                    val current = Settings.System.getInt(service.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(service.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (current == 1) 0 else 1)
                }
                val final = Settings.System.getInt(service.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("auto_rotate", final) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle auto-rotate", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.toggle_auto_rotate"
        private const val TAG = "ToggleAutoRotateTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Toggle auto-rotate screen or set to on/off.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("enabled", buildJsonObject { put("type", "boolean"); put("description", "Set to true/false. Omit to toggle.") })
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
        error = ToolError(code = "AUTO_ROTATE_FAILED", message = e.message ?: "Unknown error")
    )
}
