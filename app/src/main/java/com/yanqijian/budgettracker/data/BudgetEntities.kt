package com.yanqijian.budgettracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "budget_periods")
data class BudgetPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val totalBudgetCents: Long,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val customDailyBudgetCents: Long? = null,
    val isActive: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "quick_buttons")
data class QuickButtonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val amountCents: Long,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "expense_records",
    foreignKeys = [
        ForeignKey(
            entity = BudgetPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["periodId", "timestampMillis"])
    ]
)
data class ExpenseRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodId: Long,
    val amountCents: Long,
    val note: String,
    val timestampMillis: Long
)
