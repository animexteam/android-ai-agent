package com.androidagent.aiagent.service

import android.app.Notification
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
        private const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "agent_service"
        private const val TAG = "AgentForegroundService"

        private var currentGoal: String = ""
        private var currentStatusText: String = "idle"

        fun start(context: Context, goal: String) {
            currentGoal = goal
            currentStatusText = "running"
            val intent = Intent(context, AgentForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            currentGoal = ""
            currentStatusText = "idle"
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }

        /**
         * Update the foreground notification with the current agent status and goal.
         * Can be called from anywhere (ViewModel, etc.) without needing a service reference.
         */
        fun updateNotification(context: Context, status: AgentStatus, goal: String) {
            currentGoal = goal
            currentStatusText = when (status) {
                AgentStatus.IDLE -> "idle"
                AgentStatus.THINKING -> "running"
                AgentStatus.EXECUTING -> "running"
                AgentStatus.WAITING_FOR_USER -> "waiting for input"
                AgentStatus.WAITING_FOR_CONFIRMATION -> "waiting for confirmation"
                AgentStatus.VERIFYING -> "running"
                AgentStatus.COMPLETED -> "completed"
                AgentStatus.FAILED -> "failed"
                AgentStatus.CANCELLED -> "cancelled"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(context, currentStatusText, currentGoal))
        }

        private fun buildNotification(context: Context, status: String, goal: String): Notification {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = when (status) {
                "running" -> "Agent Running"
                "waiting for input" -> "Agent Waiting"
                "waiting for confirmation" -> "Agent Waiting"
                "completed" -> "Agent Completed"
                "failed" -> "Agent Failed"
                "cancelled" -> "Agent Stopped"
                else -> "Agent Idle"
            }

            val isOngoing = status == "running" || status == "waiting for input" || status == "waiting for confirmation"

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(goal)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(this, currentStatusText, currentGoal)
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }
}
