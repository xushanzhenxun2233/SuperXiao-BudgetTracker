package com.superxiao.budgettracker.data

import com.superxiao.budgettracker.notification.ReminderSlot
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: String,
    val periods: List<BudgetPeriodEntity>,
    val expenses: List<ExpenseRecordEntity>,
    val quickButtons: List<QuickButtonEntity>,
    val reminders: List<ReminderSlot> = emptyList()
)

object BackupManager {
    const val CURRENT_SCHEMA_VERSION = 1

    fun toJson(
        periods: List<BudgetPeriodEntity>,
        expenses: List<ExpenseRecordEntity>,
        quickButtons: List<QuickButtonEntity>,
        reminders: List<ReminderSlot> = emptyList(),
        exportedAtIso: String = Instant.now().toString()
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("exportedAt", exportedAtIso)

        val periodsArr = JSONArray()
        periods.forEach { p ->
            periodsArr.put(JSONObject().apply {
                put("id", p.id)
                put("totalBudgetCents", p.totalBudgetCents)
                put("startDate", LocalDate.ofEpochDay(p.startDateEpochDay).toString())
                put("endDate", LocalDate.ofEpochDay(p.endDateEpochDay).toString())
                if (p.customDailyBudgetCents != null) {
                    put("customDailyBudgetCents", p.customDailyBudgetCents)
                } else {
                    put("customDailyBudgetCents", JSONObject.NULL)
                }
                put("isActive", p.isActive)
                put("createdAtMillis", p.createdAtMillis)
            })
        }
        root.put("periods", periodsArr)

        val expensesArr = JSONArray()
        expenses.forEach { e ->
            expensesArr.put(JSONObject().apply {
                put("id", e.id)
                put("periodId", e.periodId)
                put("amountCents", e.amountCents)
                put("note", e.note)
                put("timestampMillis", e.timestampMillis)
            })
        }
        root.put("expenses", expensesArr)

        val buttonsArr = JSONArray()
        quickButtons.forEach { b ->
            buttonsArr.put(JSONObject().apply {
                put("id", b.id)
                put("label", b.label)
                put("amountCents", b.amountCents)
                put("sortOrder", b.sortOrder)
            })
        }
        root.put("quickButtons", buttonsArr)

        val remindersArr = JSONArray()
        reminders.forEach { r ->
            remindersArr.put(JSONObject().apply {
                put("id", r.id)
                put("label", r.label)
                put("hour", r.hour)
                put("minute", r.minute)
                put("enabled", r.enabled)
            })
        }
        root.put("reminders", remindersArr)

        return root.toString(2)
    }

