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

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class GetNetworkInfoTool : ToolHandler {
    override suspend fun execute(args: JsonObject): ToolResult {
        val service = AndroidAgentAccessibilityService.instance ?: return noService()
        return try {
            withContext(Dispatchers.IO) {
                val cm = service.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(network)
                val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                val hasEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                val downstreamMbps = caps?.getLinkDownstreamBandwidthKbps()?.div(1000) ?: 0
                val wifiManager = service.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val wifiInfo = wifiManager?.connectionInfo
                val ssid = try { wifiInfo?.ssid?.removeSurrounding(String(charArrayOf(34))) } catch (_: Exception) { null }
                val ip = try { java.net.InetAddress.getByAddress(
                    java.math.BigInteger.valueOf(wifiInfo?.ipAddress?.toLong() ?: 0).toByteArray()
                ).hostAddress } catch (_: Exception) { null }
                ToolResult(
                    success = true, toolName = TOOL_NAME,
                    result = buildJsonObject {
                        put("is_connected", isConnected)
                        put("has_wifi", hasWifi)
                        put("has_cellular", hasCellular)
                        put("has_ethernet", hasEthernet)
                        put("wifi_ssid", ssid ?: "not connected")
                        put("ip_address", ip ?: "unknown")
                        put("downstream_mbps", downstreamMbps)
                    },
                    observationRequired = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get network info", e)
            errorResult(e)
        }
    }
    companion object {
        internal const val TOOL_NAME = "android.get_network_info"
        private const val TAG = "GetNetworkInfoTool"
        fun definition(): AgentTool = AgentTool(
            name = TOOL_NAME, description = "Get network connectivity info: WiFi SSID, IP address, connection type, speed.",
            inputSchema = buildJsonObject { put("type", "object") },
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
        error = ToolError(code = "NETWORK_INFO_FAILED", message = e.message ?: "Unknown error")
    )
}
