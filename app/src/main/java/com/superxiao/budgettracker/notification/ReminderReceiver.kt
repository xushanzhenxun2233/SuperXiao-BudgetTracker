package com.superxiao.budgettracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
            return
        }

        val slotId = intent.getIntExtra(ReminderScheduler.EXTRA_SLOT_ID, 1)
        val slotLabel = intent.getStringExtra(ReminderScheduler.EXTRA_SLOT_LABEL) ?: "定时提醒"

        NotificationHelper.showReminderNotification(
            context = context,
            notificationId = 3000 + slotId,
            slotLabel = slotLabel
        )

        // 自动预约明天的同一时间
        ReminderPrefs.getSlot(context, slotId)?.let { slot ->
            if (slot.enabled) {
                ReminderScheduler.scheduleSlot(context, slot)
            }
        }
    }
}
