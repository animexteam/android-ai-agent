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

class FlingTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            val direction = args["direction"]?.toString()?.removeSurrounding("\"")?.lowercase() ?: "up"
            val dist = args["distance"]?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: 800
            val w = service.resources.displayMetrics.widthPixels
            val h = service.resources.displayMetrics.heightPixels
            val cx = w / 2f; val cy = h / 2f
            val (sx, sy, ex, ey) = when {
                direction.contains("up") -> listOf(cx, (cy + dist / 2f), cx, (cy - dist / 2f))
                direction.contains("down") -> listOf(cx, (cy - dist / 2f), cx, (cy + dist / 2f))
                direction.contains("left") -> listOf((cx + dist / 2f), cy, (cx - dist / 2f), cy)
                direction.contains("right") -> listOf((cx - dist / 2f), cy, (cx + dist / 2f), cy)
                else -> listOf(cx, (cy + dist / 2f), cx, (cy - dist / 2f))
            }
            val ok = GestureController.performFling(service, sx.toFloat(), sy.toFloat(), ex.toFloat(), ey.toFloat())
            if (ok) ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("direction", direction) })
            else ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "FLING_FAILED", message = "Fling failed"))
        } catch (e: Exception) {
            ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "FLING_FAILED", message = e.message ?: "Fling failed"))
        }
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    companion object {
        internal const val TOOL_NAME = "android.fling"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Fast fling/throw gesture in a direction (up/down/left/right). Like a quick swipe. Optional distance (int, default 800 pixels).",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {
                put("direction", buildJsonObject { put("type", "string"); put("description", "up, down, left, or right") })
                put("distance", buildJsonObject { put("type", "integer"); put("description", "Distance in pixels (default 800)") })
            })}, riskLevel = RiskLevel.SAFE, requiresConfirmation = false)
    }
}