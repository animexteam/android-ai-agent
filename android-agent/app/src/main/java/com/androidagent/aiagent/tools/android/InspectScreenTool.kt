package com.androidagent.aiagent.tools.android

import android.util.Log
import com.androidagent.aiagent.accessibility.AccessibilityObserver
import com.androidagent.aiagent.agent.AndroidObservation
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class InspectScreenTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        return try {
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

            val uiTreeArray = buildJsonArray {
                observation.uiTree.forEach { node ->
                    add(serializeNode(node))
                }
            }

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("observationId", observation.id)
                    put("packageName", observation.packageName ?: "")
                    put("nodeCount", observation.uiTree.size)
                    put("uiTree", uiTreeArray)
                },
                observationRequired = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect screen", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "INSPECT_FAILED",
                    message = "Failed to inspect screen: ${e.message}"
                )
            )
        }
    }

    private fun serializeNode(node: com.androidagent.aiagent.agent.UiNode) = buildJsonObject {
        put("nodeId", node.nodeId)
        val displayText = buildString {
            if (!node.text.isNullOrBlank()) {
                append(node.text)
            }
            if (!node.contentDescription.isNullOrBlank()) {
                if (isNotEmpty()) append(" | ")
                append(node.contentDescription)
            }
        }
        put("text", node.text ?: "")
        put("contentDescription", node.contentDescription ?: "")
        put("displayText", displayText)
        put("resourceId", node.resourceId ?: "")
        put("className", node.className ?: "")
        put("isClickable", node.isClickable)
        put("isEditable", node.isEditable)
        put("bounds", buildJsonObject {
            put("left", node.bounds.left)
            put("top", node.bounds.top)
            put("right", node.bounds.right)
            put("bottom", node.bounds.bottom)
            put("centerX", node.bounds.centerX)
            put("centerY", node.bounds.centerY)
        })
        if (node.childIds.isNotEmpty()) {
            put("childCount", node.childIds.size)
        }
    }

    companion object {
        internal const val TOOL_NAME = "android.inspect_screen"
        private const val TAG = "InspectScreenTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Captures and returns the current UI tree as a structured representation. " +
                "Each node includes its ID, text, content description, resource ID, class name, " +
                "bounds, clickability, editability, and child count. Use this to understand the " +
                "current screen layout before performing actions.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    // No inputs required
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
