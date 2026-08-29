package com.yueji.finance.ui

import com.yueji.finance.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionPresentationTest {
    @Test fun `merchant is primary and category is fallback`() {
        assertEquals("盒马鲜生", transactionPrimaryLabel("盒马鲜生", "餐饮", TransactionType.EXPENSE))
        assertEquals("餐饮", transactionPrimaryLabel(null, "餐饮", TransactionType.EXPENSE))
        assertEquals("收入", transactionPrimaryLabel("  ", null, TransactionType.INCOME))
    }
}
