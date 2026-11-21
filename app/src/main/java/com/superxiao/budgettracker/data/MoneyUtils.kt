package com.superxiao.budgettracker.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object MoneyUtils {
    /** 最大支持单笔/总预算：999,999,999.99 元（防 Long 溢出） */
    const val MAX_CENTS: Long = 99_999_999_999L

    /**
     * 严格解析元金额字符串为整数分（Long）。
     * - 最多允许 2 位小数
     * - allowZero = false 时要求 > 0
     */
    fun parseYuanToCents(input: String, allowZero: Boolean = false): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val bd = BigDecimal(trimmed)
            if (bd.scale() > 2) return null
            val centsBd = bd.movePointRight(2)
            val cents = centsBd.longValueExact()
            if (cents > MAX_CENTS) return null
            if (allowZero) {
                if (cents < 0L) null else cents
            } else {
                if (cents <= 0L) null else cents
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 格式化整数分为精确两位小数字符串，例如 12345 -> "123.45"，-50 -> "-0.50" */
    fun formatCents(cents: Long): String {
        val sign = if (cents < 0L) "-" else ""
        val absVal = abs(cents)
        val yuan = absVal / 100L
        val fen = absVal % 100L
        return "$sign$yuan.${fen.toString().padStart(2, '0')}"
    }

    /** 供输入框回显使用，例如 10000 -> "100"，10050 -> "100.50" */
    fun formatCentsForInput(cents: Long): String {
        val sign = if (cents < 0L) "-" else ""
        val absVal = abs(cents)
        val yuan = absVal / 100L
        val fen = absVal % 100L
        return if (fen == 0L) "$sign$yuan" else "$sign$yuan.${fen.toString().padStart(2, '0')}"
    }

    /**
     * 计算剩余天数（包含起止日期当日）：
     * - today < start -> [start, end] 全部天数
     * - start <= today <= end -> [today, end] 天数
     * - today > end -> 0
     */
    fun calculateRemainingDays(today: LocalDate, start: LocalDate, end: LocalDate): Int {
        if (end.isBefore(start)) return 0
        return when {
            today.isAfter(end) -> 0
            today.isBefore(start) -> (ChronoUnit.DAYS.between(start, end) + 1L).toInt().coerceAtLeast(0)
            else -> (ChronoUnit.DAYS.between(today, end) + 1L).toInt().coerceAtLeast(0)
        }
    }

    /** 自动日均估算（向零截断，剩余分留在总余额中不丢失） */
    fun calculateAutoDailyCents(remainingCents: Long, remainingDays: Int): Long {
        if (remainingDays <= 0) return 0L
        return remainingCents / remainingDays
    }

    /** 进度条比例（0f..1f） */
    fun calculateSpendRatio(spentCents: Long, totalBudgetCents: Long): Float {
        if (totalBudgetCents <= 0L) return 0f
        return BigDecimal(spentCents)
            .divide(BigDecimal(totalBudgetCents), 4, RoundingMode.HALF_UP)
            .toFloat()
            .coerceIn(0f, 1f)
    }
}
