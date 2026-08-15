package com.androidagent.aiagent.tools.android

import android.provider.Settings
import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SetBrightnessTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance
            ?: return noService()

        val brightness = args["brightness"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'brightness' (0-255) is required")
            )

        return try {
            Settings.System.putInt(service.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness.coerceIn(0, 255))
            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject { put("brightness", brightness) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "BRIGHTNESS_FAILED", message = "Failed: ${e.message}")
            )
        }
    }

    private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    companion object {
        internal const val TOOL_NAME = "android.set_brightness"
        private const val TAG = "SetBrightnessTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Set screen brightness (0-255). 0=darkest, 255=brightest.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("brightness", buildJsonObject {
                        put("type", "integer")
                        put("description", "Brightness level 0-255")
                    })
                })
                put("required", buildJsonArray { add("brightness") })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
