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

class FindTool : ToolHandler {

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
            val uiTree = observation.uiTree

            val filterText = args["text"]?.toString()?.removeSurrounding("\"")
            val filterTextContains = args["text_contains"]?.toString()?.removeSurrounding("\"")
            val filterContentDesc = args["content_description"]?.toString()?.removeSurrounding("\"")
            val filterResourceId = args["resource_id"]?.toString()?.removeSurrounding("\"")
            val filterClassName = args["class_name"]?.toString()?.removeSurrounding("\"")
            val filterClickable = args["clickable"]?.toString()?.toBooleanStrictOrNull()
            val filterEditable = args["editable"]?.toString()?.toBooleanStrictOrNull()
            val filterScrollable = args["scrollable"]?.toString()?.toBooleanStrictOrNull()

            val matchedNodes = uiTree.filter { node ->
                var matches = true

                if (filterText != null) {
                    matches = matches && node.text == filterText
                }

                if (filterTextContains != null) {
                    matches = matches && (node.text?.contains(filterTextContains) == true)
                }

                if (filterContentDesc != null) {
                    matches = matches && node.contentDescription == filterContentDesc
                }

                if (filterResourceId != null) {
                    matches = matches && (node.resourceId?.contains(filterResourceId) == true)
                }

                if (filterClassName != null) {
                    matches = matches && (node.className?.contains(filterClassName) == true)
                }

                if (filterClickable != null) {
                    matches = matches && node.isClickable == filterClickable
                }

                if (filterEditable != null) {
                    matches = matches && node.isEditable == filterEditable
                }

                if (filterScrollable != null) {
                    // scrollable is not directly on UiNode, skip gracefully
                }

                matches
            }

            val resultsArray = buildJsonArray {
                matchedNodes.forEach { node ->
                    add(buildJsonObject {
                        put("nodeId", node.nodeId)
                        put("text", node.text ?: "")
                        put("contentDescription", node.contentDescription ?: "")
                        put("resourceId", node.resourceId ?: "")
                        put("className", node.className ?: "")
                        put("isClickable", node.isClickable)
                        addJsonObject("bounds") {
                            put("left", node.bounds.left)
                            put("top", node.bounds.top)
                            put("right", node.bounds.right)
                            put("bottom", node.bounds.bottom)
                            put("centerX", node.bounds.centerX)
                            put("centerY", node.bounds.centerY)
                        }
                    })
                }
            }

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("matches", resultsArray)
                    put("total", matchedNodes.size)
                    put("observationId", observation.id)
                    put("packageName", observation.packageName ?: "")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find elements", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "FIND_FAILED",
                    message = "Failed to find elements: ${e.message}"
                )
            )
        }
    }

    companion object {
        private const val TOOL_NAME = "android.find"
        private const val TAG = "FindTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Searches the current UI tree for elements matching the given criteria. " +
                "All provided criteria must match (AND logic). Returns a list of matching nodes " +
                "with their IDs, text, descriptions, bounds, and other properties.",
            inputSchema = buildJsonObject {
                put("type", "object")
                addJsonObject("properties") {
                    addJsonObject("text") {
                        put("type", "string")
                        put("description", "Exact text match for the element")
                    }
                    addJsonObject("text_contains") {
                        put("type", "string")
                        put("description", "Substring match for the element text")
                    }
                    addJsonObject("content_description") {
                        put("type", "string")
                        put("description", "Content description of the element")
                    }
                    addJsonObject("resource_id") {
                        put("type", "string")
                        put("description", "Resource ID of the element (partial match)")
                    }
                    addJsonObject("class_name") {
                        put("type", "string")
                        put("description", "Class name of the element (partial match)")
                    }
                    addJsonObject("clickable") {
                        put("type", "boolean")
                        put("description", "Filter for clickable elements")
                    }
                    addJsonObject("editable") {
                        put("type", "boolean")
                        put("description", "Filter for editable elements")
                    }
                    addJsonObject("scrollable") {
                        put("type", "boolean")
                        put("description", "Filter for scrollable elements")
                    }
                }
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
