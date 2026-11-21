package com.superxiao.budgettracker.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.superxiao.budgettracker.MainActivity
import com.superxiao.budgettracker.R

object NotificationHelper {
    const val CHANNEL_ID = "budget_reminder_channel"
    const val KEY_TEXT_REPLY = "key_quick_expense_reply"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_DEFAULT_NOTE = "extra_default_note"

    fun ensureChannel(context: Context) {
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "定时记账提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "按设定时间提醒记账，支持通知栏直接回复记账"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        } catch (_: Exception) {
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showReminderNotification(
        context: Context,
        notificationId: Int,
        slotLabel: String
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)

        return try {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("输入金额和备注（如：25 午餐）")
                .build()

            val replyIntent = Intent(context, QuickReplyReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_DEFAULT_NOTE, slotLabel)
            }
            val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                2000 + notificationId,
                replyIntent,
                replyFlags
            )

            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_stat_budget,
                "快捷记账",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .build()

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_budget)
                .setContentTitle("超级魈提醒你记账啦 · $slotLabel")
                .setContentText("点击下方「快捷记账」直接回复金额（如 25 午餐）即可扣款")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .addAction(replyAction)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun showReplyFeedbackNotification(
        context: Context,
        notificationId: Int,
        title: String,
        content: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        try {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_budget)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
}
