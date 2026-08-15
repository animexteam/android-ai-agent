package com.androidagent.aiagent.tools.android

import android.util.Log
import com.androidagent.aiagent.accessibility.AndroidAgentAccessibilityService
import com.androidagent.aiagent.tools.AgentTool
import com.androidagent.aiagent.tools.RiskLevel
import com.androidagent.aiagent.tools.ToolError
import com.androidagent.aiagent.tools.ToolHandler
import com.androidagent.aiagent.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import android.provider.ContactsContract

class GetContactsTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val limit = args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 20
        val query = args["query"]?.jsonPrimitive?.content
        return try {
            withContext(Dispatchers.IO) {
                val contacts = mutableListOf<kotlin.Pair<String, String>>()
                val selection = if (query != null) "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?" else null
                val selectionArgs = if (query != null) arrayOf("%$query%") else null
                val cursor = service.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                    selection, selectionArgs,
                    "${ContactsContract.Contacts.DISPLAY_NAME} ASC LIMIT $limit"
                )
                cursor?.use {
                    while (it.moveToNext() && contacts.size < limit) {
                        val id = it.getString(0)
                        val name = it.getString(1)
                        val phoneCursor = service.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id), null
                        )
                        val phone = phoneCursor?.use { pc ->
                            if (pc.moveToFirst()) pc.getString(0) else null
                        }
                        contacts.add(name to (phone ?: "no phone"))
                    }
                }
                val resultArray = buildJsonArray {
                    for ((name, phone) in contacts) {
                        add(buildJsonObject { put("name", name); put("phone", phone) })
                    }
                }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("contacts", resultArray); put("count", contacts.size) },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get contacts", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_contacts"
        private const val TAG = "GetContactsTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get contacts list with names and phone numbers. Optionally filter by query or limit count.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("query", buildJsonObject { put("type", "string"); put("description", "Search query to filter contacts by name") })
                    put("limit", buildJsonObject { put("type", "integer"); put("description", "Max contacts to return (default 20)") })
                })
            },
            riskLevel = RiskLevel.SAFE, requiresConfirmation = false
        )
    }
        private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "CONTACTS_FAILED", message = e.message ?: "Unknown error")
    )
}
