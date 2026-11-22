package com.yanqijian.budgettracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    // --- Budget Periods ---
    @Query("SELECT * FROM budget_periods WHERE isActive = 1 ORDER BY id DESC LIMIT 1")
    fun observeActivePeriod(): Flow<BudgetPeriodEntity?>

    @Query("SELECT * FROM budget_periods WHERE isActive = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActivePeriodOnce(): BudgetPeriodEntity?

    @Query("SELECT * FROM budget_periods ORDER BY isActive DESC, startDateEpochDay DESC, id DESC")
    fun observeAllPeriods(): Flow<List<BudgetPeriodEntity>>

    @Query("SELECT * FROM budget_periods ORDER BY id ASC")
    suspend fun getAllPeriodsOnce(): List<BudgetPeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriod(period: BudgetPeriodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriods(periods: List<BudgetPeriodEntity>)

    @Update
    suspend fun updatePeriod(period: BudgetPeriodEntity)

    @Query("UPDATE budget_periods SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllPeriods()

    @Query("DELETE FROM budget_periods")
    suspend fun deleteAllPeriods()

    // --- Quick Buttons ---
    @Query("SELECT * FROM quick_buttons ORDER BY sortOrder ASC, id ASC")
    fun observeQuickButtons(): Flow<List<QuickButtonEntity>>

    @Query("SELECT * FROM quick_buttons ORDER BY sortOrder ASC, id ASC")
    suspend fun getQuickButtonsOnce(): List<QuickButtonEntity>

    @Query("SELECT COUNT(*) FROM quick_buttons")
    suspend fun getQuickButtonCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickButton(button: QuickButtonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickButtons(buttons: List<QuickButtonEntity>)

    @Update
    suspend fun updateQuickButton(button: QuickButtonEntity)

    @Update
    suspend fun updateQuickButtons(buttons: List<QuickButtonEntity>)

    @Delete
    suspend fun deleteQuickButton(button: QuickButtonEntity)

    @Query("DELETE FROM quick_buttons")
    suspend fun deleteAllQuickButtons()

    // --- Expense Records ---
    @Query("SELECT * FROM expense_records ORDER BY timestampMillis DESC, id DESC")
    fun observeAllExpenses(): Flow<List<ExpenseRecordEntity>>

    @Query("SELECT * FROM expense_records WHERE periodId = :periodId ORDER BY timestampMillis DESC, id DESC")
    fun observeExpensesForPeriod(periodId: Long): Flow<List<ExpenseRecordEntity>>

    @Query("SELECT * FROM expense_records WHERE periodId = :periodId ORDER BY timestampMillis DESC, id DESC")
    suspend fun getExpensesForPeriodOnce(periodId: Long): List<ExpenseRecordEntity>

    @Query("SELECT * FROM expense_records ORDER BY id ASC")
    suspend fun getAllExpensesOnce(): List<ExpenseRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseRecordEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseRecordEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseRecordEntity)

    @Query("DELETE FROM expense_records WHERE periodId = :periodId")
    suspend fun clearExpensesForPeriod(periodId: Long)

    @Query("DELETE FROM expense_records")
    suspend fun deleteAllExpenses()
}
