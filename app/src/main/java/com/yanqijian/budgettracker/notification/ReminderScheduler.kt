package com.yanqijian.budgettracker.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ReminderScheduler {
    const val EXTRA_SLOT_ID = "extra_slot_id"
    const val EXTRA_SLOT_LABEL = "extra_slot_label"

    fun scheduleSlot(context: Context, slot: ReminderSlot) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = buildPendingIntent(context, slot)

            if (!slot.enabled) {
                alarmManager.cancel(pendingIntent)
                return
            }

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, slot.hour)
                set(Calendar.MINUTE, slot.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun rescheduleAll(context: Context) {
        ReminderPrefs.loadSlots(context).forEach { slot ->
            scheduleSlot(context, slot)
        }
    }

    private fun buildPendingIntent(context: Context, slot: ReminderSlot): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_SLOT_ID, slot.id)
            putExtra(EXTRA_SLOT_LABEL, slot.label)
        }
        return PendingIntent.getBroadcast(
            context,
            1000 + slot.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
