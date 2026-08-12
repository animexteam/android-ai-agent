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

class HomeTool : ToolHandler {

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

            service.pressHome()

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("action", "press_home")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to press home", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "HOME_FAILED",
                    message = "Failed to press home: ${e.message}"
                )
            )
        }
    }

    companion object {
        private const val TOOL_NAME = "android.home"
        private const val TAG = "HomeTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Presses the system home button to return to the home screen.",
            inputSchema = buildJsonObject {
                put("type", "object")
                addJsonObject("properties") {
                    // No inputs required
                }
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
