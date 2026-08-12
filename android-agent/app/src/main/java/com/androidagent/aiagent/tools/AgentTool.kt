package com.androidagent.aiagent.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class RiskLevel { SAFE, CONFIRM, BLOCKED }

@Serializable
data class AgentTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val riskLevel: RiskLevel = RiskLevel.SAFE,
    val requiresConfirmation: Boolean = false
)

@Serializable
data class ToolResult(
    val success: Boolean,
    val toolName: String,
    val result: JsonObject? = null,
    val error: ToolError? = null,
    val observationRequired: Boolean = true
)

@Serializable
data class ToolError(
    val code: String,
    val message: String
)

@Serializable
data class ToolCall(
    val type: String = "tool_call",
    val toolName: String,
    val arguments: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class AgentMessage(
    val type: String = "message",
    val content: String
)

@Serializable
data class AskUser(
    val type: String = "ask_user",
    val question: String
)

@Serializable
data class FinishDecision(
    val type: String = "finish",
    val success: Boolean,
    val message: String
)

@Serializable
data class ErrorDecision(
    val type: String = "error",
    val message: String
)

@kotlinx.serialization.Serializable
sealed class AgentDecision {
    abstract val type: String
}

@Serializable
@kotlinx.serialization.SerialName("tool_call")
data class ToolCallDecision(
    override val type: String = "tool_call",
    val toolName: String,
    val arguments: JsonObject = JsonObject(emptyMap())
) : AgentDecision()

@Serializable
@kotlinx.serialization.SerialName("message")
data class MessageDecision(
    override val type: String = "message",
    val content: String
) : AgentDecision()

@Serializable
@kotlinx.serialization.SerialName("ask_user")
data class AskUserDecision(
    override val type: String = "ask_user",
    val question: String
) : AgentDecision()

@Serializable
@kotlinx.serialization.SerialName("finish")
data class FinishDecisionData(
    override val type: String = "finish",
    val success: Boolean,
    val message: String
) : AgentDecision()

@Serializable
@kotlinx.serialization.SerialName("error")
data class ErrorDecisionData(
    override val type: String = "error",
    val message: String
) : AgentDecision()
