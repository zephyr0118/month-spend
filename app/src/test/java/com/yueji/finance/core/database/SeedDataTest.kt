package com.yueji.finance.core.database

import org.junit.Assert.*
import org.junit.Test

class SeedDataTest {
    @Test fun `all historical snapshot totals match specification`() {
        SeedData.validateHistoricalData()
        val totals = SeedData.snapshots(0).groupBy { it.snapshotYearMonth }.mapValues { it.value.sumOf { row -> row.amountMinor } }
        assertEquals(11_040_000L, totals[202308])
        assertEquals(15_810_000L, totals[202408])
        assertEquals(22_502_000L, totals[202508])
        assertEquals(29_645_000L, totals[202608])
    }

    @Test fun `annual summaries reconcile without fake transactions`() {
        val summaries = SeedData.annualSummaries()
        assertEquals(3, summaries.size)
        summaries.forEach { assertEquals(it.savingsMinor, it.incomeMinor - it.expenseMinor) }
        assertEquals(4_770_000L, summaries[0].savingsMinor)
        assertEquals(6_692_000L, summaries[1].savingsMinor)
        assertEquals(7_143_000L, summaries[2].savingsMinor)
    }

    @Test fun `latest accounts include allowed negative balance`() {
        val accounts = SeedData.accounts(0)
        assertEquals(10, accounts.size)
        assertEquals(29_645_000L, accounts.sumOf { it.openingBalanceMinor })
        assertTrue(accounts.single { it.id == "abc" }.allowNegativeBalance)
        assertEquals(-180_000L, accounts.single { it.id == "abc" }.openingBalanceMinor)
    }
}
