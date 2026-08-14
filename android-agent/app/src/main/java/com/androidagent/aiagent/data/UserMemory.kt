package com.androidagent.aiagent.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent user memory that survives across chat sessions.
 * Stores ONLY user preferences and facts — NEVER action patterns.
 *
 * Critical fix: The LLM must NOT save memories about specific actions
 * (e.g., "sent Hello 2 times on WhatsApp"). Only user-level facts
 * like "user's best friend is Rahul" or "user prefers dark mode".
 */
class UserMemory(private val context: Context) {

    companion object {
        private const val TAG = "UserMemory"
        private const val MEMORY_KEY = "user_memory_facts"
        private const val PREFS_NAME = "android_use_memory"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Categories that are ALLOWED to be stored. */
        private val ALLOWED_CATEGORIES = setOf(
            "personal", "preference", "contact", "habit", "fact"
        )

        /** Keywords that indicate an ACTION memory (which we must REJECT). */
        private val ACTION_KEYWORDS = listOf(
            "sent", "clicked", "tapped", "typed", "scrolled", "opened",
            "launched", "pressed", "swiped", "navigated", "searched",
            "times", "repeated", "again", "performed", "executed",
            "message sent", "hello", "message to", "messaged"
        )
    }

    @Serializable
    data class MemoryFact(
        val key: String,
        val value: String,
        val category: String = "fact",
        val timestamp: Long = System.currentTimeMillis()
    )

    // ------------------------------------------------------------------
    // Read/Write
    // ------------------------------------------------------------------

    suspend fun getFacts(): List<MemoryFact> = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(MEMORY_KEY, null) ?: return@withContext emptyList()
            json.decodeFromString<List<MemoryFact>>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read memory", e)
            emptyList()
        }
    }

    /**
     * Save a fact ONLY if it passes the action-filter check.
     * This prevents the agent from storing action patterns that
     * get incorrectly reused in future tasks.
     */
    suspend fun saveFact(key: String, value: String, category: String = "fact") = withContext(Dispatchers.IO) {
        try {
            // REJECT action-pattern memories
            if (isActionMemory(key, value)) {
                Log.d(TAG, "Blocked action memory: key=$key, value=$value")
                return@withContext
            }

            val facts = getFacts().toMutableList()
            // Update existing or add new
            val existingIndex = facts.indexOfFirst { it.key.equals(key, ignoreCase = true) }
            val fact = MemoryFact(key = key, value = value, category = category)
            if (existingIndex >= 0) {
                facts[existingIndex] = fact
            } else {
                facts.add(fact)
            }
            // Keep max 30 facts to avoid bloat (reduced from 50)
            val trimmed = if (facts.size > 30) facts.takeLast(30) else facts
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(MEMORY_KEY, json.encodeToString(trimmed)).apply()
            Log.d(TAG, "Saved fact: $key = $value (category=$category)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save fact", e)
        }
    }

    /**
     * Check if a memory is an action pattern that should NOT be stored.
     * Action memories describe WHAT the agent DID, not WHO the user IS.
     */
    private fun isActionMemory(key: String, value: String): Boolean {
        val combined = "$key $value".lowercase()
        return ACTION_KEYWORDS.any { keyword ->
            combined.contains(keyword)
        }
    }

    suspend fun deleteFact(key: String) = withContext(Dispatchers.IO) {
        try {
            val facts = getFacts().toMutableList()
            facts.removeAll { it.key.equals(key, ignoreCase = true) }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(MEMORY_KEY, json.encodeToString(facts)).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete fact", e)
        }
    }

    /**
     * Clear all memories. Called from settings.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear memory", e)
        }
    }

    /**
     * Build a compact memory block for the system prompt.
     * Only includes factual preferences, NOT action history.
     */
    suspend fun buildMemoryBlock(): String {
        val facts = getFacts()
        if (facts.isEmpty()) return ""
        return buildString {
            appendLine("## About the User (remembered facts)")
            appendLine("These are facts about the user. Do NOT assume the user wants to repeat past actions.")
            appendLine("Each task is independent — only use these facts for context, NOT for action patterns.")
            appendLine()
            for (fact in facts) {
                appendLine("- ${fact.key}: ${fact.value}")
            }
        }
    }
}
