package com.yueji.finance.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class AccountType { CASH, BANK, PAYMENT_PLATFORM, SAVINGS, INVESTMENT, CREDIT, LIABILITY, OTHER }
enum class TransactionType { EXPENSE, INCOME, TRANSFER, REFUND, BALANCE_ADJUSTMENT }
enum class TransactionStatus { CONFIRMED, PENDING, DRAFT, DELETED }
enum class TransactionSource { MANUAL, CSV, LEGACY, RECURRING }
enum class RecordGranularity { DAY, WEEK, MONTH, QUARTER, YEAR }
enum class TransactionDirection { EXPENSE, INCOME }
enum class Necessity { NECESSARY, OPTIONAL }
enum class Variability { FIXED, VARIABLE }
enum class GoalType { MONTHLY_EXPENSE, ANNUAL_EXPENSE, CATEGORY_BUDGET, MONTHLY_INCOME, ANNUAL_INCOME, MONTHLY_SAVINGS, ANNUAL_SAVINGS, SAVINGS_RATE, ASSET_BALANCE, EMERGENCY_FUND, CUSTOM }
enum class PeriodType { MONTH, NATURAL_YEAR, FISCAL_YEAR, CUSTOM }
enum class RolloverMode { NONE, REMAINING, OVERSPEND, NET }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class InsightLevel { CRITICAL, WARNING, INFO, POSITIVE }

@JvmInline
value class Money(val minor: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(Math.addExact(minor, other.minor))
    operator fun minus(other: Money) = Money(Math.subtractExact(minor, other.minor))
    override fun compareTo(other: Money) = minor.compareTo(other.minor)
    fun format(hidden: Boolean = false): String {
        if (hidden) return "••••"
        val amount = BigDecimal.valueOf(minor, 2).setScale(2, RoundingMode.UNNECESSARY)
        return "¥${amount.toPlainString()}"
    }

    companion object {
        val ZERO = Money(0)
        fun parse(text: String): Money = Money(
            BigDecimal(text.trim().replace(",", ""))
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        )
    }
}

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init { require(!endInclusive.isBefore(start)) }
    val startEpochDay: Long get() = start.toEpochDay()
    val endEpochDay: Long get() = endInclusive.toEpochDay()
}

data class BudgetCalculation(val effectiveMinor: Long, val adjustmentMinor: Long)

object BudgetMath {
    fun calculate(baseMinor: Long, previousExpenseMinor: Long?, rolloverMode: RolloverMode): BudgetCalculation {
        require(baseMinor > 0)
        if (previousExpenseMinor == null || rolloverMode == RolloverMode.NONE) return BudgetCalculation(baseMinor, 0)
        val difference = baseMinor - previousExpenseMinor.coerceAtLeast(0)
        val adjustment = when (rolloverMode) {
            RolloverMode.NONE -> 0L
            RolloverMode.REMAINING -> difference.coerceAtLeast(0)
            RolloverMode.OVERSPEND -> difference.coerceAtMost(0)
            RolloverMode.NET -> difference
        }
        return BudgetCalculation((baseMinor + adjustment).coerceAtLeast(0), adjustment)
    }
}

data class FinancialPeriod(val label: String, val range: DateRange)

object Periods {
    fun month(month: YearMonth): DateRange = DateRange(month.atDay(1), month.atEndOfMonth())

    fun financialYear(containing: LocalDate, startMonth: Int): FinancialPeriod {
        require(startMonth in 1..12)
        val startYear = if (containing.monthValue >= startMonth) containing.year else containing.year - 1
        val start = LocalDate.of(startYear, startMonth, 1)
        val end = start.plusYears(1).minusDays(1)
        return FinancialPeriod("FY${end.year}", DateRange(start, end))
    }

    fun naturalYear(year: Int) = FinancialPeriod(year.toString(), DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
    fun label(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("yyyy 年 M 月"))
}

object RecordPeriods {
    fun range(granularity: RecordGranularity, anchor: LocalDate): DateRange = when (granularity) {
        RecordGranularity.DAY -> DateRange(anchor, anchor)
        RecordGranularity.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { DateRange(it, it.plusDays(6)) }
        RecordGranularity.MONTH -> Periods.month(YearMonth.from(anchor))
        RecordGranularity.QUARTER -> {
            val startMonth = ((anchor.monthValue - 1) / 3) * 3 + 1
            val start = LocalDate.of(anchor.year, startMonth, 1)
            DateRange(start, start.plusMonths(3).minusDays(1))
        }
        RecordGranularity.YEAR -> DateRange(LocalDate.of(anchor.year, 1, 1), LocalDate.of(anchor.year, 12, 31))
    }

