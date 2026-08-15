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

import android.content.ContentValues
import android.provider.ContactsContract

class CreateContactTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        val name = args["name"]?.jsonPrimitive?.content
            ?: return ToolResult(success = false, toolName = TOOL_NAME,
                error = ToolError(code = "INVALID_INPUT", message = "'name' is required"))
        val phone = args["phone"]?.jsonPrimitive?.content
        val email = args["email"]?.jsonPrimitive?.content
        return try {
            withContext(Dispatchers.IO) {
                val ops = ArrayList<android.content.ContentProviderOperation>()
                val rawContactIndex = 0
                ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build())
                ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build())
                if (phone != null) {
                    ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build())
                }
                if (email != null) {
                    ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                        .build())
                }
                val results = service.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject { put("name", name); put("phone", phone ?: ""); put("email", email ?: ""); put("action", "contact_created") },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create contact", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.create_contact"
        private const val TAG = "CreateContactTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Create a new contact with name, optional phone and email.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("name", buildJsonObject { put("type", "string"); put("description", "Contact display name") })
                    put("phone", buildJsonObject { put("type", "string"); put("description", "Phone number") })
                    put("email", buildJsonObject { put("type", "string"); put("description", "Email address") })
                })
                put("required", buildJsonArray { add(JsonPrimitive("name")) })
            },
            riskLevel = RiskLevel.CONFIRM, requiresConfirmation = true
        )
    }
        private fun noService() = ToolResult(
        success = false,
        toolName = TOOL_NAME,
        error = ToolError(code = "SERVICE_NOT_CONNECTED", message = "Accessibility service is not connected")
    )

    private fun errorResult(e: Exception) = ToolResult(
        success = false, toolName = TOOL_NAME,
        error = ToolError(code = "CREATE_CONTACT_FAILED", message = e.message ?: "Unknown error")
    )
}
