package com.androidagent.aiagent.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Risk levels for tool calls
// ============================================================================

enum class RiskLevel { SAFE, CONFIRM, BLOCKED }

// ============================================================================
// Tool definition – describes a tool's schema for the AI model
// ============================================================================

@Serializable
data class AgentTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val riskLevel: RiskLevel = RiskLevel.SAFE,
    val requiresConfirmation: Boolean = false
)

// ============================================================================
// Tool execution results
// ============================================================================

@Serializable
data class ToolResult(
    val success: Boolean,
    val toolName: String,
    val result: JsonObject? = null,
    val error: ToolError? = null,
    /** If true the runtime should re-observe the screen before the next turn. */
    val observationRequired: Boolean = true
)

@Serializable
data class ToolError(
    val code: String,
    val message: String
)

// ============================================================================
// Agent decision types – parsed from the AI model's JSON response
// ============================================================================

/**
 * Sealed hierarchy representing the parsed output of a single model turn.
 *
 * The model always returns a JSON object with a `"type"` field.  We map
 * each `type` value to exactly one subclass so that the runtime can handle
// every possible response in an exhaustive `when` expression.
 */
@kotlinx.serialization.Serializable
sealed class AgentDecision {
    abstract val type: String
}

/** The model wants to invoke a tool. */
@Serializable
@kotlinx.serialization.SerialName("tool_call")
data class ToolCallDecision(
    override val type: String = "tool_call",
    val toolName: String,
    val arguments: JsonObject = JsonObject(emptyMap())
) : AgentDecision()

/** The model emits a status/thinking message – the loop continues. */
@Serializable
@kotlinx.serialization.SerialName("message")
data class MessageDecision(
    override val type: String = "message",
    val content: String
) : AgentDecision()

/** The model needs input from the user – the loop pauses. */
@Serializable
@kotlinx.serialization.SerialName("ask_user")
data class AskUserDecision(
    override val type: String = "ask_user",
    val question: String
) : AgentDecision()

/** The model signals task completion. */
@Serializable
@kotlinx.serialization.SerialName("finish")
data class FinishDecisionData(
    override val type: String = "finish",
    val success: Boolean,
    val message: String
) : AgentDecision()

/** The model reports an error – the loop continues so it can recover. */
@Serializable
@kotlinx.serialization.SerialName("error")
data class ErrorDecisionData(
    override val type: String = "error",
    val message: String
) : AgentDecision()
