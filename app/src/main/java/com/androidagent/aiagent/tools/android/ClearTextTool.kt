package com.androidagent.aiagent.tools.android

import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.androidagent.aiagent.accessibility.AccessibilityObserver
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.agent.AndroidObservation
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ClearTextTool : ToolHandler {

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

                if (targetNode != null) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val bundle = android.os.Bundle()
                    bundle.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        ""
                    )
                    val clearResult = targetNode.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        bundle
                    )

                    if (clearResult) {
                        return ToolResult(
                            success = true,
                            toolName = TOOL_NAME,
                            result = buildJsonObject {
                                put("nodeId", node.nodeId)
                                put("method", "set_text_empty")
                            }
                        )
                    }
                }
            }

            // Fallback: select all and delete via key events
            clearViaKeyEvents(service)

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("method", "key_events")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear text", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "CLEAR_TEXT_FAILED",
                    message = "Failed to clear text: ${e.message}"
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

    private suspend fun clearViaKeyEvents(service: AndroidAgentAccessibilityService) {
        withContext(Dispatchers.IO) {
            // Select all (Ctrl+A) - using meta key
            val selectAllDown = KeyEvent(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_A,
                0,
                KeyEvent.META_CTRL_ON
            )
            service.dispatchKeyEvent(selectAllDown)
            val selectAllUp = KeyEvent(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_A,
                0,
                KeyEvent.META_CTRL_ON
            )
            service.dispatchKeyEvent(selectAllUp)

            kotlinx.coroutines.delay(50)

            // Delete selected text
            val deleteDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
            service.dispatchKeyEvent(deleteDown)
            val deleteUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
            service.dispatchKeyEvent(deleteUp)

            kotlinx.coroutines.delay(50)
        }
    }

    companion object {
        private const val TOOL_NAME = "android.clear_text"
        private const val TAG = "ClearTextTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Clears text from the currently focused editable field or a specified node. " +
                "If node_id is provided, sets the text to empty via accessibility action. " +
                "Otherwise attempts to select all and delete via key events.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("node_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional node ID of the editable field to clear")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
