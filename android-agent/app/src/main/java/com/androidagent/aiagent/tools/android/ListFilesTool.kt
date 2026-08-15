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


class ListFilesTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val path = args["path"]?.jsonPrimitive?.content
            ?: android.os.Environment.getExternalStorageDirectory().absolutePath
        return try {
            withContext(Dispatchers.IO) {
                val dir = java.io.File(path)
                if (!dir.exists() || !dir.isDirectory)
                    return ToolResult(success = false, toolName = TOOL_NAME,
                        error = ToolError(code = "DIR_NOT_FOUND", message = "Directory not found: $path"))
                val items = buildJsonArray {
                    val files = (dir.listFiles() ?: emptyArray()).sortedBy { it.name.lowercase() }.take(100)
                    for (f in files) {
                        add(buildJsonObject {
                            put("name", f.name)
                            put("is_directory", f.isDirectory)
                            put("size_bytes", if (f.isFile) f.length() else 0)
                            put("last_modified", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(f.lastModified())))
                        })
                    }
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("path", dir.absolutePath); put("items", items)
                        put("total", dir.listFiles()?.size ?: 0)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.list_files"
        private const val TAG = "ListFilesTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "List files and directories at a given path. Defaults to external storage root.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "Directory path (default: external storage root)") })
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
        error = ToolError(code = "LIST_FILES_FAILED", message = e.message ?: "Unknown error")
    )
}
