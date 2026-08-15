package com.androidagent.aiagent.tools.android

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

class DragTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            val sx = args["start_x"]?.toString()?.removeSurrounding("\"")?.toFloatOrNull()
            val sy = args["start_y"]?.toString()?.removeSurrounding("\"")?.toFloatOrNull()
            val ex = args["end_x"]?.toString()?.removeSurrounding("\"")?.toFloatOrNull()
            val ey = args["end_y"]?.toString()?.removeSurrounding("\"")?.toFloatOrNull()
            val dur = args["duration_ms"]?.toString()?.removeSurrounding("\"")?.toLongOrNull() ?: 500L
            if (sx == null || sy == null || ex == null || ey == null) {
                return ToolResult(success = false, toolName = TOOL_NAME,
                    error = ToolError(code = "INVALID_INPUT", message = "start_x, start_y, end_x, end_y required"))
            }
            val ok = GestureController.performDrag(service, sx, sy, ex, ey, dur)
            if (ok) ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("from", "${sx},${sy}"); put("to", "${ex},${ey}") })
            else ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "DRAG_FAILED", message = "Drag gesture failed"))
        } catch (e: Exception) {
            ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "DRAG_FAILED", message = e.message ?: "Drag failed"))
        }
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.drag"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Drag from one point to another. Slower than swipe. Provide start_x, start_y, end_x, end_y (floats). Optional duration_ms (long, default 500).",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {
                put("start_x", buildJsonObject { put("type", "number"); put("description", "Start X coordinate") })
                put("start_y", buildJsonObject { put("type", "number"); put("description", "Start Y coordinate") })
                put("end_x", buildJsonObject { put("type", "number"); put("description", "End X coordinate") })
                put("end_y", buildJsonObject { put("type", "number"); put("description", "End Y coordinate") })
                put("duration_ms", buildJsonObject { put("type", "integer"); put("description", "Duration in ms (default 500)") })
            })}, riskLevel = RiskLevel.SAFE, requiresConfirmation = false)
    }
}