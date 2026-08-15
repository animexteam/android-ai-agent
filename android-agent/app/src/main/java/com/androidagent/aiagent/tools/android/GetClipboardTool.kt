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

import android.content.ClipData
import android.content.ClipboardManager

class GetClipboardTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val cm = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip
                val text = if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(service).toString()
                } else null
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("has_content", text != null)
                        put("content", text ?: "")
                        put("length", text?.length ?: 0)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get clipboard", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_clipboard"
        private const val TAG = "GetClipboardTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Read the current clipboard content.",
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
        error = ToolError(code = "CLIPBOARD_READ_FAILED", message = e.message ?: "Unknown error")
    )
}
