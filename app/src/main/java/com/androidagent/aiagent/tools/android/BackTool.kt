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

class BackTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        return try {
            val service = AndroidAgentAccessibilityService.instance
                ?: return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "SERVICE_NOT_CONNECTED",
                        message = "Accessibility service is not connected"
                    )
                )

            service.pressBack()

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("action", "press_back")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to press back", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "BACK_FAILED",
                    message = "Failed to press back: ${e.message}"
                )
            )
        }
    }

    companion object {
        internal const val TOOL_NAME = "android.back"
        private const val TAG = "BackTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Presses the system back button to navigate to the previous screen.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    // No inputs required
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
