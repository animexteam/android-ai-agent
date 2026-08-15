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

import android.service.notification.StatusBarNotification

class DismissNotificationTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val key = args["key"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'key' (notification key) is required")
        return try {
            withContext(Dispatchers.IO) {
                val nm = service.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                val sbNotifs = nm?.activeNotifications ?: arrayOf()
                var dismissed = false
                for (sbNotif in sbNotifs) {
                    if (sbNotif.key == key) {
                        sbNotif.notification?.let { nm.cancel(sbNotif.id, sbNotif.notification.tag ?: "") }
                        dismissed = true
                        break
                    }
                }
                ToolResult(
                    success = dismissed, toolName = TOOL_NAME,
                    result = if (dismissed) buildJsonObject { put("key", key); put("action", "dismissed") } else null,
                    error = if (!dismissed) ToolError(code = "NOT_FOUND", message = "Notification key not found: $key") else null,
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss notification", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.dismiss_notification"
        private const val TAG = "DismissNotificationTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Dismiss a notification by its key (obtained from android.get_notifications).",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("key", buildJsonObject { put("type", "string"); put("description", "Notification key to dismiss") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("key")) })
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
        error = ToolError(code = "DISMISS_FAILED", message = e.message ?: "Unknown error")
    )
}
