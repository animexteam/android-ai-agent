package com.androidagent.aiagent.tools.android

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.androidagent.aiagent.accessibility.AccessibilityObserver
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.accessibility.GestureController
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TypeTextTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        return try {
            val service = AndroidAgentAccessibilityService.instance
                ?: return noService()

            val text = args["text"]?.toString()?.removeSurrounding("\"")
            if (text.isNullOrBlank()) {
                return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(code = "INVALID_INPUT", message = "'text' parameter is required")
                )
            }

            val nodeId = args["node_id"]?.toString()?.removeSurrounding("\"")

            // Strategy 1: If node_id provided, find the real node and use ACTION_SET_TEXT
            if (!nodeId.isNullOrBlank()) {
                val rootNode = service.safeGetRootInActiveWindow()
                val targetNode = findNodeByHashId(rootNode, nodeId)

                if (targetNode != null) {
                    // Focus the node first
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    delay(50)

                    // Use ACTION_SET_TEXT (most reliable)
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            text
                        )
                    }
                    val setResult = targetNode.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT, args
                    )

                    if (setResult) {
                        return ToolResult(
                            success = true,
                            toolName = TOOL_NAME,
                            result = buildJsonObject {
                                put("nodeId", nodeId)
                                put("text", text)
                                put("method", "ACTION_SET_TEXT")
                                put("characters", text.length)
                            }
                        )
                    }
                    // If ACTION_SET_TEXT failed, try click-to-focus then paste
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(100)
                }
            }

            // Strategy 2: Use AccessibilityService performAction with SET_TEXT on focused node
            val focusedSet = setTextOnFocusedField(service, text)
            if (focusedSet) {
                return ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("text", text)
                        put("method", "focused_set_text")
                        put("characters", text.length)
                    }
                )
            }

            // Strategy 3: Paste via clipboard + gesture tap on EditText
            val clipboardResult = setTextViaClipboard(service, text)
            if (clipboardResult) {
                return ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("text", text)
                        put("method", "clipboard_paste")
                        put("characters", text.length)
                    }
                )
            }

            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "TYPE_FAILED", message = "All text input methods failed")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to type text", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "TYPE_FAILED", message = "Failed to type text: ${e.message}")
            )
        }
    }

    /**
     * Find a node in the live tree by its hashed node_id (format: "node_12345").
     * The number after "node_" is the hashCode of the original AccessibilityNodeInfo.
     */
    private fun findNodeByHashId(
        root: AccessibilityNodeInfo?,
        nodeId: String
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        // Extract the hash code from the node_id format "node_12345"
        val hashStr = nodeId.removePrefix("node_")
        val targetHash = hashStr.toIntOrNull()

        return if (targetHash != null) {
            findByHashCode(root, targetHash)
        } else {
            // Try matching by viewIdResourceName
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

    /**
     * Try to set text on the currently focused editable node by walking
     * the tree and finding a focused editable node.
     */
    private suspend fun setTextOnFocusedField(
        service: AndroidAgentAccessibilityService,
        text: String
    ): Boolean {
        val rootNode = service.safeGetRootInActiveWindow() ?: return false

        val focusedNode = findFocusedEditable(rootNode)
        if (focusedNode != null) {
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    private fun findFocusedEditable(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.isEditable) return node
        if (node.isEditable && node.isFocusable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedEditable(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Set text via clipboard: copy text to clipboard, then long-press the
     * editable field and tap "Paste".
     */
    private suspend fun setTextViaClipboard(
        service: AndroidAgentAccessibilityService,
        text: String
    ): Boolean {
        return try {
            val clipboard = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager ?: return false

            val clip = android.content.ClipData.newPlainText("agent_input", text)
            clipboard.setPrimaryClip(clip)
            delay(100)

            // Find the editable field and long-press it
            val rootNode = service.safeGetRootInActiveWindow() ?: return false
            val editNode = findFocusedEditable(rootNode) ?: findFirstEditable(rootNode)
            if (editNode != null) {
                val b = android.graphics.Rect()
                editNode.getBoundsInScreen(b)
                val cx = (b.left + b.right) / 2f
                val cy = (b.top + b.bottom) / 2f

                // Long press to show context menu
                GestureController.performLongPress(cx, cy)
                delay(500)

                // Look for "Paste" in the tree
                val pasteNode = findNodeByText(service.safeGetRootInActiveWindow(), "Paste")
                    ?: findNodeByText(service.safeGetRootInActiveWindow(), "Paste")

                if (pasteNode != null) {
                    val pb = android.graphics.Rect()
                    pasteNode.getBoundsInScreen(pb)
                    GestureController.performTap(
                        ((pb.left + pb.right) / 2f),
                        ((pb.top + pb.bottom) / 2f)
                    )
                    delay(200)
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard paste failed", e)
            false
        }
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByText(
        node: AccessibilityNodeInfo?,
        text: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.equals(text, ignoreCase = true) == true) return node
        if (node.contentDescription?.toString()?.equals(text, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun noService(): ToolResult = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    companion object {
        internal const val TOOL_NAME = "android.type_text"
        private const val TAG = "TypeTextTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Type text into a field. Provide 'text' (required). Optionally provide 'node_id' of the target editable field. Uses multiple strategies: ACTION_SET_TEXT, focused field detection, and clipboard paste as fallback.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "The text to type")
                    })
                    put("node_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional node ID of the editable field")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
