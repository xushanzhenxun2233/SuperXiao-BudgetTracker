package com.superxiao.budgettracker

import com.superxiao.budgettracker.data.BackupManager
import com.superxiao.budgettracker.data.BudgetPeriodEntity
import com.superxiao.budgettracker.data.ExpenseRecordEntity
import com.superxiao.budgettracker.data.MoneyUtils
import com.superxiao.budgettracker.data.QuickButtonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetDomainTest {

    @Test
    fun moneyParsingAndFormatting_exactCentsPrecision() {
        assertEquals(1L, MoneyUtils.parseYuanToCents("0.01"))
        assertEquals(10L, MoneyUtils.parseYuanToCents("0.10"))
        assertEquals(99_999L, MoneyUtils.parseYuanToCents("999.99"))
        assertEquals(100_000L, MoneyUtils.parseYuanToCents("1000"))
        assertEquals(0L, MoneyUtils.parseYuanToCents("0", allowZero = true))

        // Invalid inputs
        assertNull(MoneyUtils.parseYuanToCents("0", allowZero = false))
        assertNull(MoneyUtils.parseYuanToCents("-5.00", allowZero = true))
        assertNull(MoneyUtils.parseYuanToCents("12.345"))
        assertNull(MoneyUtils.parseYuanToCents("abc"))
        assertNull(MoneyUtils.parseYuanToCents(""))
        assertNull(MoneyUtils.parseYuanToCents("9999999999.99")) // overflow bound

        // Formatting
        assertEquals("0.01", MoneyUtils.formatCents(1L))
        assertEquals("1000.00", MoneyUtils.formatCents(100_000L))
        assertEquals("-12.50", MoneyUtils.formatCents(-1_250L))
        assertEquals("94.50", MoneyUtils.formatCents(9_450L))
    }

    @Test
    fun remainingDaysAndDailyAllowance_allDateBoundaries() {
        val start = LocalDate.of(2026, 11, 1)
        val end = LocalDate.of(2026, 11, 30)

        // During period (Nov 21..Nov 30 inclusive = 10 days)
        assertEquals(10, MoneyUtils.calculateRemainingDays(LocalDate.of(2026, 11, 21), start, end))
        assertEquals(10_000L, MoneyUtils.calculateAutoDailyCents(100_000L, 10)) // ¥1000 / 10 = ¥100.00
        assertEquals(9_450L, MoneyUtils.calculateAutoDailyCents(94_500L, 10)) // ¥945 / 10 = ¥94.50

        // Before start -> full inclusive period length (30 days)
        assertEquals(30, MoneyUtils.calculateRemainingDays(LocalDate.of(2026, 10, 25), start, end))

        // On end date -> 1 day
        assertEquals(1, MoneyUtils.calculateRemainingDays(LocalDate.of(2026, 11, 30), start, end))

        // After end date -> 0 days, no divide by zero
        assertEquals(0, MoneyUtils.calculateRemainingDays(LocalDate.of(2026, 12, 1), start, end))
        assertEquals(0L, MoneyUtils.calculateAutoDailyCents(50_000L, 0))

        // Leap year & cross-year ranges
        assertEquals(
            3,
            MoneyUtils.calculateRemainingDays(
                LocalDate.of(2024, 2, 28),
                LocalDate.of(2024, 2, 28),
                LocalDate.of(2024, 3, 1)
            )
        )
        assertEquals(
            9,
            MoneyUtils.calculateRemainingDays(
                LocalDate.of(2026, 12, 28),
                LocalDate.of(2026, 12, 28),
                LocalDate.of(2027, 1, 5)
            )
        )
    }

    @Test
    fun backupJson_roundTripAndValidation() {
        val periods = listOf(
            BudgetPeriodEntity(
                id = 1L,
                totalBudgetCents = 200_000L,
                startDateEpochDay = LocalDate.of(2026, 10, 1).toEpochDay(),
                endDateEpochDay = LocalDate.of(2026, 10, 31).toEpochDay(),
                customDailyBudgetCents = null,
                isActive = false,
                createdAtMillis = 1_700_000_000_000L
            ),
            BudgetPeriodEntity(
                id = 2L,
                totalBudgetCents = 300_000L,
                startDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
                endDateEpochDay = LocalDate.of(2026, 11, 30).toEpochDay(),
                customDailyBudgetCents = 3_000L,
                isActive = true,
                createdAtMillis = 1_702_000_000_000L
            )
        )
        val expenses = listOf(
            ExpenseRecordEntity(id = 10L, periodId = 1L, amountCents = 2_050L, note = "早餐", timestampMillis = 1_700_001_000_000L),
            ExpenseRecordEntity(id = 11L, periodId = 2L, amountCents = 950L, note = "快捷扣款：咖啡", timestampMillis = 1_702_001_000_000L)
        )
        val buttons = listOf(
            QuickButtonEntity(id = 1L, label = "咖啡", amountCents = 1_500L, sortOrder = 0),
            QuickButtonEntity(id = 2L, label = "午餐", amountCents = 2_500L, sortOrder = 1)
        )

        val json = BackupManager.toJson(periods, expenses, buttons, exportedAtIso = "2026-11-21T10:00:00Z")
        val parsed = BackupManager.parseAndValidate(json)
        assertTrue(parsed.isSuccess)
        val payload = parsed.getOrNull()
        assertNotNull(payload)
        assertEquals(2, payload!!.periods.size)
        assertEquals(2, payload.expenses.size)
        assertEquals(2, payload.quickButtons.size)
        assertEquals(3_000L, payload.periods[1].customDailyBudgetCents)
        assertEquals("快捷扣款：咖啡", payload.expenses[1].note)

        // Reject unsupported schema version
        val badVersion = BackupManager.parseAndValidate("""{"schemaVersion":99,"periods":[],"expenses":[],"quickButtons":[]}""")
        assertTrue(badVersion.isFailure)

        // Reject broken period foreign key
        val badFk = json.replace("\"periodId\": 2", "\"periodId\": 999")
        assertTrue(BackupManager.parseAndValidate(badFk).isFailure)

        // Reject malformed JSON
        assertTrue(BackupManager.parseAndValidate("{not valid json").isFailure)
    }
}
