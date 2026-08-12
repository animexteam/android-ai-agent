package com.androidagent.aiagent.tools.android

import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

class WaitTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        return try {
            val milliseconds = args["milliseconds"]?.jsonPrimitive?.intOrNull ?: DEFAULT_WAIT_MS
            val clampedMs = milliseconds.coerceIn(0, MAX_WAIT_MS)

            withContext(Dispatchers.IO) {
                delay(clampedMs.toLong())
            }

            ToolResult(
                success = true,
                toolName = TOOL_NAME,
                result = buildJsonObject {
                    put("waited_ms", clampedMs)
                },
                observationRequired = true
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = com.androidagent.aiagent.tools.ToolError(
                    code = "WAIT_FAILED",
                    message = "Failed to wait: ${e.message}"
                )
            )
        }
    }

    companion object {
        private const val TOOL_NAME = "android.wait"
        private const val DEFAULT_WAIT_MS = 1000
        private const val MAX_WAIT_MS = 30000

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Waits for the specified number of milliseconds before continuing. " +
                "Useful for waiting for animations, loading, or transitions to complete.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("milliseconds", buildJsonObject {
                        put("type", "integer")
                        put("description", "Time to wait in milliseconds (default 1000)")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false
        )
    }
}
