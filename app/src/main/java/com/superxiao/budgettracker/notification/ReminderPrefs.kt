package com.superxiao.budgettracker.notification

import android.content.Context

data class ReminderSlot(
    val id: Int,
    val label: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean
) {
    fun formattedTime(): String = String.format("%02d:%02d", hour, minute)
}

object ReminderPrefs {
    private const val PREF_NAME = "budget_reminder_prefs"

    private val defaultSlots = listOf(
        ReminderSlot(id = 1, label = "早上提醒", hour = 9, minute = 0, enabled = false),
        ReminderSlot(id = 2, label = "中午提醒", hour = 12, minute = 30, enabled = false),
        ReminderSlot(id = 3, label = "晚上提醒", hour = 21, minute = 0, enabled = false)
    )

    fun loadSlots(context: Context): List<ReminderSlot> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return defaultSlots.map { def ->
            ReminderSlot(
                id = def.id,
                label = def.label,
                hour = prefs.getInt("slot_${def.id}_hour", def.hour),
                minute = prefs.getInt("slot_${def.id}_minute", def.minute),
                enabled = prefs.getBoolean("slot_${def.id}_enabled", def.enabled)
            )
        }
    }

    fun saveSlot(context: Context, slot: ReminderSlot) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("slot_${slot.id}_hour", slot.hour)
            .putInt("slot_${slot.id}_minute", slot.minute)
            .putBoolean("slot_${slot.id}_enabled", slot.enabled)
            .apply()
    }

    fun getSlot(context: Context, id: Int): ReminderSlot? {
        return loadSlots(context).firstOrNull { it.id == id }
    }
}
