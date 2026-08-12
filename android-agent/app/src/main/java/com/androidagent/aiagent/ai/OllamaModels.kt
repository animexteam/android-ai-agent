package com.androidagent.aiagent.ai

import kotlinx.serialization.Serializable

/**
 * Response from the Ollama `GET /api/tags` endpoint.
 *
 * Usage example:
 * ```
 * val models = Json.decodeFromString<OllamaModels>(responseBody)
 * models.models.forEach { println(it.name) }
 * ```
 */
@Serializable
data class OllamaModels(
    val models: List<ModelInfo> = emptyList(),
) {
    /**
     * Returns a list of model name strings (e.g. "gemma4:31b").
     */
    val modelNames: List<String> get() = models.map { it.name }

    /**
     * Finds a [ModelInfo] by exact name match, or null if not present.
     */
    fun findByName(name: String): ModelInfo? = models.find { it.name == name }

    /**
     * Returns true when no models are available.
     */
    val isEmpty: Boolean get() = models.isEmpty()
}

/**
 * Metadata about a single model available on the Ollama server.
 *
 * @param name       The model identifier (e.g. "gemma4:31b").
 * @param size       Size of the model in bytes, if reported by the server.
 * @param modified_at ISO-8601 timestamp of when the model was last modified, if available.
 */
@Serializable
data class ModelInfo(
    val name: String,
    val size: Long? = null,
    val modified_at: String? = null,
) {
    /**
     * Human-readable size string (e.g. "4.2 GB").
     */
    val formattedSize: String?
        get() {
            if (size == null) return null
            val gb = size / (1_073_741_824.0)
            val mb = size / (1_048_576.0)
            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                else -> "$size B"
            }
        }
}
