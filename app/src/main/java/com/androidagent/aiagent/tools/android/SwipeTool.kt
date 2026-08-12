package com.androidagent.aiagent.tools.android

import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.accessibility.GestureController
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

class SwipeTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        return try {
            val service = AndroidAgentAccessibilityService.instance
                ?: return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "SERVICE_NOT_CONNECTED",
                        message = "Accessibility service is not connected"
                    )
                )

            val direction = args["direction"]?.toString()?.removeSurrounding("\"") ?: "up"
            if (direction !in VALID_DIRECTIONS) {
                return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "INVALID_INPUT",
                        message = "Direction must be one of $VALID_DIRECTIONS, got: $direction"
                    )
                )
            }

            val durationMs = args["duration_ms"]?.jsonPrimitive?.intOrNull ?: DEFAULT_DURATION_MS
            val clampedDuration = durationMs.coerceIn(50, 2000)

            val explicitStartX = args["startX"]?.jsonPrimitive?.intOrNull
            val explicitStartY = args["startY"]?.jsonPrimitive?.intOrNull
            val explicitEndX = args["endX"]?.jsonPrimitive?.intOrNull
            val explicitEndY = args["endY"]?.jsonPrimitive?.intOrNull

            val metrics = service.resources?.displayMetrics
                ?: return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "DISPLAY_METRICS_UNAVAILABLE",
                        message = "Unable to get display metrics"
                    )
                )

            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val centerX = screenWidth / 2f
            val centerY = screenHeight / 2f
            val edgeMargin = screenWidth * 0.1f
            val verticalMargin = screenHeight * 0.1f

            val (startX, startY, endX, endY) = when {
                explicitStartX != null && explicitStartY != null &&
                    explicitEndX != null && explicitEndY != null -> {
                    Quad(
                        explicitStartX.toFloat(), explicitStartY.toFloat(),
                        explicitEndX.toFloat(), explicitEndY.toFloat()
                    )
                }
                else -> computeSwipeCoords(
                    direction, screenWidth, screenHeight,
                    edgeMargin, verticalMargin
                )
            }

            GestureController.performSwipe(
                startX, startY,
                endX, endY,
                clampedDuration.toLong()
            )

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("direction", direction)
                    put("startX", startX)
                    put("startY", startY)
                    put("endX", endX)
                    put("endY", endY)
                    put("duration_ms", clampedDuration)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform swipe", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "SWIPE_FAILED",
                    message = "Failed to perform swipe: ${e.message}"
                )
            )
        }
    }

    private fun computeSwipeCoords(
        direction: String,
        screenWidth: Int,
        screenHeight: Int,
        edgeMargin: Float,
        verticalMargin: Float
    ): Quad {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        return when (direction) {
            "up" -> Quad(
                centerX, screenHeight - verticalMargin,
                centerX, verticalMargin
            )
            "down" -> Quad(
                centerX, verticalMargin,
                centerX, screenHeight - verticalMargin
            )
            "left" -> Quad(
                screenWidth - edgeMargin, centerY,
                edgeMargin, centerY
            )
            "right" -> Quad(
                edgeMargin, centerY,
                screenWidth - edgeMargin, centerY
            )
            else -> Quad(
                centerX, screenHeight - verticalMargin,
                centerX, verticalMargin
            )
        }
    }

    private data class Quad(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    )

    companion object {
        private const val TOOL_NAME = "android.swipe"
        private const val TAG = "SwipeTool"
        private const val DEFAULT_DURATION_MS = 300
        private val VALID_DIRECTIONS = listOf("up", "down", "left", "right")

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Performs a swipe gesture on the screen in the specified direction. " +
                "Can use predefined directions (up/down/left/right) with automatic coordinate " +
                "calculation, or explicit startX/startY/endX/endY pixel coordinates.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("direction", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("up")
                            add("down")
                            add("left")
                            add("right")
                        })
                        put("description", "Swipe direction")
                    })
                    put("startX", buildJsonObject {
                        put("type", "integer")
                        put("description", "Start X coordinate (optional, overrides direction)")
                    })
                    put("startY", buildJsonObject {
                        put("type", "integer")
                        put("description", "Start Y coordinate (optional, overrides direction)")
                    })
                    put("endX", buildJsonObject {
                        put("type", "integer")
                        put("description", "End X coordinate (optional, overrides direction)")
                    })
                    put("endY", buildJsonObject {
                        put("type", "integer")
                        put("description", "End Y coordinate (optional, overrides direction)")
                    })
                    put("duration_ms", buildJsonObject {
                        put("type", "integer")
                        put("description", "Swipe duration in milliseconds (default 300)")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
