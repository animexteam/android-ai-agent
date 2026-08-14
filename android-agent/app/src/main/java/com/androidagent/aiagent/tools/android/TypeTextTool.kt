package com.androidagent.aiagent.tools.android

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
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
                    val result = setTextViaSetAction(targetNode, text)
                    if (result != null) {
                        delay(150)
                        // Verify and retry with clipboard if mismatch
                        if (!verifyText(service, text)) {
                            Log.w(TAG, "Verification failed after ACTION_SET_TEXT, trying clipboard for exact text")
                            val retryResult = retryWithClipboard(service, text)
                            if (retryResult != null) return retryResult
                        }
                        return result
                    }
                    // Click to focus for fallback strategies
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(100)
                }
            }

            // Strategy 2: Use AccessibilityService performAction with SET_TEXT on focused node
            val focusedSet = setTextOnFocusedField(service, text)
            if (focusedSet) {
                delay(150)
                if (verifyText(service, text)) {
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
                Log.w(TAG, "Verification failed after focused set, trying clipboard")
                // Verification failed — try clipboard as retry
                val retryResult = retryWithClipboard(service, text)
                if (retryResult != null) return retryResult
            }

            // Strategy 3: Paste via clipboard + gesture tap on EditText
            val clipboardResult = setTextViaClipboard(service, text)
            if (clipboardResult) {
                delay(200)
                if (verifyText(service, text)) {
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
                // Clipboard was the last resort — return success even if verify fails
                // (some apps don't expose text via accessibility after paste)
                return ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("text", text)
                        put("method", "clipboard_paste_unverified")
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
     * When verification fails, clear the field and retry with clipboard paste.
     * This is the MOST RELIABLE method for exact text (especially numbers).
     */
    private suspend fun retryWithClipboard(service: AndroidAgentAccessibilityService, text: String): ToolResult? {
        return try {
            // First clear the field
            val rootNode = service.safeGetRootInActiveWindow() ?: return null
            val editNode = findFocusedEditable(rootNode) ?: findFirstEditable(rootNode)
            if (editNode != null) {
                // Clear existing wrong text
                editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val clearArgs = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        ""
                    )
                }
                editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
                delay(100)
            }

            // Now paste via clipboard
            val clipboardOk = setTextViaClipboard(service, text)
            if (clipboardOk) {
                delay(200)
                ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("text", text)
                        put("method", "clipboard_retry_after_verify_fail")
                        put("characters", text.length)
                    }
                )
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard retry failed", e)
            null
        }
    }

    /**
     * Set text using ACTION_SET_TEXT on a specific node.
     */
    private fun setTextViaSetAction(
        node: AccessibilityNodeInfo,
        text: String
    ): ToolResult? {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        val setResult = node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT, args
        )
        return if (setResult) {
            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("method", "ACTION_SET_TEXT")
                    put("characters", text.length)
                }
            )
        } else null
    }

    /**
     * Verify that the text in the focused editable field matches what we set.
     */
    private fun verifyText(service: AndroidAgentAccessibilityService, expected: String): Boolean {
        return try {
            val rootNode = service.safeGetRootInActiveWindow() ?: return true
            val focusedNode = findFocusedEditable(rootNode) ?: return true
            val actual = focusedNode.text?.toString() ?: return true
            val match = actual.trim() == expected.trim()
            if (!match) {
                Log.w(TAG, "Text mismatch! Expected: '$expected', Got: '$actual'")
            }
            match
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Find a node in the live tree by its hashed node_id.
     */
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
                val newRoot = service.safeGetRootInActiveWindow()
                val pasteNode = findNodeByText(newRoot, "Paste")

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
            description = "Type text into a field. Provide 'text' (required) — type EXACTLY what the user said, character by character. No auto-correct, no modifications. If user said '786687', type exactly '786687'. Optionally provide 'node_id' of the target editable field.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "The EXACT text to type — character-perfect, no modifications")
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
