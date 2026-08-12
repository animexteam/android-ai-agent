package com.androidagent.aiagent.agent

import com.androidagent.aiagent.tools.AgentDecision
import com.androidagent.aiagent.tools.AskUserDecision
import com.androidagent.aiagent.tools.ErrorDecisionData
import com.androidagent.aiagent.tools.FinishDecisionData
import com.androidagent.aiagent.tools.MessageDecision
import com.androidagent.aiagent.tools.ToolCallDecision
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object DecisionParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(response: String): AgentDecision {
        val cleaned = stripMarkdownFences(response.trim())
        val jsonStr = extractJson(cleaned)

        return try {
            val element = json.parseToJsonElement(jsonStr)
            val obj = element.jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull

            when (type) {
                "tool_call" -> parseToolCall(obj)
                "message" -> parseMessage(obj)
                "ask_user" -> parseAskUser(obj)
                "finish" -> parseFinish(obj)
                "error" -> parseError(obj)
                else -> ErrorDecisionData(
                    type = "error",
                    message = "Unknown decision type: '$type'. Response was: $jsonStr"
                )
            }
        } catch (e: Exception) {
            ErrorDecisionData(
                type = "error",
                message = "Failed to parse model response: ${e.message}. Response was: ${jsonStr.take(300)}"
            )
        }
    }

    private fun stripMarkdownFences(text: String): String {
        var result = text.trim()
        val jsonFenceRegex = Regex("""^```(?:json)?\s*\n?""", RegexOption.MULTILINE)
        val endFenceRegex = Regex("""\n?\s*```$""", RegexOption.MULTILINE)
        result = jsonFenceRegex.replace(result, "")
        result = endFenceRegex.replace(result, "")
        return result.trim()
    }

    private fun extractJson(text: String): String {
        // Try parsing the whole text first; if it fails, try to find JSON object within
        return try {
            json.parseToJsonElement(text)
            text
        } catch (_: Exception) {
            val firstBrace = text.indexOf('{')
            val lastBrace = text.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace > firstBrace) {
                text.substring(firstBrace, lastBrace + 1)
            } else {
                text
            }
        }
    }

    private fun parseToolCall(obj: JsonObject): AgentDecision {
        val toolName = obj["tool_name"]?.jsonPrimitive?.contentOrNull
            ?: obj["toolName"]?.jsonPrimitive?.contentOrNull
            ?: return ErrorDecisionData(
                type = "error",
                message = "tool_call decision missing 'tool_name' field"
            )

        val arguments = obj["arguments"]?.let {
            if (it is JsonObject) it else null
        } ?: JsonObject(emptyMap())

        return ToolCallDecision(
            type = "tool_call",
            toolName = toolName,
            arguments = arguments
        )
    }

    private fun parseMessage(obj: JsonObject): AgentDecision {
        val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
        return MessageDecision(
            type = "message",
            content = content
        )
    }

    private fun parseAskUser(obj: JsonObject): AgentDecision {
        val question = obj["question"]?.jsonPrimitive?.contentOrNull
            ?: return ErrorDecisionData(
                type = "error",
                message = "ask_user decision missing 'question' field"
            )
        return AskUserDecision(
            type = "ask_user",
            question = question
        )
    }

    private fun parseFinish(obj: JsonObject): AgentDecision {
        val success = obj["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: "Task finished."
        return FinishDecisionData(
            type = "finish",
            success = success,
            message = message
        )
    }

    private fun parseError(obj: JsonObject): AgentDecision {
        val message = obj["message"]?.jsonPrimitive?.contentOrNull
            ?: "Model reported an error with no message."
        return ErrorDecisionData(
            type = "error",
            message = message
        )
    }
}
