package com.yueji.finance.domain

import com.yueji.finance.core.model.*
import java.time.LocalDate
import java.time.YearMonth

object InsightEngine {
    fun generate(dashboard: MonthlyDashboard, today: LocalDate = LocalDate.now(), lastBackupEpochMillis: Long? = null): List<Insight> {
        val month = dashboard.month
        val inCurrentMonth = YearMonth.from(today) == month
        val timeProgress = if (inCurrentMonth) today.dayOfMonth * 10_000 / month.lengthOfMonth() else 10_000
        val budgetProgress = dashboard.budgetMinor?.let { FinancialMath.progressBasisPoints(dashboard.effectiveExpenseMinor, it) }
        val items = buildList {
            if (dashboard.accounts.any { it.amountMinor < 0 && it.type != AccountType.LIABILITY })
                add(Insight(InsightLevel.CRITICAL, "账户余额需要核对", "存在负余额账户，请确认是透支、待还款还是需要余额调整。", 100))
            if (budgetProgress != null && budgetProgress >= 10_000)
                add(Insight(InsightLevel.CRITICAL, "本月预算已超支", "有效支出已达到预算的 ${budgetProgress / 100}% 。", 95))
            else if (budgetProgress != null && budgetProgress >= 9_000 && timeProgress < 8_000)
                add(Insight(InsightLevel.WARNING, "预算使用过快", "预算已使用 ${budgetProgress / 100}%，时间进度为 ${timeProgress / 100}%。", 90))
            if (!dashboard.hasTransactions)
                add(Insight(InsightLevel.INFO, "本月暂无明细", if (dashboard.hasLegacySummaryOnly) "该时期只有年度汇总，不能推断具体月份消费。" else "添加第一笔记录后即可查看趋势与预算。", 80))
            val top = dashboard.categories.firstOrNull()
            if (top != null && dashboard.effectiveExpenseMinor > 0)
                add(Insight(InsightLevel.INFO, "主要支出：${top.name}", "占本月有效支出的 ${top.amountMinor * 100 / dashboard.effectiveExpenseMinor}%。", 40))
            val assetGoal = dashboard.goals.firstOrNull { it.type == GoalType.ASSET_BALANCE && it.targetMinor > it.currentMinor }
            if (assetGoal != null) add(Insight(InsightLevel.POSITIVE, "资产目标持续推进", "距离 ${assetGoal.name} 还差 ${Money(assetGoal.targetMinor - assetGoal.currentMinor).format()}。", 20))
            if (lastBackupEpochMillis == null || System.currentTimeMillis() - lastBackupEpochMillis > 30L * 86_400_000L)
                add(Insight(InsightLevel.WARNING, "建议备份数据", "距离上次完整备份已超过 30 天或尚未备份。", 60))
        }
        return items.sortedByDescending { it.priority }.take(3)
    }
}

object ForecastEngine {
    fun forDashboard(dashboard: MonthlyDashboard, today: LocalDate = LocalDate.now()): Forecast {
        val expenseForecast = if (dashboard.hasExpenseAggregateRecords) {
            dashboard.effectiveExpenseMinor
        } else {
            val oneOff = 0L
            val variable = (dashboard.effectiveExpenseMinor - oneOff).coerceAtLeast(0)
            FinancialMath.forecastMonthEnd(dashboard.month, today, variable, oneOff, 0, dashboard.incomeMinor).expenseMinor
        }
        val incomeForecast = dashboard.incomeMinor
        val elapsed = if (YearMonth.from(today) == dashboard.month) today.dayOfMonth else dashboard.month.lengthOfMonth()
        val confidence = when {
            dashboard.hasExpenseAggregateRecords -> "汇总口径"
            elapsed < 5 -> "较低"
            elapsed < 15 -> "中等"
            else -> "较高"
        }
        val expenseBasis = if (dashboard.hasExpenseAggregateRecords) {
            "支出含周期汇总，按已确认金额计算"
        } else {
            "支出按当前日均速度推算"
        }
        val incomeBasis = if (dashboard.hasIncomeAggregateRecords) {
            "周期收入按完整收入计入"
        } else {
            "收入按已确认金额保守计入"
        }
        return Forecast(
            expenseMinor = expenseForecast,
            savingsMinor = incomeForecast - expenseForecast,
            confidence = confidence,
            basis = "$expenseBasis；$incomeBasis",
            incomeMinor = incomeForecast,
        )
    }

    fun assetGoalDates(currentMinor: Long, targetMinor: Long, lastThreeMonthsSavingsMinor: Long, asOf: LocalDate = LocalDate.now()): Map<String, LocalDate?> {
        val average = lastThreeMonthsSavingsMinor / 3
        fun date(monthly: Long): LocalDate? {
            if (monthly <= 0 || currentMinor >= targetMinor) return if (currentMinor >= targetMinor) asOf else null
            val needed = targetMinor - currentMinor
            val months = (needed + monthly - 1) / monthly
            return asOf.plusMonths(months)
        }
        return mapOf("保守" to date(average * 80 / 100), "基准" to date(average), "乐观" to date(average * 120 / 100))
    }
}
