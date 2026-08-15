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


class DeleteFileTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val path = args["path"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'path' is required"))
        return try {
            withContext(Dispatchers.IO) {
                val file = java.io.File(path)
                if (!file.exists()) {
                    ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "FILE_NOT_FOUND", message = "File not found: $path"))
                } else {
                    val deleted = file.deleteRecursively()
                    ToolResult(
                        success = deleted, toolName = TOOL_NAME,
                        result = if (deleted) buildJsonObject { put("path", path); put("action", "deleted") } else null,
                        error = if (!deleted) ToolError(code = "DELETE_FAILED", message = "Could not delete: $path") else null,
                        observationRequired = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.delete_file"
        private const val TAG = "DeleteFileTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Delete a file or directory recursively.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "File or directory path to delete") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
        private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "DELETE_FILE_FAILED", message = e.message ?: "Unknown error")
    )
}
