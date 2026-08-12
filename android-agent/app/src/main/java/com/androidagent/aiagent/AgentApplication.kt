package com.androidagent.aiagent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.androidagent.aiagent.data.SecureStorage
import com.androidagent.aiagent.data.SettingsRepository
import com.androidagent.aiagent.data.TaskRepository
import com.androidagent.aiagent.data.AppDatabase

class AgentApplication : Application() {

    val secureStorage: SecureStorage by lazy { SecureStorage(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this, secureStorage) }
    val database: AppDatabase by lazy { AppDatabase.provide(this) }
    val taskRepository: TaskRepository by lazy { TaskRepository(database) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_AGENT_SERVICE,
                "Agent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for the AI agent service status"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_AGENT_SERVICE = "agent_service"
    }
}