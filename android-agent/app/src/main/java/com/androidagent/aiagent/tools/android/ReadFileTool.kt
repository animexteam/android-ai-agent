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


class ReadFileTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val path = args["path"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'path' is required"))
        val maxChars = args["max_chars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5000
        return try {
            withContext(Dispatchers.IO) {
                val file = if (path.startsWith("/")) java.io.File(path) else java.io.File(service.filesDir, path)
                if (!file.exists())
                    return ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "FILE_NOT_FOUND", message = "File not found: $path"))
                val text = file.readText().take(maxChars)
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", file.absolutePath); put("size_bytes", file.length())
                        put("content", text); put("truncated", text.length >= maxChars)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.read_file"
        private const val TAG = "ReadFileTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Read a text file from device storage. Supports app-private files and (with permissions) shared storage.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "Absolute or relative file path") })
                    put("max_chars", buildJsonObject { put("type", "integer"); put("description", "Max characters to read (default 5000)") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
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
        error = ToolError(code = "READ_FILE_FAILED", message = e.message ?: "Unknown error")
    )
}
