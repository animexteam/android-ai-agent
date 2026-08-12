package com.androidagent.aiagent.tools.android

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.androidagent.aiagent.accessibility.AccessibilityObserver
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.accessibility.GestureController
import com.androidagent.aiagent.agent.AndroidObservation
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull

class ClickTool : ToolHandler {

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

            val nodeId = args["node_id"]?.toString()?.removeSurrounding("\"")
            val x = args["x"]?.jsonPrimitive?.intOrNull
            val y = args["y"]?.jsonPrimitive?.intOrNull

            when {
                !nodeId.isNullOrBlank() -> {
                    val observer = AccessibilityObserver.instance
                        ?: return ToolResult(
                            success = false,
                            toolName = TOOL_NAME,
                            error = ToolError(
                                code = "OBSERVER_NOT_AVAILABLE",
                                message = "Accessibility observer is not available"
                            )
                        )

                    val observation = observer.observe()
                    val node = observation.uiTree.find { it.nodeId == nodeId }

                    if (node == null) {
                        return ToolResult(
                            success = false,
                            toolName = TOOL_NAME,
                            error = ToolError(
                                code = "NODE_NOT_FOUND",
                                message = "Node with id '$nodeId' not found in current UI tree. " +
                                    "The UI may have changed; consider re-observing."
                            )
                        )
                    }

                    val centerX = node.bounds.centerX
                    val centerY = node.bounds.centerY

                    GestureController.performTap(centerX.toFloat(), centerY.toFloat())

                    ToolResult(
                        success = true,
                        toolName = TOOL_NAME,
                        result = buildJsonObject {
                            put("nodeId", node.nodeId)
                            put("text", node.text ?: "")
                            put("tappedX", centerX)
                            put("tappedY", centerY)
                            put("method", "gesture")
                        }
                    )
                }
                x != null && y != null -> {
                    GestureController.performTap(x.toFloat(), y.toFloat())

                    ToolResult(
                        success = true,
                        toolName = TOOL_NAME,
                        result = buildJsonObject {
                            put("tappedX", x)
                            put("tappedY", y)
                            put("method", "coordinate")
                        }
                    )
                }
                else -> {
                    ToolResult(
                        success = false,
                        toolName = TOOL_NAME,
                        error = ToolError(
                            code = "INVALID_INPUT",
                            message = "Either 'node_id' or both 'x' and 'y' must be provided"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform click", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "CLICK_FAILED",
                    message = "Failed to perform click: ${e.message}"
                )
            )
        }
    }

    companion object {
        private const val TOOL_NAME = "android.click"
        private const val TAG = "ClickTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Clicks on a UI element identified by node_id, or at specific x,y coordinates. " +
                "Prefer using node_id for reliability. Use x,y only as a visual fallback " +
                "when the element cannot be found in the accessibility tree.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("observation_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional observation snapshot ID for validation")
                    })
                    put("node_id", buildJsonObject {
                        put("type", "string")
                        put("description", "The node ID of the element to click (from find or inspect_screen)")
                    })
                    put("x", buildJsonObject {
                        put("type", "integer")
                        put("description", "X coordinate to tap (fallback, use with y)")
                    })
                    put("y", buildJsonObject {
                        put("type", "integer")
                        put("description", "Y coordinate to tap (fallback, use with x)")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
