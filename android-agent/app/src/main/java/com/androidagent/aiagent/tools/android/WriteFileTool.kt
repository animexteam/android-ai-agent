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


class WriteFileTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val path = args["path"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'path' is required"))
        val content = args["content"]?.jsonPrimitive?.content ?: ""
        val append = args["append"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        return try {
            withContext(Dispatchers.IO) {
                val file = if (path.startsWith("/")) java.io.File(path) else java.io.File(service.filesDir, path)
                file.parentFile?.mkdirs()
                if (append) file.appendText(content) else file.writeText(content)
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", file.absolutePath); put("size_bytes", file.length())
                        put("action", if (append) "appended" else "written")
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write file", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.write_file"
        private const val TAG = "WriteFileTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Write text to a file. Create directories as needed. Set append=true to append instead of overwrite.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "File path") })
                    put("content", buildJsonObject { put("type", "string"); put("description", "Content to write") })
                    put("append", buildJsonObject { put("type", "boolean"); put("description", "Append to existing file (default false)") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("path")); add(JsonPrimitive("content")) })
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
        error = ToolError(code = "WRITE_FILE_FAILED", message = e.message ?: "Unknown error")
    )
}