    /**
     * 完整解析并校验备份 JSON；任何结构错误、版本不支持、外键引用无效或金额非法均返回 Result.failure，绝不部分导入。
     */
    fun parseAndValidate(jsonText: String): Result<BackupPayload> {
        return try {
            val root = JSONObject(jsonText)
            if (!root.has("schemaVersion")) {
                return Result.failure(IllegalArgumentException("备份文件缺少 schemaVersion 字段"))
            }
            val version = root.getInt("schemaVersion")
            if (version != CURRENT_SCHEMA_VERSION) {
                return Result.failure(IllegalArgumentException("不支持的备份版本：v$version（当前仅支持 v$CURRENT_SCHEMA_VERSION）"))
            }
            val exportedAt = root.optString("exportedAt", "")

            val periodsArr = root.getJSONArray("periods")
            if (periodsArr.length() == 0) {
                return Result.failure(IllegalArgumentException("备份文件中没有有效的预算周期"))
            }

            val periodIds = HashSet<Long>()
            val periods = mutableListOf<BudgetPeriodEntity>()
            var activeCount = 0

            for (i in 0 until periodsArr.length()) {
                val obj = periodsArr.getJSONObject(i)
                val id = obj.getLong("id")
                if (id <= 0L || !periodIds.add(id)) {
                    return Result.failure(IllegalArgumentException("预算周期 ID 无效或重复：$id"))
                }
                val totalBudgetCents = obj.getLong("totalBudgetCents")
                if (totalBudgetCents < 0L || totalBudgetCents > MoneyUtils.MAX_CENTS) {
                    return Result.failure(IllegalArgumentException("预算周期 $id 的总预算金额非法"))
                }
                val startDate = LocalDate.parse(obj.getString("startDate"))
                val endDate = LocalDate.parse(obj.getString("endDate"))
                if (endDate.isBefore(startDate)) {
                    return Result.failure(IllegalArgumentException("预算周期 $id 的结束日期早于起始日期"))
                }
                val customDaily: Long? = if (obj.isNull("customDailyBudgetCents")) {
                    null
                } else {
                    val v = obj.getLong("customDailyBudgetCents")
                    if (v < 0L || v > MoneyUtils.MAX_CENTS) {
                        return Result.failure(IllegalArgumentException("预算周期 $id 的自定义每日预算非法"))
                    }
                    v
                }
                val isActive = obj.getBoolean("isActive")
                if (isActive) activeCount++
                val createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())

                periods.add(
                    BudgetPeriodEntity(
                        id = id,
                        totalBudgetCents = totalBudgetCents,
                        startDateEpochDay = startDate.toEpochDay(),
                        endDateEpochDay = endDate.toEpochDay(),
                        customDailyBudgetCents = customDaily,
                        isActive = isActive,
                        createdAtMillis = createdAtMillis
                    )
                )
            }

            if (activeCount != 1) {
                return Result.failure(IllegalArgumentException("备份中必须且只能有 1 个当前活跃周期（实际：$activeCount）"))
            }

            val expensesArr = root.getJSONArray("expenses")
            val expenseIds = HashSet<Long>()
            val expenses = mutableListOf<ExpenseRecordEntity>()
            for (i in 0 until expensesArr.length()) {
                val obj = expensesArr.getJSONObject(i)
                val id = obj.getLong("id")
                if (id <= 0L || !expenseIds.add(id)) {
                    return Result.failure(IllegalArgumentException("支出记录 ID 无效或重复：$id"))
                }
                val periodId = obj.getLong("periodId")
                if (periodId !in periodIds) {
                    return Result.failure(IllegalArgumentException("支出记录 $id 引用了不存在的周期 $periodId"))
                }
                val amountCents = obj.getLong("amountCents")
                if (amountCents <= 0L || amountCents > MoneyUtils.MAX_CENTS) {
                    return Result.failure(IllegalArgumentException("支出记录 $id 金额非法"))
                }
                val note = obj.optString("note", "")
                val timestampMillis = obj.getLong("timestampMillis")
                expenses.add(
                    ExpenseRecordEntity(
                        id = id,
                        periodId = periodId,
                        amountCents = amountCents,
                        note = note,
                        timestampMillis = timestampMillis
                    )
                )
            }

            val buttonsArr = root.getJSONArray("quickButtons")
            val buttonIds = HashSet<Long>()
            val buttons = mutableListOf<QuickButtonEntity>()
            for (i in 0 until buttonsArr.length()) {
                val obj = buttonsArr.getJSONObject(i)
                val id = obj.getLong("id")
                if (id <= 0L || !buttonIds.add(id)) {
                    return Result.failure(IllegalArgumentException("快捷按钮 ID 无效或重复：$id"))
                }
                val label = obj.getString("label").trim()
                if (label.isEmpty()) {
                    return Result.failure(IllegalArgumentException("快捷按钮 $id 名称为空"))
                }
                val amountCents = obj.getLong("amountCents")
                if (amountCents <= 0L || amountCents > MoneyUtils.MAX_CENTS) {
                    return Result.failure(IllegalArgumentException("快捷按钮 $id 金额非法"))
                }
                val sortOrder = obj.optInt("sortOrder", i)
                buttons.add(
                    QuickButtonEntity(
                        id = id,
                        label = label,
                        amountCents = amountCents,
                        sortOrder = sortOrder
                    )
                )
            }

            val reminders = mutableListOf<ReminderSlot>()
            if (root.has("reminders") && !root.isNull("reminders")) {
                val remindersArr = root.getJSONArray("reminders")
                for (i in 0 until remindersArr.length()) {
                    val obj = remindersArr.getJSONObject(i)
                    val id = obj.getInt("id")
                    val label = obj.optString("label", "定时提醒")
                    val hour = obj.getInt("hour")
                    val minute = obj.getInt("minute")
                    val enabled = obj.getBoolean("enabled")
                    if (hour in 0..23 && minute in 0..59) {
                        reminders.add(ReminderSlot(id, label, hour, minute, enabled))
                    }
                }
            }

            Result.success(
                BackupPayload(
                    schemaVersion = version,
                    exportedAt = exportedAt,
                    periods = periods,
                    expenses = expenses,
                    quickButtons = buttons,
                    reminders = reminders
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("备份文件格式错误：${e.message ?: "无法解析 JSON"}"))
        }
    }
}
