package com.yueji.finance.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecordPeriodsTest {
    private val anchor = LocalDate.of(2026, 8, 5)

    @Test fun derivesMondayToSundayWeek() {
        val range = RecordPeriods.range(RecordGranularity.WEEK, anchor)
        assertEquals(LocalDate.of(2026, 8, 3), range.start)
        assertEquals(LocalDate.of(2026, 8, 9), range.endInclusive)
    }

    @Test fun derivesWholeMonthQuarterAndYear() {
        assertEquals(DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)), RecordPeriods.range(RecordGranularity.MONTH, anchor))
        assertEquals(DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)), RecordPeriods.range(RecordGranularity.QUARTER, anchor))
        assertEquals(DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), RecordPeriods.range(RecordGranularity.YEAR, anchor))
    }

    @Test fun formatsHumanReadableLabels() {
        assertEquals("2026年8月", RecordPeriods.label(RecordGranularity.MONTH, anchor))
        assertEquals("2026年第3季度", RecordPeriods.label(RecordGranularity.QUARTER, anchor))
        assertEquals("2026年", RecordPeriods.label(RecordGranularity.YEAR, anchor))
    }
}
