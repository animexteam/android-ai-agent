package com.androidagent.aiagent.tools.android

import android.content.Intent
import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetAppListTool : ToolHandler {

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

            val pm = service.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)

            val appsArray = buildJsonArray {
                resolveInfos
                    .sortedBy { it.loadLabel(pm).toString().lowercase() }
                    .forEach { resolveInfo ->
                        add(buildJsonObject {
                            put("name", resolveInfo.loadLabel(pm).toString())
                            put("package", resolveInfo.activityInfo.packageName)
                        })
                    }
            }

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("count", resolveInfos.size)
                    put("apps", appsArray)
                },
                observationRequired = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app list", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "GET_APP_LIST_FAILED",
                    message = "Failed to get app list: ${e.message}"
                )
            )
        }
    }

    companion object {
        internal const val TOOL_NAME = "android.get_app_list"
        private const val TAG = "GetAppListTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Lists all installed apps with display names and package names. Use to discover available apps before launching.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
