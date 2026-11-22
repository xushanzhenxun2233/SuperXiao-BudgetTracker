package com.yanqijian.budgettracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yanqijian.budgettracker.data.BudgetPeriodEntity
import com.yanqijian.budgettracker.data.BudgetRepository
import com.yanqijian.budgettracker.data.ExpenseRecordEntity
import com.yanqijian.budgettracker.data.MoneyUtils
import com.yanqijian.budgettracker.data.QuickButtonEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class PeriodHistorySummary(
    val period: BudgetPeriodEntity,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val spentCents: Long,
    val remainingCents: Long,
    val expenses: List<ExpenseRecordEntity>
)

data class BudgetUiState(
    val isLoaded: Boolean = false,
    val activePeriodId: Long = 0L,
    val totalBudgetCents: Long = 100_000L,
    val spentCents: Long = 0L,
    val remainingCents: Long = 100_000L,
    val startDate: LocalDate = YearMonth.now().atDay(1),
    val endDate: LocalDate = YearMonth.now().atEndOfMonth(),
    val today: LocalDate = LocalDate.now(),
    val remainingDays: Int = 1,
    val autoDailyCents: Long = 0L,
    val customDailyBudgetCents: Long? = null,
    val quickButtons: List<QuickButtonEntity> = emptyList(),
    val expenses: List<ExpenseRecordEntity> = emptyList(),
    val allExpenses: List<ExpenseRecordEntity> = emptyList(),
    val periodHistories: List<PeriodHistorySummary> = emptyList()
) {
    val isPeriodEnded: Boolean
        get() = today.isAfter(endDate)

    val isDailyBudgetCustom: Boolean
        get() = customDailyBudgetCents != null

    val effectiveDailyCents: Long
        get() = if (isPeriodEnded) 0L else (customDailyBudgetCents ?: autoDailyCents)

    /** 按自定义每日预算，剩余天数预计总花销（分） */
    val plannedTotalSpendCents: Long?
        get() = customDailyBudgetCents?.let { it * remainingDays }

    /** 按自定义每日预算，到期预计结余（分；>=0 能剩钱，<0 缺口） */
    val projectedEndBalanceCents: Long?
        get() = plannedTotalSpendCents?.let { remainingCents - it }

    /** 每日花销预算建议（分；最低为 0） */
    val suggestedDailyBudgetCents: Long
        get() = if (remainingDays > 0) (remainingCents / remainingDays).coerceAtLeast(0L) else 0L
}

class BudgetViewModel(private val repository: BudgetRepository) : ViewModel() {

    private val todayFlow = MutableStateFlow(LocalDate.now())
    private val _messageFlow = MutableSharedFlow<String>()
    val messageFlow: SharedFlow<String> = _messageFlow.asSharedFlow()

    val uiState: StateFlow<BudgetUiState> = combine(
        repository.allPeriodsFlow,
        repository.quickButtonsFlow,
        repository.allExpensesFlow,
        todayFlow
    ) { periods, buttons, allExpenses, today ->
        val active = periods.firstOrNull { it.isActive }
        if (active == null) {
            BudgetUiState(isLoaded = false, today = today)
        } else {
            val expensesByPeriod = allExpenses.groupBy { it.periodId }
            val activeExpenses = expensesByPeriod[active.id].orEmpty()
            val spentCents = activeExpenses.sumOf { it.amountCents }
            val remainingCents = active.totalBudgetCents - spentCents
            val start = LocalDate.ofEpochDay(active.startDateEpochDay)
            val end = LocalDate.ofEpochDay(active.endDateEpochDay)
            val remainingDays = MoneyUtils.calculateRemainingDays(today, start, end)
            val autoDailyCents = MoneyUtils.calculateAutoDailyCents(remainingCents, remainingDays)

            val histories = periods.map { p ->
                val list = expensesByPeriod[p.id].orEmpty()
                val spent = list.sumOf { it.amountCents }
                PeriodHistorySummary(
                    period = p,
                    startDate = LocalDate.ofEpochDay(p.startDateEpochDay),
                    endDate = LocalDate.ofEpochDay(p.endDateEpochDay),
                    spentCents = spent,
                    remainingCents = p.totalBudgetCents - spent,
                    expenses = list
                )
            }

            BudgetUiState(
                isLoaded = true,
                activePeriodId = active.id,
                totalBudgetCents = active.totalBudgetCents,
                spentCents = spentCents,
                remainingCents = remainingCents,
                startDate = start,
                endDate = end,
                today = today,
                remainingDays = remainingDays,
                autoDailyCents = autoDailyCents,
                customDailyBudgetCents = active.customDailyBudgetCents,
                quickButtons = buttons,
                expenses = activeExpenses,
                allExpenses = allExpenses,
                periodHistories = histories
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
        }
    }

    /** 切回前台或跨零点时刷新本地日期 */
    fun refreshToday() {
        val now = LocalDate.now()
        if (todayFlow.value != now) {
            todayFlow.value = now
        }
    }

    fun recordExpenseFromText(
        amountText: String,
        note: String,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val cents = MoneyUtils.parseYuanToCents(amountText, allowZero = false)
            if (cents == null) {
                _messageFlow.emit("请输入有效的正数金额（最多两位小数）")
                return@launch
            }
            repository.addExpenseTransactional(cents, note, timestampMillis)
            _messageFlow.emit("已扣减 ¥${MoneyUtils.formatCents(cents)}")
        }
    }

    fun updateExpenseTimestamp(expense: ExpenseRecordEntity, newTimestampMillis: Long) {
        viewModelScope.launch {
            repository.updateExpenseTimestamp(expense, newTimestampMillis)
            _messageFlow.emit("记录时间已更新")
        }
    }

