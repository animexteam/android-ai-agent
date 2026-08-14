package com.androidagent.aiagent.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent user memory that survives across chat sessions.
 * Stores facts about the user, preferences, and learned information.
 *
 * Design: Simple key-value store backed by DataStore.
 * The LLM can save facts during conversations, and these are
 * injected into the system prompt so the agent remembers the user.
 */
class UserMemory(private val context: Context) {

    companion object {
        private const val TAG = "UserMemory"
        private const val MEMORY_KEY = "user_memory_facts"
        private const val PREFS_NAME = "android_use_memory"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    @Serializable
    data class MemoryFact(
        val key: String,
        val value: String,
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

    suspend fun saveFact(key: String, value: String) = withContext(Dispatchers.IO) {
        try {
            val facts = getFacts().toMutableList()
            // Update existing or add new
            val existingIndex = facts.indexOfFirst { it.key.equals(key, ignoreCase = true) }
            val fact = MemoryFact(key = key, value = value)
            if (existingIndex >= 0) {
                facts[existingIndex] = fact
            } else {
                facts.add(fact)
            }
            // Keep max 50 facts to avoid bloat
            val trimmed = if (facts.size > 50) facts.takeLast(50) else facts
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(MEMORY_KEY, json.encodeToString(trimmed)).apply()
            Log.d(TAG, "Saved fact: $key = $value")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save fact", e)
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
     */
    suspend fun buildMemoryBlock(): String {
        val facts = getFacts()
        if (facts.isEmpty()) return ""
        return buildString {
            appendLine("## About the User (remembered facts)")
            for (fact in facts) {
                appendLine("- ${fact.key}: ${fact.value}")
            }
        }
    }
}
