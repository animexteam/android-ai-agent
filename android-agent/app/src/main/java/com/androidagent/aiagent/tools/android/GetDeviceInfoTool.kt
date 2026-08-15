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

class GetDeviceInfoTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val packageInfo = service.packageManager.getPackageInfo(service.packageName, 0)
                val displayMetrics = service.resources.displayMetrics
                ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("manufacturer", android.os.Build.MANUFACTURER)
                        put("model", android.os.Build.MODEL)
                        put("device", android.os.Build.DEVICE)
                        put("product", android.os.Build.PRODUCT)
                        put("brand", android.os.Build.BRAND)
                        put("android_version", android.os.Build.VERSION.RELEASE)
                        put("sdk_int", android.os.Build.VERSION.SDK_INT)
                        put("screen_width", displayMetrics.widthPixels)
                        put("screen_height", displayMetrics.heightPixels)
                        put("density", displayMetrics.density)
                        put("app_version", packageInfo.versionName ?: "unknown")
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device info", e)
            errorResult(e)
        }
    }

    companion object {
        internal const val TOOL_NAME = "android.get_device_info"
        private const val TAG = "GetDeviceInfoTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Get device information: manufacturer, model, Android version, screen size, etc.",
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
        error = ToolError(code = "DEVICE_INFO_FAILED", message = e.message ?: "Unknown error")
    )
}
