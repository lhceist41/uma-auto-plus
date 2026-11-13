package com.steve1316.uma_android_automation

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.steve1316.automation_library.utils.BotService

/**
 * Tells the player at once when the previous process died mid-session (low memory, the stall
 * watchdog's kill, a crash, a reboot). Android restarts the process only to rebind the accessibility
 * service, and nothing else runs until the app is opened, so without this the report waits for Home.
 * Uses `android.util.Log` only: MessageLog is not running in a restarted process.
 */
internal object DeadSessionNotice {
    private const val TAG = "DeadSessionNotice"

    /** Not the library's id 1, whose notification is ongoing and carries a Stop action. */
    internal const val NOTIFICATION_ID = 2

    /** The library's status channel, so the player's settings for it apply. */
    internal const val CHANNEL_ID = "STATUS"

    /** Only a healthy database is touched at start; a damaged one waits for Home, as before. */
    internal fun checksAtStart(state: SettingsDatabase.StartState): Boolean = state == SettingsDatabase.StartState.OPEN || state == SettingsDatabase.StartState.RESTORED

    /** The report's own words, or null when it is not a mid-session death the player did not cause. */
    internal fun noticeText(report: QueueReport?): ReportText? {
        if (report == null || report.kind != SessionEnd.PROCESS_ENDED) return null
        if (report.exitInfo?.optString("reason") == "USER_REQUESTED") return null
        val text = queueReportText(report.toJson())
        return if (text.nextAction == null) text.copy(nextAction = pressStart(false).replaceFirstChar { it.uppercase() }) else text
    }

    /**
     * Records the dead session's report, then posts its notice only when this call recorded it.
     * Returns whether it posted. Never throws: a lost notice must not break app start.
     */
    internal fun notifyDeadSession(
        reportDeadSession: () -> QueueReport?,
        sdkInt: Int,
        canPost: () -> Boolean,
        sessionRunning: () -> Boolean,
        ensureChannel: () -> Unit,
        post: (ReportText) -> Unit,
    ): Boolean =
        try {
            val text = noticeText(reportDeadSession())
            if (text == null || (sdkInt >= 33 && !canPost()) || sessionRunning()) {
                false
            } else {
                ensureChannel()
                post(text)
                true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to post the dead-session notice: ${e.message}")
            false
        }

    /** Called from `MainApplication.onCreate` after the settings database is open; the work runs off the main thread. */
    fun postAtProcessStart(application: Application) {
        try {
            if (!checksAtStart(SettingsDatabase.startState)) return
            Thread {
                notifyDeadSession(
                    reportDeadSession = { QueueLedger.reportDeadSession(application) },
                    sdkInt = Build.VERSION.SDK_INT,
                    canPost = { ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED },
                    sessionRunning = { BotService.isRunning },
                    ensureChannel = { ensureChannel(application) },
                    post = { post(application, it) },
                )
            }.apply {
                name = "DeadSessionNotice"
                isDaemon = true
                start()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to start the dead-session check: ${e.message}")
        }
    }

    /** Removes the notice; a new session no longer needs it. Never throws. */
    fun cancel(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to remove the dead-session notice: ${e.message}")
        }
    }

    /** Creates the channel only when the library has not, with the library's own settings, so it never changes them. */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val name = context.getString(R.string.app_name)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply { description = "Displays status of $name, whether it is running or not." })
    }

    private fun post(context: Context, text: ReportText) {
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_control_camera_24)
                .setContentTitle(text.title)
                .setContentText(text.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
