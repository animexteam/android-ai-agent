package com.androidagent.aiagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.androidagent.aiagent.R
import com.androidagent.aiagent.agent.AgentStatus
import com.androidagent.aiagent.ui.MainActivity


class AgentForegroundService : Service() {

    companion object {
        private const val TAG = "AgentFGS"
        const val CHANNEL_ID = "android_use_agent"
        private const val NOTIFICATION_ID = 1001

        private var currentGoal: String = ""
        private var currentStatusText: String = "ready"

        /**
         * Create the notification channel. MUST be called before starting
         * the service on Android 8+. Safe to call multiple times.
         */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Android-Use Agent",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows the current status of the AI agent"
                        setShowBadge(false)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }

        fun start(context: Context, goal: String) {
            createChannel(context)
            currentGoal = goal
            currentStatusText = "working"
            val intent = Intent(context, AgentForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            currentGoal = ""
            currentStatusText = "ready"
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }

        fun updateNotification(context: Context, status: AgentStatus, goal: String) {
            createChannel(context)
            currentGoal = goal
            currentStatusText = statusToText(status)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(context, currentStatusText, currentGoal))
        }

        private fun statusToText(status: AgentStatus): String = when (status) {
            AgentStatus.IDLE -> "ready"
            AgentStatus.THINKING -> "thinking"
            AgentStatus.EXECUTING -> "working"
            AgentStatus.WAITING_FOR_USER -> "waiting for you"
            AgentStatus.WAITING_FOR_CONFIRMATION -> "needs confirmation"
            AgentStatus.VERIFYING -> "verifying"
            AgentStatus.COMPLETED -> "done"
            AgentStatus.FAILED -> "failed"
            AgentStatus.CANCELLED -> "stopped"
        }

        private fun buildNotification(context: Context, status: String, goal: String): Notification {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = when (status) {
                "working", "thinking", "verifying" -> "Android-Use is working"
                "waiting for you" -> "Android-Use needs input"
                "needs confirmation" -> "Android-Use needs confirmation"
                "done" -> "Android-Use finished"
                "failed" -> "Android-Use failed"
                "stopped" -> "Android-Use stopped"
                else -> "Android-Use is ready"
            }

            val isOngoing = status in listOf("working", "thinking", "verifying", "waiting for you", "needs confirmation")

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle(title)
                .setContentText(goal.take(80))
                .setStyle(NotificationCompat.BigTextStyle().bigText(goal))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isOngoing)
                .setContentIntent(pendingIntent)
                .setSilent(true)
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(this, currentStatusText, currentGoal)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY  // Restart if killed — critical for background execution
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }
}
