package com.yueji.finance.core.database

import com.yueji.finance.core.model.*

object SeedData {
    private const val AUG_2023 = 202308
    private const val AUG_2024 = 202408
    private const val AUG_2025 = 202508
    private const val AUG_2026 = 202608

    val accountIds = listOf("cmb", "rcb", "abc", "boc", "ccb", "webank", "wechat_a", "wechat_b", "alipay_a", "alipay_b")

    fun accounts(now: Long): List<AccountEntity> {
        val rows = listOf(
            Triple("cmb", "招商银行", 10_390_000L), Triple("rcb", "农商银行", 40_000L),
            Triple("abc", "农业银行", -180_000L), Triple("boc", "中国银行", 20_000L),
            Triple("ccb", "建设银行", 8_000_000L), Triple("webank", "微众银行", 10_130_000L),
            Triple("wechat_a", "微信子账户 A", 200_000L), Triple("wechat_b", "微信子账户 B", 35_000L),
            Triple("alipay_a", "支付宝子账户 A", 920_000L), Triple("alipay_b", "支付宝子账户 B", 90_000L),
        )
        return rows.mapIndexed { index, (id, name, balance) ->
            AccountEntity(
                id = id, name = name,
                institutionName = when {
                    id.startsWith("wechat") -> "微信"
                    id.startsWith("alipay") -> "支付宝"
                    else -> name
                },
                accountType = if (id.startsWith("wechat") || id.startsWith("alipay")) AccountType.PAYMENT_PLATFORM else AccountType.BANK,
                openingBalanceMinor = balance,
                allowNegativeBalance = id == "abc",
                iconKey = if (id.startsWith("wechat") || id.startsWith("alipay")) "payments" else "account_balance",
                sortOrder = index, createdAtEpochMillis = now, updatedAtEpochMillis = now,
            )
        }
    }

    fun snapshots(now: Long): List<BalanceSnapshotEntity> {
        val data = mapOf(
            AUG_2023 to listOf(4_000_000L, 330_000L, 1_040_000L, 80_000L, 40_000L, 0L, 350_000L, 4_010_000L, 1_010_000L, 180_000L),
            AUG_2024 to listOf(5_150_000L, 740_000L, 10_000L, 30_000L, 6_400_000L, 0L, 180_000L, 2_100_000L, 980_000L, 220_000L),
            AUG_2025 to listOf(10_270_000L, 0L, -160_000L, 90_000L, 4_220_000L, 7_040_000L, 17_000L, 35_000L, 920_000L, 70_000L),
            AUG_2026 to listOf(10_390_000L, 40_000L, -180_000L, 20_000L, 8_000_000L, 10_130_000L, 200_000L, 35_000L, 920_000L, 90_000L),
        )
        return data.flatMap { (month, balances) -> balances.mapIndexed { index, amount ->
            BalanceSnapshotEntity("snapshot_${month}_${accountIds[index]}", accountIds[index], month, amount, createdAtEpochMillis = now)
        } }
    }

    fun annualSummaries() = listOf(
        LegacyAnnualSummaryEntity("fy2024", "FY2024", 202308, 202407, 7_940_000L, 3_170_000L, 4_770_000L, note = "2023-08-01 至 2024-07-31"),
        LegacyAnnualSummaryEntity("fy2025", "FY2025", 202408, 202507, 10_440_000L, 3_748_000L, 6_692_000L, note = "2024-08-01 至 2025-07-31"),
        LegacyAnnualSummaryEntity("fy2026", "FY2026", 202508, 202607, 12_440_000L, 5_297_000L, 7_143_000L, note = "2025-08-01 至 2026-07-31"),
    )

    fun categories(): List<CategoryEntity> {
        val expenses = listOf("餐饮", "交通", "住房", "水电燃气", "通讯网络", "日用购物", "服饰美妆", "数码家电", "医疗健康", "宠物", "旅行", "娱乐", "学习", "人情往来", "家庭支出", "工作经营", "保险", "税费", "捐赠", "其他")
        val incomes = listOf("工资", "奖金", "兼职", "经营收入", "报销", "利息", "投资收益", "退款", "礼金", "其他")
        return expenses.mapIndexed { i, name ->
            CategoryEntity("expense_$i", name = name, transactionDirection = TransactionDirection.EXPENSE,
                iconKey = expenseIcon(name), defaultNecessity = if (name in setOf("旅行", "娱乐", "服饰美妆")) Necessity.OPTIONAL else Necessity.NECESSARY,
                defaultVariability = if (name in setOf("住房", "通讯网络", "保险")) Variability.FIXED else Variability.VARIABLE, sortOrder = i)
        } + incomes.mapIndexed { i, name ->
            CategoryEntity("income_$i", name = name, transactionDirection = TransactionDirection.INCOME,
                iconKey = "payments", defaultNecessity = Necessity.NECESSARY, defaultVariability = if (name == "工资") Variability.FIXED else Variability.VARIABLE, sortOrder = i)
        }
    }

    private fun expenseIcon(name: String) = when (name) {
        "餐饮" -> "restaurant"; "交通" -> "directions_car"; "住房" -> "home"; "旅行" -> "flight"
        "医疗健康" -> "medical_services"; "学习" -> "school"; "娱乐" -> "movie"; else -> "category"
    }

    fun validateHistoricalData() {
        val expected = mapOf(AUG_2023 to 11_040_000L, AUG_2024 to 15_810_000L, AUG_2025 to 22_502_000L, AUG_2026 to 29_645_000L)
        snapshots(0).groupBy { it.snapshotYearMonth }.forEach { (month, rows) ->
            check(rows.sumOf { it.amountMinor } == expected.getValue(month)) { "历史快照 $month 合计不一致" }
        }
        annualSummaries().forEach { check(it.incomeMinor - it.expenseMinor == it.savingsMinor) { "${it.label} 收支校验失败" } }
    }
}
