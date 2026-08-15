package com.androidagent.aiagent.ai

import android.util.Log
import com.androidagent.aiagent.data.SettingsRepository
import com.androidagent.aiagent.tools.AgentTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AI client supporting both Ollama and OpenAI-compatible APIs.
 *
 * v5.0 Key improvements:
 * - Singleton OkHttpClient (fixes ANR from thread pool exhaustion)
 * - OpenAI response format auto-detection
 * - Ollama image support preserved
 * - Better error messages
 */
class GemmaClient(private val settingsRepository: SettingsRepository) {

    companion object {
        private const val TAG = "GemmaClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Singleton HTTP client — shared across all requests to prevent thread pool exhaustion. */
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Android-Use/5.0")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
        prettyPrint = false
    }

    suspend fun generate(
        systemPrompt: String,
        userMessage: String,
        tools: List<AgentTool>,
        screenshotBase64: String? = null
    ): String {
        val chatMessages = buildList {
            add(buildTextMessage("system", systemPrompt))
            if (screenshotBase64 != null) {
                add(buildOllamaUserMessageWithImage(userMessage, screenshotBase64))
            } else {
                add(buildTextMessage("user", userMessage))
            }
        }
        return doChat(chatMessages, screenshotBase64 != null)
    }

    suspend fun generateWithHistory(
        systemPrompt: String,
        messages: List<ChatMessage>,
        screenshotBase64: String? = null
    ): String {
        val chatMessages = mutableListOf<JsonObject>()
        chatMessages.add(buildTextMessage("system", systemPrompt))

        for ((index, msg) in messages.withIndex()) {
            val isLastUserMessage = index == messages.lastIndex &&
                msg.role.equals("user", ignoreCase = true)

            if (isLastUserMessage && screenshotBase64 != null) {
                // For vision: send image only in the last user message
                chatMessages.add(buildOllamaUserMessageWithImage(msg.content, screenshotBase64))
            } else {
                chatMessages.add(buildTextMessage(msg.role, msg.content))
            }
        }

        return doChat(chatMessages, screenshotBase64 != null)
    }

    // ===================================================================
    // Message builders
    // ===================================================================

    private fun buildTextMessage(role: String, content: String): JsonObject {
        return buildJsonObject {
            put("role", role)
            put("content", content)
        }
    }

    /** Ollama format: images array with raw base64 (no data URI prefix). */
    private fun buildOllamaUserMessageWithImage(text: String, screenshotBase64: String): JsonObject {
        return buildJsonObject {
            put("role", "user")
            put("content", text)
            put("images", buildJsonArray { add(screenshotBase64) })
        }
    }

    // ===================================================================
    // Core chat method — auto-detects Ollama vs OpenAI format
    // ===================================================================

    private suspend fun doChat(messages: List<JsonObject>, hasImage: Boolean): String {
        val endpoint = settingsRepository.endpoint()
        val model = settingsRepository.model()
        val temperature = settingsRepository.temperature()
        val timeoutMs = settingsRepository.timeout()
        val apiKey = settingsRepository.apiKey()
        val provider = settingsRepository.provider()

        val isOpenAI = isLikelyOpenAI(endpoint, provider)

        val bodyObject = if (isOpenAI) {
            buildOpenAIBody(model, messages, temperature, hasImage, screenshotBase64 = null)
        } else {
            buildOllamaBody(model, messages, temperature)
        }

        val bodyString = json.encodeToString(JsonElement.serializer(), bodyObject)
        Log.d(TAG, "Request: ${bodyString.length} chars, model=$model, api=${if (isOpenAI) "OpenAI" else "Ollama"}")

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyString.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                httpClient.newCall(requestBuilder.build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.e(TAG, "Request failed after ${elapsed}ms", e)
                        val message = when (e) {
                            is SocketTimeoutException -> "Request timed out after ${elapsed}ms. Try increasing timeout in settings."
                            else -> "Network error: ${e.localizedMessage ?: e.message ?: "unknown"}"
                        }
                        if (continuation.isActive) {
                            continuation.resumeWithException(GemmaClientException(message, e))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val elapsed = System.currentTimeMillis() - startTime
                        try {
                            val responseBody = response.body
                            if (responseBody == null) {
                                if (continuation.isActive) continuation.resumeWithException(
                                    GemmaClientException("Empty response body (HTTP ${response.code})"))
                                return
                            }

                            val bodyString = responseBody.string()

                            if (!response.isSuccessful) {
                                Log.e(TAG, "HTTP ${response.code} after ${elapsed}ms: ${bodyString.take(300)}")
                                if (continuation.isActive) continuation.resumeWithException(
                                    GemmaClientException("HTTP ${response.code}: ${extractErrorMessage(bodyString)}"))
                                return
                            }

                            Log.d(TAG, "Response in ${elapsed}ms, size=${bodyString.length}")

                            val content = if (isOpenAI) {
                                parseOpenAIResponse(bodyString)
                            } else {
                                parseOllamaResponse(bodyString)
                            }

                            if (content.isNullOrBlank()) {
                                if (continuation.isActive) continuation.resumeWithException(
                                    GemmaClientException("Empty model response. Body: ${bodyString.take(200)}"))
                                return
                            }

                            if (continuation.isActive) continuation.resume(content)
                        } catch (t: Throwable) {
                            if (t is GemmaClientException || t is CancellationException) throw t
                            if (continuation.isActive) continuation.resumeWithException(
                                GemmaClientException("Error processing response", t))
                        } finally {
                            response.close()
                        }
                    }
                })

