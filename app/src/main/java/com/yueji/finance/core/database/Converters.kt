package com.yueji.finance.core.database

import androidx.room.TypeConverter
import com.yueji.finance.core.model.*

class Converters {
    @TypeConverter fun accountType(value: AccountType) = value.name
    @TypeConverter fun accountType(value: String) = AccountType.valueOf(value)
    @TypeConverter fun transactionType(value: TransactionType) = value.name
    @TypeConverter fun transactionType(value: String) = TransactionType.valueOf(value)
    @TypeConverter fun transactionStatus(value: TransactionStatus) = value.name
    @TypeConverter fun transactionStatus(value: String) = TransactionStatus.valueOf(value)
    @TypeConverter fun transactionSource(value: TransactionSource) = value.name
    @TypeConverter fun transactionSource(value: String) = TransactionSource.valueOf(value)
    @TypeConverter fun recordGranularity(value: RecordGranularity) = value.name
    @TypeConverter fun recordGranularity(value: String) = RecordGranularity.valueOf(value)
    @TypeConverter fun transactionDirection(value: TransactionDirection) = value.name
    @TypeConverter fun transactionDirection(value: String) = TransactionDirection.valueOf(value)
    @TypeConverter fun necessity(value: Necessity?) = value?.name
    @TypeConverter fun necessity(value: String?) = value?.let(Necessity::valueOf)
    @TypeConverter fun variability(value: Variability?) = value?.name
    @TypeConverter fun variability(value: String?) = value?.let(Variability::valueOf)
    @TypeConverter fun goalType(value: GoalType) = value.name
    @TypeConverter fun goalType(value: String) = GoalType.valueOf(value)
    @TypeConverter fun periodType(value: PeriodType) = value.name
    @TypeConverter fun periodType(value: String) = PeriodType.valueOf(value)
    @TypeConverter fun rolloverMode(value: RolloverMode) = value.name
    @TypeConverter fun rolloverMode(value: String) = RolloverMode.valueOf(value)
}
