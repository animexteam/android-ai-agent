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
import android.provider.AlarmClock

class SetTimerTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val seconds = args["seconds"]?.jsonPrimitive?.content?.toIntOrNull()
        val message = args["message"]?.jsonPrimitive?.content
        if (seconds == null || seconds <= 0)
            return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'seconds' (positive integer) is required"))
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("seconds", seconds); put("message", message ?: ""); put("action", "timer_set") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timer", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.set_timer"
        private const val TAG = "SetTimerTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Set a countdown timer via the system Clock app.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("seconds", buildJsonObject { put("type", "integer"); put("description", "Timer duration in seconds") })
                    put("message", buildJsonObject { put("type", "string"); put("description", "Optional timer label") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("seconds")) })
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
        error = ToolError(code = "SET_TIMER_FAILED", message = e.message ?: "Unknown error")
    )
}