                continuation.invokeOnCancellation {
                    Log.d(TAG, "Chat request cancelled")
                }
            }
        }
    }

    // ===================================================================
    // Request body builders
    // ===================================================================

    private fun buildOllamaBody(model: String, messages: List<JsonObject>, temperature: Float): JsonObject {
        return buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray { messages.forEach { add(it) } })
            put("options", buildJsonObject { put("temperature", temperature) })
            put("stream", false)
        }
    }

    private fun buildOpenAIBody(model: String, messages: List<JsonObject>, temperature: Float, hasImage: Boolean, screenshotBase64: String?): JsonObject {
        // For OpenAI: if there's an image in the last user message, convert to multimodal format
        val adaptedMessages = if (hasImage && messages.isNotEmpty()) {
            val lastMsg = messages.last()
            val textContent = lastMsg["content"]?.jsonPrimitive?.contentOrNull ?: ""
            val imageBase64 = lastMsg["images"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull

            val multimodalContent = buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", textContent) })
                if (imageBase64 != null) {
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", "data:image/jpeg;base64,$imageBase64")
                        })
                    })
                }
            }

            messages.dropLast(1) + buildJsonObject {
                put("role", "user")
                put("content", multimodalContent)
            }
        } else {
            messages
        }

        return buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray { adaptedMessages.forEach { add(it) } })
            put("temperature", temperature)
            put("max_tokens", 4096)
        }
    }

    // ===================================================================
    // Response parsers
    // ===================================================================

    /** Parse Ollama response: {"message":{"content":"..."}} */
    private fun parseOllamaResponse(bodyString: String): String? {
        val parsed = try { json.parseToJsonElement(bodyString).jsonObject } catch (_: Exception) { return null }
        return parsed["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
    }

    /** Parse OpenAI response: {"choices":[{"message":{"content":"..."}}]} */
    private fun parseOpenAIResponse(bodyString: String): String? {
        val parsed = try { json.parseToJsonElement(bodyString).jsonObject } catch (_: Exception) { return null }
        return parsed["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    /** Detect if the endpoint is likely an OpenAI-compatible API. */
    private fun isLikelyOpenAI(endpoint: String, provider: String): Boolean {
        val openAIProviders = listOf(
            "OpenAI", "openai", "OpenRouter", "Groq", "Together AI",
            "DeepInfra", "Fireworks AI", "Cerebras", "Google AI",
            "Anthropic", "Mistral", "Cohere"
        )
        if (openAIProviders.any { provider.equals(it, ignoreCase = true) }) return true
        val ollamaProviders = listOf("Ollama Cloud", "Ollama Local", "ollama")
        if (ollamaProviders.any { provider.equals(it, ignoreCase = true) }) return false
        // Heuristic: if endpoint contains openai/chat/completions or known OpenAI-compatible domains
        val url = endpoint.lowercase()
        return url.contains("/v1/chat") || url.contains("openai") ||
            url.contains("/chat/completions") || url.contains("groq") ||
            url.contains("together") || url.contains("deepinfra") ||
            url.contains("fireworks") || url.contains("cerebras") ||
            url.contains("openrouter")
    }

    private fun extractErrorMessage(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error"]?.let { err ->
                err.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    ?: err.jsonPrimitive?.contentOrNull
            } ?: obj["message"]?.jsonPrimitive?.contentOrNull
                ?: body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }
    }

    @Serializable
    data class ChatMessage(
        val role: String,
        val content: String
    )

    class GemmaClientException(
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause)
}
