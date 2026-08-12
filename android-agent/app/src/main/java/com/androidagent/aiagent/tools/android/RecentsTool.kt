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

class RecentsTool : ToolHandler {

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

            service.pressRecents()

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("action", "press_recents")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to press recents", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "RECENTS_FAILED",
                    message = "Failed to press recents: ${e.message}"
                )
            )
        }
    }

    companion object {
        private const val TOOL_NAME = "android.recents"
        private const val TAG = "RecentsTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Presses the recents (overview/task switcher) button to show recently used apps.",
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
