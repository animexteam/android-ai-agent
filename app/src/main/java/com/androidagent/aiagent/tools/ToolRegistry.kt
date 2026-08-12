package com.androidagent.aiagent.tools

import android.util.Log
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Dynamic tool registry that manages available [AgentTool] instances.
 *
 * Tools can be registered and unregistered at runtime, and the full set
 * of tool definitions can be serialized to a JSON string for inclusion
 * in AI model prompts.
 */
class ToolRegistry {

    companion object {
        private const val TAG = "ToolRegistry"
    }

    private val tools: MutableMap<String, AgentTool> = mutableMapOf()

    /**
     * Register a tool. If a tool with the same name already exists it will
     * be silently replaced.
     *
     * @param tool The [AgentTool] to register.
     */
    fun register(tool: AgentTool) {
        val replaced = tools.put(tool.name, tool)
        if (replaced != null) {
            Log.w(TAG, "Replaced existing tool: ${tool.name}")
        } else {
            Log.d(TAG, "Registered tool: ${tool.name}")
        }
    }

    /**
     * Unregister a tool by name.
     *
     * @param name The unique tool name to remove.
     */
    fun unregister(name: String) {
        val removed = tools.remove(name)
        if (removed != null) {
            Log.d(TAG, "Unregistered tool: $name")
        } else {
            Log.w(TAG, "Attempted to unregister unknown tool: $name")
        }
    }

    /**
     * Retrieve a registered tool by name.
     *
     * @param name The tool name.
     * @return The [AgentTool], or `null` if not registered.
     */
    fun get(name: String): AgentTool? = tools[name]

    /**
     * Return all registered tools as an unmodifiable list.
     */
    fun getAll(): List<AgentTool> = tools.values.toList()

    /**
     * Check whether a tool with the given name is currently registered.
     *
     * @param name The tool name to check.
     * @return `true` if a tool with that name is registered.
     */
    fun isRegistered(name: String): Boolean = tools.containsKey(name)

    /**
     * Serialize all registered tool definitions into a JSON array string
     * suitable for embedding in an AI model prompt.
     *
     * Each entry contains:
     * - `name` (String): the tool's unique identifier
     * - `description` (String): human-readable description for the model
     * - `inputSchema` (Object): the JSON Schema describing expected arguments
     *
     * @return A compact JSON array string, or `"[]"` when no tools are registered.
     */
    fun getToolDefinitionsForPrompt(): String {
        if (tools.isEmpty()) {
            Log.w(TAG, "No tools registered; returning empty definitions array")
            return "[]"
        }

        val array = buildJsonArray {
            tools.values.forEach { tool ->
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("inputSchema", tool.inputSchema)
                })
            }
        }

        return array.toString()
    }
}
