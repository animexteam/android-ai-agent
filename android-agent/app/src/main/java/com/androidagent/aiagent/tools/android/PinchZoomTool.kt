package com.androidagent.aiagent.tools.android

import android.os.Build
import android.util.Log
import com.androidagent.aiagent.accessibility.GestureController
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PinchZoomTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "UNSUPPORTED", message = "Pinch zoom requires Android 11+"))
        }
        return try {
            val cx = args["center_x"]?.toString()?.removeSurrounding("\"")?.toFloatOrNull()
            val cy = args["center_y"]?.toString()?.removeSurrounding("\"")?.toFloatOrNull()
            val scale = args["scale"]?.toString()?.removeSurrounding("\"")?.toFloatOrNull() ?: 0.5f
            val dur = args["duration_ms"]?.toString()?.removeSurrounding("\"")?.toLongOrNull() ?: 400L
            if (cx == null || cy == null) {
                return ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "INVALID_INPUT", message = "center_x, center_y required"))
            }
            val ok = GestureController.performPinchZoom(service, cx, cy, scale, dur)
            if (ok) ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("scale", scale) })
            else ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "PINCH_FAILED", message = "Pinch gesture failed"))
        } catch (e: Exception) {
            ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "PINCH_FAILED", message = e.message ?: "Pinch failed"))
        }
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.pinch_zoom"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Pinch to zoom in/out on screen. Provide center_x, center_y (floats). scale: >1 = zoom in, <1 = zoom out (default 0.5). Optional duration_ms.",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {
                put("center_x", buildJsonObject { put("type", "number"); put("description", "Center X") })
                put("center_y", buildJsonObject { put("type", "number"); put("description", "Center Y") })
                put("scale", buildJsonObject { put("type", "number"); put("description", "Zoom factor: >1 zoom in, <1 zoom out") })
                put("duration_ms", buildJsonObject { put("type", "integer"); put("description", "Duration (default 400)") })
            })}, riskLevel = RiskLevel.SAFE, requiresConfirmation = false)
    }
}