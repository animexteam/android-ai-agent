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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GemmaClient(private val settingsRepository: SettingsRepository) {

    companion object {
        private const val TAG = "GemmaClient"
        private const val DEFAULT_ENDPOINT = "https://ollama.com/api/chat"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
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
                add(buildUserMessageWithImage(userMessage, screenshotBase64))
            } else {
                add(buildTextMessage("user", userMessage))
            }
        }
        return doChat(chatMessages)
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
                chatMessages.add(buildUserMessageWithImage(msg.content, screenshotBase64))
            } else {
                chatMessages.add(buildTextMessage(msg.role, msg.content))
            }
        }

        return doChat(chatMessages)
    }

    private fun buildTextMessage(role: String, content: String): JsonObject {
        return buildJsonObject {
            put("role", role)
            put("content", content)
        }
    }

    private fun buildUserMessageWithImage(text: String, screenshotBase64: String): JsonObject {
        // Ollama API format: content is a string, images is a separate array of base64 strings
        return buildJsonObject {
            put("role", "user")
            put("content", text)
            put("images", buildJsonArray {
                add(screenshotBase64)
            })
        }
    }

    private suspend fun doChat(messages: List<JsonObject>): String {
        val endpoint = settingsRepository.endpoint()
        val model = settingsRepository.model()
        val temperature = settingsRepository.temperature()
        val timeoutMs = settingsRepository.timeout()
        val apiKey = settingsRepository.apiKey()

        val bodyObject = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                messages.forEach { add(it) }
            })
            put("options", buildJsonObject {
                put("temperature", temperature)
            })
            put("stream", false)
        }

        val bodyString = json.encodeToString(JsonElement.serializer(), bodyObject)
        Log.d(TAG, "Request body size: ${bodyString.length} chars, model: $model")

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyString.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()

        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.e(TAG, "Request failed after ${elapsed}ms", e)
                        val message = when (e) {
                            is SocketTimeoutException ->
                                "Request timed out after ${elapsed}ms"
                            else ->
                                "Network error: ${e.localizedMessage ?: e.message ?: "unknown"}"
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
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        GemmaClientException(
                                            "Empty response body (HTTP ${response.code})"
                                        )
                                    )
                                }
                                return
                            }

                            val bodyString = responseBody.string()

                            if (!response.isSuccessful) {
                                Log.e(TAG, "HTTP ${response.code} after ${elapsed}ms")
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        GemmaClientException(
                                            "HTTP ${response.code}: ${extractErrorMessage(bodyString)}"
                                        )
                                    )
                                }
                                return
                            }

                            Log.d(TAG, "Response received in ${elapsed}ms")

                            val parsed = try {
                                json.parseToJsonElement(bodyString).jsonObject
                            } catch (e: SerializationException) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        GemmaClientException("Malformed JSON response", e)
                                    )
                                }
                                return
                            }

                            val messageContent = parsed["message"]
                                ?.jsonObject
                                ?.get("content")
                                ?.jsonPrimitive
                                ?.contentOrNull

                            if (messageContent.isNullOrBlank()) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        GemmaClientException("Empty model response")
                                    )
                                }
                                return
                            }

                            if (continuation.isActive) {
                                continuation.resume(messageContent)
                            }
                        } catch (t: Throwable) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    GemmaClientException("Error processing response", t)
                                )
                            }
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

    private fun extractErrorMessage(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error"]?.jsonPrimitive?.contentOrNull
                ?: obj["message"]?.jsonPrimitive?.contentOrNull
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
