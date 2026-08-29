package com.yueji.finance.domain

import com.yueji.finance.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InsightEngineTest {
    @Test fun `data error outranks budget warning`() {
        val dashboard = MonthlyDashboard(
            month = YearMonth.of(2026, 8), expenseMinor = 600_000, incomeMinor = 1_000_000, budgetMinor = 500_000,
            accounts = listOf(AccountBalance("a", "异常账户", -10_000, AccountType.BANK, true)), hasTransactions = true,
        )
        val insights = InsightEngine.generate(dashboard, LocalDate.of(2026, 8, 4), System.currentTimeMillis())
        assertEquals("账户余额需要核对", insights.first().title)
        assertTrue(insights.zipWithNext().all { it.first.priority >= it.second.priority })
    }

    @Test fun `legacy month explicitly reports no detail`() {
        val dashboard = MonthlyDashboard(YearMonth.of(2025, 1), hasLegacySummaryOnly = true)
        val insights = InsightEngine.generate(dashboard, LocalDate.of(2025, 1, 10), System.currentTimeMillis())
        assertTrue(insights.any { "年度汇总" in it.message })
    }
}