    fun recordQuickButtonExpense(button: QuickButtonEntity) {
        viewModelScope.launch {
            repository.addExpenseTransactional(button.amountCents, "快捷扣款：${button.label}")
            _messageFlow.emit("已扣减 ¥${MoneyUtils.formatCents(button.amountCents)}（${button.label}）")
        }
    }

    fun deleteExpense(expense: ExpenseRecordEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            _messageFlow.emit("已删除记录，返还 ¥${MoneyUtils.formatCents(expense.amountCents)}")
        }
    }

    fun updateTotalBudgetFromText(budgetText: String) {
        viewModelScope.launch {
            val cents = MoneyUtils.parseYuanToCents(budgetText, allowZero = true)
            if (cents == null) {
                _messageFlow.emit("请输入有效的总预算金额（最多两位小数）")
                return@launch
            }
            repository.updateActivePeriod { it.copy(totalBudgetCents = cents) }
            _messageFlow.emit("当前周期总预算已更新为 ¥${MoneyUtils.formatCents(cents)}")
        }
    }

    fun updateDateRange(start: LocalDate, end: LocalDate) {
        viewModelScope.launch {
            if (end.isBefore(start)) {
                _messageFlow.emit("结束日期不能早于起始日期")
                return@launch
            }
            repository.updateActivePeriod {
                it.copy(
                    startDateEpochDay = start.toEpochDay(),
                    endDateEpochDay = end.toEpochDay()
                )
            }
            _messageFlow.emit("预算起止日期已更新")
        }
    }

    fun updateCustomDailyBudgetFromText(amountText: String?) {
        viewModelScope.launch {
            if (amountText == null) {
                repository.updateActivePeriod { it.copy(customDailyBudgetCents = null) }
                _messageFlow.emit("已恢复自动计算每日可花额度")
                return@launch
            }
            val cents = MoneyUtils.parseYuanToCents(amountText, allowZero = true)
            if (cents == null) {
                _messageFlow.emit("请输入有效的每日预算金额（最多两位小数）")
                return@launch
            }
            repository.updateActivePeriod { it.copy(customDailyBudgetCents = cents) }
            _messageFlow.emit("已设定每日计划预算为 ¥${MoneyUtils.formatCents(cents)}")
        }
    }

    fun addQuickButton(label: String, amountText: String) {
        viewModelScope.launch {
            val trimmed = label.trim()
            val cents = MoneyUtils.parseYuanToCents(amountText, allowZero = false)
            if (trimmed.isEmpty() || cents == null) {
                _messageFlow.emit("请填写按钮名称和有效的正数金额（最多两位小数）")
                return@launch
            }
            repository.addQuickButton(trimmed, cents)
            _messageFlow.emit("已添加快捷按钮：$trimmed")
        }
    }

    fun updateQuickButton(button: QuickButtonEntity, newLabel: String, newAmountText: String) {
        viewModelScope.launch {
            val trimmed = newLabel.trim()
            val cents = MoneyUtils.parseYuanToCents(newAmountText, allowZero = false)
            if (trimmed.isEmpty() || cents == null) {
                _messageFlow.emit("请填写按钮名称和有效的正数金额（最多两位小数）")
                return@launch
            }
            repository.updateQuickButton(button.copy(label = trimmed, amountCents = cents))
            _messageFlow.emit("已更新快捷按钮：$trimmed")
        }
    }

    fun deleteQuickButton(button: QuickButtonEntity) {
        viewModelScope.launch {
            repository.deleteQuickButton(button)
            _messageFlow.emit("已删除快捷按钮：${button.label}")
        }
    }

    fun moveQuickButton(buttonId: Long, moveUp: Boolean) {
        viewModelScope.launch {
            repository.moveQuickButton(buttonId, moveUp)
        }
    }

    /** 开启新预算周期并归档当前周期 */
    fun startNewPeriod(budgetText: String, startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            val cents = MoneyUtils.parseYuanToCents(budgetText, allowZero = true)
            if (cents == null) {
                _messageFlow.emit("请输入有效的新周期总预算金额")
                return@launch
            }
            if (endDate.isBefore(startDate)) {
                _messageFlow.emit("结束日期不能早于起始日期")
                return@launch
            }
            repository.startNewPeriod(cents, startDate, endDate)
            _messageFlow.emit("已开启新预算周期，旧周期已归档至历史记录")
        }
    }

    /** 仅重置当前活跃周期的流水，保留历史周期与快捷按钮 */
    fun resetActivePeriodExpenses() {
        viewModelScope.launch {
            repository.resetActivePeriodExpenses()
            _messageFlow.emit("当前周期支出流水已清空，历史周期与快捷按钮均已保留")
        }
    }

    suspend fun exportBackupJson(
        reminders: List<com.yanqijian.budgettracker.notification.ReminderSlot> = emptyList()
    ): String = repository.exportBackupJson(reminders)

    fun importBackupJson(
        jsonText: String,
        onRemindersRestored: (List<com.yanqijian.budgettracker.notification.ReminderSlot>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.importBackupJson(jsonText)
            result.onSuccess { restoredReminders ->
                if (restoredReminders.isNotEmpty()) {
                    onRemindersRestored(restoredReminders)
                }
                _messageFlow.emit("备份导入成功，所有周期、流水、快捷按钮与提醒设置已恢复")
            }.onFailure { err ->
                _messageFlow.emit("导入失败（原数据未受影响）：${err.message}")
            }
        }
    }
}

class BudgetViewModelFactory(private val repository: BudgetRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            return BudgetViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
