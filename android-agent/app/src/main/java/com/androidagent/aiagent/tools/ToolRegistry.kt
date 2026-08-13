package com.androidagent.aiagent.tools

import android.util.Log

/**
 * Dynamic registry that manages available [AgentTool] definitions.
 *
 * Tools can be registered and unregistered at runtime.  The registry
 * provides the full tool list so the prompt builder can embed tool
 * descriptions in the model's system prompt.
 *
 * NOTE: Tool *execution* is handled by [ToolExecutor], not by this
 * class.  The registry is purely a catalogue.
 */
class ToolRegistry {

    companion object {
        private const val TAG = "ToolRegistry"
    }

    private val tools: MutableMap<String, AgentTool> = mutableMapOf()

    /**
     * Register a tool.  If a tool with the same name already exists
     * it is silently replaced.
     */
    fun register(tool: AgentTool) {
        val replaced = tools.put(tool.name, tool)
        if (replaced != null) {
            Log.w(TAG, "Replaced existing tool: ${tool.name}")
        } else {
            Log.d(TAG, "Registered tool: ${tool.name}")
        }
    }

    /** Unregister a tool by name.  No-op if not found. */
    fun unregister(name: String) {
        val removed = tools.remove(name)
        if (removed != null) {
            Log.d(TAG, "Unregistered tool: $name")
        } else {
            Log.w(TAG, "Attempted to unregister unknown tool: $name")
        }
    }

    /** Retrieve a registered tool by name, or null. */
    fun get(name: String): AgentTool? = tools[name]

    /** Return all registered tools as a snapshot list. */
    fun getAll(): List<AgentTool> = tools.values.toList()

    /** Return the set of currently registered tool names. */
    fun getToolNames(): Set<String> = tools.keys.toSet()

    /** Check whether a tool with the given name is registered. */
    fun isRegistered(name: String): Boolean = tools.containsKey(name)

    /** Number of currently registered tools. */
    fun size(): Int = tools.size
}
