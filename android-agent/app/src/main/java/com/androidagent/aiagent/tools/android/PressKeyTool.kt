package com.androidagent.aiagent.tools.android

import android.util.Log
import android.view.KeyEvent
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PressKeyTool : ToolHandler {

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

            val keyName = args["key"]?.toString()?.removeSurrounding("\"")
            if (keyName.isNullOrBlank()) {
                return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "INVALID_INPUT",
                        message = "'key' parameter is required. Must be one of: ${KEY_MAP.keys.joinToString(", ")}"
                    )
                )
            }

            val upperKeyName = keyName.uppercase()
            val keyCode = KEY_MAP[upperKeyName]
            if (keyCode == null) {
                return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(
                        code = "INVALID_KEY",
                        message = "Unsupported key: '$keyName'. Supported keys: ${KEY_MAP.keys.joinToString(", ")}"
                    )
                )
            }

            val success = if (keyCode == KeyEvent.KEYCODE_BACK) {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            } else {
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "input keyevent $keyCode"))
                    proc.waitFor() == 0
                } catch (e: Exception) { false }
            }

            if (!success) {
                return ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(code = "PRESS_KEY_FAILED", message = "Failed to press key: $upperKeyName")
                )
            }

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("key", upperKeyName)
                    put("keyCode", keyCode)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to press key", e)
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(
                    code = "PRESS_KEY_FAILED",
                    message = "Failed to press key: ${e.message}"
                )
            )
        }
    }

    companion object {
        internal const val TOOL_NAME = "android.press_key"
        private const val TAG = "PressKeyTool"

        private val KEY_MAP = mapOf(
            "ENTER" to KeyEvent.KEYCODE_ENTER,
            "BACK" to KeyEvent.KEYCODE_BACK,
            "TAB" to KeyEvent.KEYCODE_TAB,
            "ESCAPE" to KeyEvent.KEYCODE_ESCAPE,
            "SPACE" to KeyEvent.KEYCODE_SPACE
        )

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Presses a system or character key. Supported keys: ENTER, BACK, TAB, ESCAPE, SPACE.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("key", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("ENTER")
                            add("BACK")
                            add("TAB")
                            add("ESCAPE")
                            add("SPACE")
                        })
                        put("description", "The key to press")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
