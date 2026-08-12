package com.androidagent.aiagent.tools.agent

import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put


class AskUserTool : ToolHandler {

    override suspend fun execute(args: JsonObject): ToolResult {
        val question = args["question"]?.jsonPrimitive?.content
        if (question.isNullOrBlank()) {
            return ToolResult(
                success = false,
                toolName = TOOL_NAME,
                error = com.androidagent.aiagent.tools.ToolError(
                    code = "MISSING_ARGUMENT",
                    message = "'question' is required."
                )
            )
        }

        return ToolResult(
            success = true,
            toolName = TOOL_NAME,
            result = buildJsonObject {
                put("question", question)
                put("status", "awaiting_response")
            },
            observationRequired = false
        )
    }

    companion object {
        const val TOOL_NAME = "agent.ask_user"

        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME,
            description = "Ask the user a question when essential information is missing or ambiguous. The agent will pause and wait for the user's response before continuing. Use this for disambiguation, account selection, or any scenario where the user's input is needed.",
            inputSchema = kotlinx.serialization.json.buildJsonObject {
                put("type", "object")
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("question", kotlinx.serialization.json.buildJsonObject {
                        put("type", "string")
                        put("description", "The question to ask the user.")
                    })
                })
                put("required", kotlinx.serialization.json.buildJsonArray { add("question") })
            },
            riskLevel = RiskLevel.SAFE
        )
    }
}
