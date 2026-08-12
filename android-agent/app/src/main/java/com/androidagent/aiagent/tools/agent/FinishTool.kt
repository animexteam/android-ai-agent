package com.androidagent.aiagent.tools.agent

import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put


class FinishTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val success = args["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        val message = args["message"]?.jsonPrimitive?.content ?: "Task completed."

        return ToolResult(
            success = true,
            toolName = TOOL_NAME,
            result = buildJsonObject {
                put("success", success)
                put("message", message)
                put("action", "agent_finished")
            },
            observationRequired = false
        )
    }

    companion object {
        const val TOOL_NAME = "agent.finish"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Signal that the task has been completed or cannot be completed. Set success to true if the goal was achieved, false otherwise. Provide a brief message describing the outcome.",
            inputSchema = kotlinx.serialization.json.buildJsonObject {
                put("type", "object")
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("success", kotlinx.serialization.json.buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether the task was successfully completed.")
                    })
                    put("message", kotlinx.serialization.json.buildJsonObject {
                        put("type", "string")
                        put("description", "Summary of the task outcome.")
                    })
                })
                put("required", kotlinx.serialization.json.buildJsonArray { add("success") })
            },
            riskLevel = RiskLevel.SAFE
        )
    }
}
