package com.androidagent.aiagent.tools.agent

import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


class StopTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        return ToolResult(
            success = true,
            toolName = TOOL_NAME,
            result = buildJsonObject {
                put("action", "agent_stopped")
            },
            observationRequired = false
        )
    }

    companion object {
        const val TOOL_NAME = "agent.stop"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Stop the agent immediately. Use this when the task cannot be safely completed, when you are stuck in a loop, or when the user's request cannot be fulfilled.",
            inputSchema = kotlinx.serialization.json.buildJsonObject {
                put("type", "object")
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("reason", kotlinx.serialization.json.buildJsonObject {
                        put("type", "string")
                        put("description", "Reason for stopping.")
                    })
                })
            },
            riskLevel = RiskLevel.SAFE
        )
    }
}
