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
import android.app.Notification
import android.app.NotificationManager

class GetNotificationsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val nm = service.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? NotificationManager
                val activeNotifications = nm?.activeNotifications ?: arrayOf()
                val notifs = buildJsonArray {
                    for (sbNotif in activeNotifications) {
                        val extras = sbNotif.notification?.extras
                        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                        val pkg = sbNotif.packageName
                        val key = sbNotif.key
                        if (title.isNotEmpty() || text.isNotEmpty()) {
                            add(buildJsonObject {
                                put("package", pkg); put("key", key)
                                put("title", title); put("text", text)
                            })
                        }
                    }
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("notifications", notifs); put("count", notifs.size) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get notifications", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_notifications"
        private const val TAG = "GetNotificationsTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get currently active notifications with titles and text.",
            inputSchema = buildJsonObject { put("type", "object") },
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
        error = ToolError(code = "GET_NOTIFICATIONS_FAILED", message = e.message ?: "Unknown error")
    )
}
