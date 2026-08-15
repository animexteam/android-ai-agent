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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ForceStopAppTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance
            ?: return noService()

        val packageName = args["package_name"]?.jsonPrimitive?.content
            ?: return ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'package_name' is required")
            )

        return try {
            service.packageManager.getPackageInfo(packageName, 0)
            // Need to use ActivityManager to force stop, but since we don't have direct
            // access, we use the service context
            val am = service.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (am != null) {
                am.forceStopPackage(packageName)
                ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("package", packageName)
                        put("action", "force_stopped")
                    }
                )
            } else {
                ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(code = "NO_ACTIVITY_MANAGER", message = "ActivityManager not available")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force stop $packageName", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "FORCE_STOP_FAILED", message = "Failed to force stop '$packageName': ${e.message}")
            )
        }
    }

    private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    companion object {
        internal const val TOOL_NAME = "android.force_stop_app"
        private const val TAG = "ForceStopAppTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Force-stop a running app by package name. The app will be killed immediately.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("package_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Package name of the app to force stop")
                    })
                })
                put("required", buildJsonArray { add("package_name") })
            },
            riskLevel = RiskLevel.CONFIRM,
            requiresConfirmation = true
        )
    }
}