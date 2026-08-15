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

import android.app.ActivityManager

@Suppress("DEPRECATION")
class GetRunningAppsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val am = service.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? ActivityManager
                val processes = am?.runningAppProcesses ?: emptyList()
                val apps = buildJsonArray {
                    for (proc in processes.sortedByDescending { it.importance }) {
                        add(buildJsonObject {
                            put("package", proc.processName)
                            put("importance", when (proc.importance) {
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND -> "background"
                                else -> "other"
                            })
                        })
                    }
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("running_apps", apps); put("count", apps.size) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get running apps", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_running_apps"
        private const val TAG = "GetRunningAppsTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get list of currently running apps/processes with their importance level.",
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
        error = ToolError(code = "RUNNING_APPS_FAILED", message = e.message ?: "Unknown error")
    )
}
