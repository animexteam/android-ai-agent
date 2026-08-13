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

/**
 * Parses the raw text returned by the AI model into a typed [AgentDecision].
 *
 * The model is instructed to return **exactly one** JSON object, but in
 * practice it may wrap the JSON in markdown fences, prepend/append prose,
 * or include multiple objects.  This parser is designed to be tolerant of
 * all of these edge cases.
 *
 * Usage: `DecisionParser.parse(response, registeredToolNames)`
 */
object DecisionParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse a raw model response string into an [AgentDecision].
     *
     * @param response The raw text returned by the model.
     * @param knownToolNames The set of currently registered tool names.
     *   When the model references an unknown tool, the parser returns an
     *   [ErrorDecisionData] that includes the list of valid names.
     * @return A parsed [AgentDecision] – never null.
     */
    fun parse(response: String, knownToolNames: Set<String> = emptySet()): AgentDecision {
        val cleaned = stripMarkdownFences(response.trim())
        val jsonStr = extractJson(cleaned)

        return try {
            val element = json.parseToJsonElement(jsonStr)
            val obj = element.jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull

            when (type) {
                "tool_call" -> parseToolCall(obj, knownToolNames)
                "message"   -> parseMessage(obj)
                "ask_user"  -> parseAskUser(obj)
                "finish"    -> parseFinish(obj)
                "error"     -> parseError(obj)
                else -> ErrorDecisionData(
                    type = "error",
                    message = buildString {
                        append("Unknown decision type: '$type'. ")
                        append("Expected one of: tool_call, message, ask_user, finish, error. ")
                        append("Response was: $jsonStr")
                    }
                )
            }
        } catch (e: Exception) {
            ErrorDecisionData(
                type = "error",
                message = "Failed to parse model response: ${e.message}. " +
                    "Response was: ${jsonStr.take(300)}"
            )
        }
    }

    // -----------------------------------------------------------------------
    // Markdown-fence stripping
    // -----------------------------------------------------------------------

    private fun stripMarkdownFences(text: String): String {
        var result = text.trim()
        // Remove opening fence: ```json, ```, ```JSON, etc.
        val openFence = Regex("""^```(?:json|JSON)?\s*\n?""", RegexOption.MULTILINE)
        result = openFence.replace(result, "")
        // Remove closing fence
        val closeFence = Regex("""\n?\s*```$""", RegexOption.MULTILINE)
        result = closeFence.replace(result, "")
        return result.trim()
    }

    // -----------------------------------------------------------------------
    // JSON extraction – tolerant of prose around the JSON
    // -----------------------------------------------------------------------

    /**
     * Try to find a valid JSON object in [text].
     *
     * Strategy:
     * 1. If the whole text parses as JSON, return it.
     * 2. Otherwise, find the outermost `{…}` pair using brace-depth counting.
     */
    private fun extractJson(text: String): String {
        // Fast path: the whole thing is JSON.
        return try {
            json.parseToJsonElement(text)
            text
        } catch (_: Exception) {
            // Slow path: brace-depth scan.
            extractFirstJsonObject(text)
        }
    }

    /**
     * Extract the first top-level JSON object from the string using
     * brace-depth counting.  This correctly handles nested braces and
     * braces inside string literals.
     */
    private fun extractFirstJsonObject(text: String): String {
        var depth = 0
        var start = -1
        var inString = false
        var escape = false

        for (i in text.indices) {
            val ch = text[i]

            if (escape) {
                escape = false
                continue
            }
            if (ch == '\\' && inString) {
                escape = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (inString) continue

            when (ch) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start != -1) {
                        return text.substring(start, i + 1)
                    }
                }
            }
        }

        // Fallback: return the original text and let the caller handle the
        // parse error.
        return text
    }

    // -----------------------------------------------------------------------
    // Per-type parsers
    // -----------------------------------------------------------------------

    private fun parseToolCall(obj: JsonObject, knownToolNames: Set<String>): AgentDecision {
        val toolName = obj["tool_name"]?.jsonPrimitive?.contentOrNull
            ?: obj["toolName"]?.jsonPrimitive?.contentOrNull
            ?: return ErrorDecisionData(
                type = "error",
                message = "tool_call decision missing 'tool_name' field"
            )

        // NOTE: We do NOT reject unknown tool names here.
        // The ToolExecutor handles alias resolution and fuzzy matching
        // at execution time. Rejecting here would prevent valid aliases
        // (e.g. "android.tap" -> "android.click") from working.

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
        return MessageDecision(type = "message", content = content)
    }

    private fun parseAskUser(obj: JsonObject): AgentDecision {
        val question = obj["question"]?.jsonPrimitive?.contentOrNull
            ?: return ErrorDecisionData(
                type = "error",
                message = "ask_user decision missing 'question' field"
            )
        return AskUserDecision(type = "ask_user", question = question)
    }

    private fun parseFinish(obj: JsonObject): AgentDecision {
        val success = obj["success"]?.jsonPrimitive?.contentOrNull
            ?.toBooleanStrictOrNull() ?: false
        val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: "Task finished."
        return FinishDecisionData(type = "finish", success = success, message = message)
    }

    private fun parseError(obj: JsonObject): AgentDecision {
        val message = obj["message"]?.jsonPrimitive?.contentOrNull
            ?: "Model reported an error with no message."
        return ErrorDecisionData(type = "error", message = message)
    }
}
