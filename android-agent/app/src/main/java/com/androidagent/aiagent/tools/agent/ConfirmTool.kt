package com.androidagent.aiagent.tools.agent

import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put


class ConfirmTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args["action"]?.jsonPrimitive?.content
        val reason = args["reason"]?.jsonPrimitive?.content ?: "Sensitive action requested."

        return ToolResult(
            success = true,
            toolName = TOOL_NAME,
            result = buildJsonObject {
                put("action", action ?: "unknown")
                put("reason", reason)
                put("status", "confirmation_requested")
            },
            observationRequired = false
        )
    }

    companion object {
        const val TOOL_NAME = "agent.confirm"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Request user confirmation before performing a sensitive action. The agent will pause and wait for the user to approve or deny. Include a clear description of what action will be taken.",
            inputSchema = kotlinx.serialization.json.buildJsonObject {
                put("type", "object")
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("action", kotlinx.serialization.json.buildJsonObject {
                        put("type", "string")
                        put("description", "Description of the action requiring confirmation.")
                    })
                    put("reason", kotlinx.serialization.json.buildJsonObject {
                        put("type", "string")
                        put("description", "Why this action needs confirmation.")
                    })
                })
                put("required", kotlinx.serialization.json.buildJsonArray { add("action") })
            },
            riskLevel = RiskLevel.SAFE
        )
    }
}
