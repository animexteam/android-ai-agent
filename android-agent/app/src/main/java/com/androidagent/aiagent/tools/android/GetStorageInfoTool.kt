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

import android.os.Environment
import android.os.StatFs

class GetStorageInfoTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val path = Environment.getExternalStorageDirectory()
                val stat = StatFs(path.absolutePath)
                val total = stat.totalBytes
                val available = stat.availableBytes
                val used = total - available
                val totalGB = String.format("%.2f", total / (1024.0 * 1024.0 * 1024.0))
                val availableGB = String.format("%.2f", available / (1024.0 * 1024.0 * 1024.0))
                val usedGB = String.format("%.2f", used / (1024.0 * 1024.0 * 1024.0))
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("total_gb", totalGB)
                        put("used_gb", usedGB)
                        put("available_gb", availableGB)
                        put("used_percent", (used * 100 / total).toInt())
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get storage info", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_storage_info"
        private const val TAG = "GetStorageInfoTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get device storage information: total, used, and available space.",
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
        error = ToolError(code = "STORAGE_INFO_FAILED", message = e.message ?: "Unknown error")
    )
}
