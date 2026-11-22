package com.superxiao.budgettracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

class BudgetRepository(private val database: BudgetDatabase) {
    private val dao = database.budgetDao()

    val activePeriodFlow: Flow<BudgetPeriodEntity?> = dao.observeActivePeriod()
    val allPeriodsFlow: Flow<List<BudgetPeriodEntity>> = dao.observeAllPeriods()
    val quickButtonsFlow: Flow<List<QuickButtonEntity>> = dao.observeQuickButtons()
    val allExpensesFlow: Flow<List<ExpenseRecordEntity>> = dao.observeAllExpenses()

    suspend fun ensureInitialized() {
        database.withTransaction {
            if (dao.getActivePeriodOnce() == null) {
                val today = LocalDate.now()
                val ym = YearMonth.from(today)
                dao.insertPeriod(
                    BudgetPeriodEntity(
                        totalBudgetCents = 100_000L, // 默认 ¥1000.00
                        startDateEpochDay = ym.atDay(1).toEpochDay(),
                        endDateEpochDay = ym.atEndOfMonth().toEpochDay(),
                        customDailyBudgetCents = null,
                        isActive = true
                    )
                )
            }
            if (dao.getQuickButtonCount() == 0) {
                dao.insertQuickButton(QuickButtonEntity(label = "早饭", amountCents = 500L, sortOrder = 0))
                dao.insertQuickButton(QuickButtonEntity(label = "午饭", amountCents = 1_200L, sortOrder = 1))
                dao.insertQuickButton(QuickButtonEntity(label = "晚饭", amountCents = 1_200L, sortOrder = 2))
                dao.insertQuickButton(QuickButtonEntity(label = "奶茶", amountCents = 600L, sortOrder = 3))
            }
        }
    }

    suspend fun updateActivePeriod(transform: (BudgetPeriodEntity) -> BudgetPeriodEntity) {
        database.withTransaction {
            val current = dao.getActivePeriodOnce() ?: return@withTransaction
            dao.updatePeriod(transform(current))
        }
    }

    /** 事务内原子写入一笔支出，并返回扣款后当前周期的剩余金额（分） */
    suspend fun addExpenseTransactional(
        amountCents: Long,
        note: String,
        timestampMillis: Long = System.currentTimeMillis()
    ): Long? {
        return database.withTransaction {
            val active = dao.getActivePeriodOnce() ?: return@withTransaction null
            dao.insertExpense(
                ExpenseRecordEntity(
                    periodId = active.id,
                    amountCents = amountCents,
                    note = note.trim(),
                    timestampMillis = timestampMillis
                )
            )
            val spent = dao.getExpensesForPeriodOnce(active.id).sumOf { it.amountCents }
            active.totalBudgetCents - spent
        }
    }

    suspend fun updateExpenseTimestamp(expense: ExpenseRecordEntity, newTimestampMillis: Long) {
        dao.updateExpense(expense.copy(timestampMillis = newTimestampMillis))
    }

    suspend fun deleteExpense(expense: ExpenseRecordEntity) {
        dao.deleteExpense(expense)
    }

    /** 仅清空当前活跃周期的流水，保留历史周期、起止日期、快捷按钮与每日预算模式 */
    suspend fun resetActivePeriodExpenses() {
        database.withTransaction {
            val active = dao.getActivePeriodOnce() ?: return@withTransaction
            dao.clearExpensesForPeriod(active.id)
        }
    }

    /** 开启新预算周期：自动归档旧周期并保留其所有历史流水 */
    suspend fun startNewPeriod(
        totalBudgetCents: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        database.withTransaction {
            dao.deactivateAllPeriods()
            dao.insertPeriod(
                BudgetPeriodEntity(
                    totalBudgetCents = totalBudgetCents,
                    startDateEpochDay = startDate.toEpochDay(),
                    endDateEpochDay = endDate.toEpochDay(),
                    customDailyBudgetCents = null,
                    isActive = true,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun addQuickButton(label: String, amountCents: Long) {
        database.withTransaction {
            val existing = dao.getQuickButtonsOnce()
            val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            dao.insertQuickButton(
                QuickButtonEntity(
                    label = label.trim(),
                    amountCents = amountCents,
                    sortOrder = nextOrder
                )
            )
        }
    }

    suspend fun updateQuickButton(button: QuickButtonEntity) {
        dao.updateQuickButton(button)
    }

    suspend fun deleteQuickButton(button: QuickButtonEntity) {
        dao.deleteQuickButton(button)
    }

    suspend fun moveQuickButton(buttonId: Long, moveUp: Boolean) {
        database.withTransaction {
            val list = dao.getQuickButtonsOnce().toMutableList()
            val index = list.indexOfFirst { it.id == buttonId }
            if (index == -1) return@withTransaction
            val targetIndex = if (moveUp) index - 1 else index + 1
            if (targetIndex !in list.indices) return@withTransaction

            val item = list.removeAt(index)
            list.add(targetIndex, item)
            val reindexed = list.mapIndexed { idx, btn -> btn.copy(sortOrder = idx) }
            dao.updateQuickButtons(reindexed)
        }
    }

    suspend fun exportBackupJson(reminders: List<com.superxiao.budgettracker.notification.ReminderSlot> = emptyList()): String {
        return database.withTransaction {
            val periods = dao.getAllPeriodsOnce()
            val expenses = dao.getAllExpensesOnce()
            val buttons = dao.getQuickButtonsOnce()
            BackupManager.toJson(periods, expenses, buttons, reminders)
        }
    }

    suspend fun importBackupJson(jsonText: String): Result<List<com.superxiao.budgettracker.notification.ReminderSlot>> {
        val parsed = BackupManager.parseAndValidate(jsonText)
        val payload = parsed.getOrElse { return Result.failure(it) }
        return try {
            database.withTransaction {
                dao.deleteAllExpenses()
                dao.deleteAllPeriods()
                dao.deleteAllQuickButtons()
                dao.insertPeriods(payload.periods)
                dao.insertExpenses(payload.expenses)
                dao.insertQuickButtons(payload.quickButtons)
            }
            Result.success(payload.reminders)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
