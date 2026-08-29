package com.yueji.finance.core.model

import com.yueji.finance.domain.ForecastEngine
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinancialMathTest {
    @Test fun `money uses exact minor units`() {
        assertEquals(1L, Money.parse("0.01").minor)
        assertEquals(12_345L, Money.parse("123.45").minor)
        assertEquals(-180_000L, Money.parse("-1,800").minor)
        assertEquals(Money(30), Money(10) + Money(20))
    }

    @Test fun `savings and savings rate are exact`() {
        assertEquals(6_000, FinancialMath.savingsRateBasisPoints(1_000_00, 400_00))
        assertNull(FinancialMath.savingsRateBasisPoints(0, 100))
        assertNull(FinancialMath.savingsRateBasisPoints(-100, 100))
        assertEquals(-2_000, FinancialMath.savingsRateBasisPoints(1_000, 1_200))
    }

    @Test fun `financial year September through August crosses calendar year`() {
        val period = Periods.financialYear(LocalDate.of(2026, 8, 4), 9)
        assertEquals(LocalDate.of(2025, 9, 1), period.range.start)
        assertEquals(LocalDate.of(2026, 8, 31), period.range.endInclusive)
        assertEquals("FY2026", period.label)
    }

    @Test fun `natural year is January through December`() {
        val period = Periods.naturalYear(2026)
        assertEquals(LocalDate.of(2026, 1, 1), period.range.start)
        assertEquals(LocalDate.of(2026, 12, 31), period.range.endInclusive)
    }

    @Test fun `month range handles leap year and month end`() {
        val leap = Periods.month(YearMonth.of(2024, 2))
        assertEquals(LocalDate.of(2024, 2, 29), leap.endInclusive)
        assertEquals(LocalDate.of(2025, 2, 28), Periods.month(YearMonth.of(2025, 2)).endInclusive)
    }

    @Test fun `forecast does not extrapolate one off expense`() {
        val forecast = FinancialMath.forecastMonthEnd(YearMonth.of(2026, 8), LocalDate.of(2026, 8, 10), 1_000_00, 800_00, 200_00, 5_000_00)
        assertEquals(4_100_00L, forecast.expenseMinor) // 1000 / 10 * 31 + 800 one-off + 200 fixed
        assertEquals(900_00L, forecast.savingsMinor)
        assertEquals("中等", forecast.confidence)
    }

    @Test fun `aggregate income does not stop daily expense forecast`() {
        val dashboard = MonthlyDashboard(
            month = YearMonth.of(2026, 8),
            expenseMinor = 100_000,
            incomeMinor = 500_000,
            hasAggregateRecords = true,
            hasExpenseAggregateRecords = false,
            hasIncomeAggregateRecords = true,
        )
        val forecast = ForecastEngine.forDashboard(dashboard, LocalDate.of(2026, 8, 10))
        assertEquals(310_000L, forecast.expenseMinor)
        assertEquals(500_000L, forecast.incomeMinor)
        assertEquals(190_000L, forecast.savingsMinor)
        assertTrue(forecast.basis.contains("支出按当前日均速度推算"))
    }

    @Test fun `expense aggregate uses confirmed total independently of income`() {
        val dashboard = MonthlyDashboard(
            month = YearMonth.of(2026, 8),
            expenseMinor = 300_000,
            incomeMinor = 800_000,
            hasExpenseAggregateRecords = true,
        )
        val forecast = ForecastEngine.forDashboard(dashboard, LocalDate.of(2026, 8, 3))
        assertEquals(300_000L, forecast.expenseMinor)
        assertEquals(800_000L, forecast.incomeMinor)
        assertEquals(500_000L, forecast.savingsMinor)
    }

    @Test fun `dynamic budget carries last month saving or overspend while fixed stays unchanged`() {
        assertEquals(BudgetCalculation(500_000, 0), BudgetMath.calculate(500_000, 350_000, RolloverMode.NONE))
        assertEquals(BudgetCalculation(650_000, 150_000), BudgetMath.calculate(500_000, 350_000, RolloverMode.NET))
        assertEquals(BudgetCalculation(400_000, -100_000), BudgetMath.calculate(500_000, 600_000, RolloverMode.NET))
        assertEquals(BudgetCalculation(500_000, 0), BudgetMath.calculate(500_000, null, RolloverMode.NET))
    }

    @Test fun `asset goal dates return no date for nonpositive savings`() {
        assertTrue(ForecastEngine.assetGoalDates(100_000, 200_000, 0).values.all { it == null })
        assertTrue(ForecastEngine.assetGoalDates(100_000, 200_000, -30_000).values.all { it == null })
    }

    @Test fun `emergency fund requires real expense denominator`() {
        assertNull(FinancialMath.emergencyMonthsBasisPoints(1_000_000, 0))
        assertEquals(30_000, FinancialMath.emergencyMonthsBasisPoints(1_000_000, 1_000_000))
    }
}
