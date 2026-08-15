package com.androidagent.aiagent.tools.android

import android.bluetooth.BluetoothAdapter
import android.util.Log
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ToggleBluetoothTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val enable = args["enable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()

        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = ToolError(code = "NO_BLUETOOTH", message = "Device does not support Bluetooth")
            )

        return if (enable != null) {
            try {
                val wasEnabled = adapter.isEnabled
                if (enable) adapter.enable() else adapter.disable()
                ToolResult(
                    success = true,
                    toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("was_enabled", wasEnabled)
                        put("now_enabled", enable)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle Bluetooth", e)
                ToolResult(
                    success = false,
                    toolName = TOOL_NAME,
                    error = ToolError(code = "BT_TOGGLE_FAILED", message = "Failed to toggle Bluetooth: ${e.message}")
                )
            }
        } else {
            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject { put("bluetooth_enabled", adapter.isEnabled) },
                observationRequired = false
            )
        }
    }

    companion object {
        internal const val TOOL_NAME = "android.toggle_bluetooth"
        private const val TAG = "ToggleBluetoothTool"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Toggle Bluetooth on/off or check current state. Pass enable=true/false, or omit to check state.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("enable", buildJsonObject {
                        put("type", "boolean")
                        put("description", "true to enable, false to disable. Omit to check state.")
                    })
                })
            },
            riskLevel = RiskLevel.CONFIRM,
            requiresConfirmation = true
        )
    }
}
