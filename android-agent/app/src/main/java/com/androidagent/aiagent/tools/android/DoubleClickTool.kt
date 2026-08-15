package com.androidagent.aiagent.tools.android

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.accessibility.GestureController
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class DoubleClickTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        return try {
            val service = AndroidAgentAccessibilityService.instance
                ?: return noService()

            val nodeId = args["node_id"]?.toString()?.removeSurrounding("\"")
            val x = args["x"]?.jsonPrimitive?.intOrNull
            val y = args["y"]?.jsonPrimitive?.intOrNull

            when {
                !nodeId.isNullOrBlank() -> doubleClickByNodeId(service, nodeId)
                x != null && y != null -> doubleClickByCoords(x, y)
                else -> ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "INVALID_INPUT",
                        message = "Either 'node_id' or both 'x' and 'y' must be provided"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform double click", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "DOUBLE_CLICK_FAILED", message = "Failed to double click: ${e.message}")
            )
        }
    }

    private fun doubleClickByNodeId(service: AndroidAgentAccessibilityService, nodeId: String): ToolResult {
        val rootNode = service.safeGetRootInActiveWindow()
        val targetNode = findNodeByHashId(rootNode, nodeId)

        if (targetNode != null) {
            val b = Rect()
            targetNode.getBoundsInScreen(b)
            val cx = ((b.left + b.right) / 2f)
            val cy = ((b.top + b.bottom) / 2f)
            val ok = GestureController.performDoubleTap(cx, cy)
            return if (ok) {
                ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("nodeId", nodeId)
                        put("doubleClickedX", (b.left + b.right) / 2)
                        put("doubleClickedY", (b.top + b.bottom) / 2)
                        put("method", "gesture")
                    }
                )
            } else {
                ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(code = "DOUBLE_CLICK_FAILED", message = "Double tap gesture failed on node '$nodeId'")
                )
            }
        }

        return ToolResult(
            success = false,
            toolName = TOOL_NAME,
            error = ToolError(
                code = "NODE_NOT_FOUND",
                message = "Node '$nodeId' not found. Screen may have changed."
            )
        )
    }

    private fun doubleClickByCoords(x: Int, y: Int): ToolResult {
        val ok = GestureController.performDoubleTap(x.toFloat(), y.toFloat())
        return if (ok) {
            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("doubleClickedX", x)
                    put("doubleClickedY", y)
                    put("method", "coordinate")
                }
            )
        } else {
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "DOUBLE_CLICK_FAILED", message = "Double tap gesture failed at ($x, $y)")
            )
        }
    }

    private fun findNodeByHashId(
        root: AccessibilityNodeInfo?,
        nodeId: String
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val hashStr = nodeId.removePrefix("node_")
        val targetHash = hashStr.toIntOrNull()
        return if (targetHash != null) {
            findByHashCode(root, targetHash)
        } else {
            findByViewId(root, nodeId)
        }
    }

    private fun findByHashCode(
        node: AccessibilityNodeInfo,
        targetHash: Int
    ): AccessibilityNodeInfo? {
        if (node.hashCode() == targetHash) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByHashCode(child, targetHash)
            if (found != null) return found
        }
        return null
    }

    private fun findByViewId(
        node: AccessibilityNodeInfo,
        viewId: String
    ): AccessibilityNodeInfo? {
        if (node.viewIdResourceName == viewId) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByViewId(child, viewId)
            if (found != null) return found
        }
        return null
    }

    private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    companion object {
        internal const val TOOL_NAME = "android.double_click"
        private const val TAG = "DoubleClickTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Double-tap at coordinates or on a node. Used for zooming in maps, images, etc.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("node_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Node ID of the element to double-tap")
                    })
                    put("x", buildJsonObject {
                        put("type", "integer")
                        put("description", "X coordinate to double-tap (fallback)")
                    })
                    put("y", buildJsonObject {
                        put("type", "integer")
                        put("description", "Y coordinate to double-tap (fallback)")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
