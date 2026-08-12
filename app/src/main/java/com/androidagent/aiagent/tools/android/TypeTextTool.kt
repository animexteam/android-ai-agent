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

class TypeTextTool : ToolHandler {

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

            val text = args["text"]?.toString()?.removeSurrounding("\"")
            if (text.isNullOrBlank()) {
                return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "INVALID_INPUT",
                        message = "'text' parameter is required and must not be empty"
                    )
                )
            }

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

                // Find the underlying AccessibilityNodeInfo and perform actions
                val rootNode = service.rootInActiveWindow
                val targetNode = findNodeById(rootNode, node.nodeId)

                if (targetNode != null) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val bundle = android.os.Bundle()
                    bundle.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                    val setTextResult = targetNode.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        bundle
                    )

                    if (setTextResult) {
                        return ToolResult(
                            success = true,
                            toolName = TOOL_NAME,
                            result = buildJsonObject {
                                put("nodeId", node.nodeId)
                                put("text", text)
                                put("method", "set_text")
                                put("characters", text.length)
                            }
                        )
                    }
                    // If ACTION_SET_TEXT failed, fall through to key event dispatch
                }
            }

            // Fallback: type via key event dispatch
            typeViaKeyEvents(service, text)

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("text", text)
                    put("method", "key_events")
                    put("characters", text.length)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to type text", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "TYPE_FAILED",
                    message = "Failed to type text: ${e.message}"
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

    private suspend fun typeViaKeyEvents(
        service: AndroidAgentAccessibilityService,
        text: String
    ) {
        withContext(Dispatchers.IO) {
            text.forEach { char ->
                val event = KeyEvent(KeyEvent.ACTION_DOWN, char.code)
                service.dispatchKeyEvent(event)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, char.code)
                service.dispatchKeyEvent(upEvent)
                kotlinx.coroutines.delay(10)
            }
        }
    }

    companion object {
        internal const val TOOL_NAME = "android.type_text"
        private const val TAG = "TypeTextTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Types text into the currently focused editable field or a specified node. " +
                "If node_id is provided, focuses that node first then sets the text. " +
                "Otherwise, dispatches key events to the current focus.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "The text to type")
                    })
                    put("node_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional node ID of the editable field to type into")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
