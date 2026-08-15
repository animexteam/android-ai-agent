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

import android.content.Intent
import android.provider.MediaStore

class OpenCameraTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val mode = args["mode"]?.jsonPrimitive?.content
        return try {
            val intent = when (mode?.lowercase()) {
                "video" -> Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                else -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            service.startActivity(intent)
            ToolResult(
                success = true, toolName = TOOL_NAME,
                result = buildJsonObject { put("mode", mode ?: "photo"); put("action", "camera_opened") },
                observationRequired = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.open_camera"
        private const val TAG = "OpenCameraTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Open the device camera app. Mode: 'photo' (default) or 'video'.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("mode", buildJsonObject { put("type", "string"); put("description", "'photo' or 'video'") })
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
        error = ToolError(code = "CAMERA_FAILED", message = e.message ?: "Unknown error")
    )
}
