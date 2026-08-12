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
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive

class ScrollTool : ToolHandler {

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

            val direction = args["direction"]?.toString()?.removeSurrounding("\"") ?: "down"
            if (direction != "down" && direction != "up") {
                return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "INVALID_INPUT",
                        message = "Direction must be 'up' or 'down', got: $direction"
                    )
                )
            }

            val amount = args["amount"]?.jsonPrimitive?.content?.toFloatOrNull() ?: DEFAULT_AMOUNT
            val clampedAmount = amount.coerceIn(0.1f, 1.0f)

            val nodeId = args["node_id"]?.toString()?.removeSurrounding("\"")

            if (!nodeId.isNullOrBlank()) {
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
                            message = "Node with id '$nodeId' not found in current UI tree"
                        )
                    )
                }

                val rootNode = service.rootInActiveWindow
                val targetNode = findNodeById(rootNode, node.nodeId)

                if (targetNode != null && targetNode.isScrollable) {
                    val scrollAction = if (direction == "down") {
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    } else {
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    }

                    val scrollResult = targetNode.performAction(scrollAction)

                    if (scrollResult) {
                        delay(SCROLL_SETTLE_DELAY_MS)
                        return ToolResult(
                            success = true,
                            toolName = TOOL_NAME,
                            result = buildJsonObject {
                                put("nodeId", node.nodeId)
                                put("direction", direction)
                                put("method", "accessibility_scroll")
                            }
                        )
                    }
                    // If accessibility scroll failed, fall through to gesture
                }
            }

            // Fallback: use gesture-based swipe
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
            val scrollDistance = screenHeight * clampedAmount

            val startY = if (direction == "down") {
                screenHeight * 0.7f
            } else {
                screenHeight * 0.3f
            }
            val endY = if (direction == "down") {
                screenHeight * 0.3f
            } else {
                screenHeight * 0.7f
            }

            GestureController.performSwipe(
                centerX, startY,
                centerX, endY,
                SWIPE_DURATION_MS
            )

            delay(SCROLL_SETTLE_DELAY_MS)

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("direction", direction)
                    put("amount", clampedAmount)
                    put("method", "gesture_swipe")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scroll", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "SCROLL_FAILED",
                    message = "Failed to scroll: ${e.message}"
                )
            )
        }
    }

    private fun findNodeById(node: AccessibilityNodeInfo?, nodeId: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.viewIdResourceName == nodeId || node.hashCode().toString() == nodeId) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeById(child, nodeId)
            if (found != null) return found
        }
        return null
    }

    companion object {
        internal const val TOOL_NAME = "android.scroll"
        private const val TAG = "ScrollTool"
        private const val DEFAULT_AMOUNT = 0.7f
        private const val SWIPE_DURATION_MS = 300L
        private const val SCROLL_SETTLE_DELAY_MS = 300L

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Scrolls a scrollable container or the screen in the specified direction. " +
                "If node_id is provided, attempts to use accessibility scroll on that node. " +
                "Otherwise performs a gesture-based swipe on the screen center.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("direction", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("down")
                            add("up")
                        })
                        put("description", "Scroll direction")
                    })
                    put("amount", buildJsonObject {
                        put("type", "number")
                        put("description", "Scroll amount as fraction of screen height (0.0-1.0, default 0.7)")
                    })
                    put("node_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional node ID of the scrollable container")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