    fun label(granularity: RecordGranularity, anchor: LocalDate): String = when (granularity) {
        RecordGranularity.DAY -> anchor.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        RecordGranularity.WEEK -> range(granularity, anchor).let { "${it.start.format(DateTimeFormatter.ofPattern("M月d日"))}—${it.endInclusive.format(DateTimeFormatter.ofPattern("M月d日"))}（周）" }
        RecordGranularity.MONTH -> anchor.format(DateTimeFormatter.ofPattern("yyyy年M月"))
        RecordGranularity.QUARTER -> "${anchor.year}年第${(anchor.monthValue - 1) / 3 + 1}季度"
        RecordGranularity.YEAR -> "${anchor.year}年"
    }

    fun granularityLabel(granularity: RecordGranularity) = when (granularity) {
        RecordGranularity.DAY -> "按天"; RecordGranularity.WEEK -> "按周"; RecordGranularity.MONTH -> "按月"
        RecordGranularity.QUARTER -> "按季"; RecordGranularity.YEAR -> "按年"
    }
}

data class CategoryTotal(val id: String?, val name: String, val amountMinor: Long)
data class AccountBalance(val id: String, val name: String, val amountMinor: Long, val type: AccountType, val includeInAssets: Boolean)
data class DailyTotal(val epochDay: Long, val amountMinor: Long)

data class MonthlyDashboard(
    val month: YearMonth,
    val expenseMinor: Long = 0,
    val incomeMinor: Long = 0,
    val refundMinor: Long = 0,
    val budgetMinor: Long? = null,
    val categories: List<CategoryTotal> = emptyList(),
    val daily: List<DailyTotal> = emptyList(),
    val accounts: List<AccountBalance> = emptyList(),
    val goals: List<GoalProgress> = emptyList(),
    val hasTransactions: Boolean = false,
    val hasLegacySummaryOnly: Boolean = false,
    val hasAggregateRecords: Boolean = false,
    val hasExpenseAggregateRecords: Boolean = false,
    val hasIncomeAggregateRecords: Boolean = false,
    val baseBudgetMinor: Long? = null,
    val budgetAdjustmentMinor: Long = 0,
    val budgetRolloverMode: RolloverMode = RolloverMode.NONE,
    val previousMonthExpenseMinor: Long? = null,
) {
    val effectiveExpenseMinor get() = (expenseMinor - refundMinor).coerceAtLeast(0)
    val savingsMinor get() = incomeMinor - effectiveExpenseMinor
    val savingsRate get() = FinancialMath.savingsRateBasisPoints(incomeMinor, effectiveExpenseMinor)
    val budgetRemainingMinor get() = budgetMinor?.minus(effectiveExpenseMinor)
    val totalAssetsMinor get() = accounts.filter { it.includeInAssets }.sumOf { it.amountMinor }
}

data class GoalProgress(val id: String, val name: String, val type: GoalType, val currentMinor: Long, val targetMinor: Long, val targetRatioBasisPoints: Int? = null)
data class Forecast(
    val expenseMinor: Long,
    val savingsMinor: Long,
    val confidence: String,
    val basis: String,
    val incomeMinor: Long = 0,
)
data class Insight(val level: InsightLevel, val title: String, val message: String, val priority: Int)

object FinancialMath {
    fun savingsRateBasisPoints(incomeMinor: Long, expenseMinor: Long): Int? {
        if (incomeMinor <= 0L) return null
        return (((incomeMinor - expenseMinor) * 10_000L) / incomeMinor).toInt()
    }

    fun progressBasisPoints(currentMinor: Long, targetMinor: Long): Int? =
        if (targetMinor <= 0L) null else ((currentMinor * 10_000L) / targetMinor).toInt()

    fun emergencyMonthsBasisPoints(availableMinor: Long, threeMonthNecessaryExpenseMinor: Long): Int? =
        if (threeMonthNecessaryExpenseMinor <= 0L) null
        else ((availableMinor * 30_000L) / threeMonthNecessaryExpenseMinor).toInt()

    fun forecastMonthEnd(
        month: YearMonth,
        asOf: LocalDate,
        variableExpenseMinor: Long,
        oneOffExpenseMinor: Long,
        remainingFixedMinor: Long,
        incomeMinor: Long,
    ): Forecast {
        val elapsed = if (asOf.year == month.year && asOf.month == month.month) asOf.dayOfMonth else month.lengthOfMonth()
        val projectedVariable = if (elapsed <= 0) 0 else variableExpenseMinor * month.lengthOfMonth() / elapsed
        val expense = projectedVariable + oneOffExpenseMinor + remainingFixedMinor
        val confidence = when {
            elapsed < 5 -> "较低"
            elapsed < 15 -> "中等"
            else -> "较高"
        }
        return Forecast(
            expenseMinor = expense,
            savingsMinor = incomeMinor - expense,
            confidence = confidence,
            basis = "按已发生的可变支出速度推算，一次性支出不重复外推；收入按已确认金额计入",
            incomeMinor = incomeMinor,
        )
    }
}
