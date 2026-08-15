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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat

@Suppress("DEPRECATION")
class SendNotificationTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val title = args["title"]?.jsonPrimitive?.content ?: "Android-Use"
        val text = args["text"]?.jsonPrimitive?.content ?: ""
        return try {
            withContext(Dispatchers.IO) {
                val nm = service.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? NotificationManager
                val channelId = "agent_custom"
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(channelId, "Agent", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Notifications from Android-Use agent"
                    }
                    nm?.createNotificationChannel(channel)
                }
                val notifId = (System.currentTimeMillis() % 100000).toInt()
                val intent = service.packageManager.getLaunchIntentForPackage(service.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pi = PendingIntent.getActivity(service, notifId, intent, PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(service, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                nm?.notify(notifId, notification)
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("title", title); put("text", text); put("id", notifId) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.send_notification"
        private const val TAG = "SendNotificationTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Post a notification to the device notification bar with a title and text.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("title", buildJsonObject { put("type", "string"); put("description", "Notification title") })
                    put("text", buildJsonObject { put("type", "string"); put("description", "Notification body text") })
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
        error = ToolError(code = "SEND_NOTIFICATION_FAILED", message = e.message ?: "Unknown error")
    )
}
