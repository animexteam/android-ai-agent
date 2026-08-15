package com.androidagent.aiagent.tools.android

import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CopyTextTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val root = service.safeGetRootInActiveWindow()
            ?: return ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "NO_ROOT", message = "No root node"))
        val focused = findFocusedEditable(root)
            ?: return ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "NO_EDITABLE", message = "No editable field focused"))
        val selected = focused.text?.toString() ?: ""
        val ok = service.performCopy(focused)
        return if (ok) ToolResult(success = true, toolName = TOOL_NAME, result = buildJsonObject { put("text", selected); put("action", "copied") })
        else ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "COPY_FAILED", message = "Could not copy"))
    }
    private fun noService() = ToolResult(success = false, toolName = TOOL_NAME, error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service not connected"))
    private fun findFocusedEditable(n: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isFocused && n.isEditable) return n
        if (n.isEditable && n.isFocusable) return n
        for (i in 0 until n.childCount) { val c = n.getChild(i); if (c != null) { val f = findFocusedEditable(c); if (f != null) return f } }
        return null
    }
    companion object {
        internal const val TOOL_NAME = "android.copy_text"
        fun definition() = AgentTool(name = TOOL_NAME,
            description = "Copy selected text from the focused editable field to clipboard.",
            inputSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false)
    }
}