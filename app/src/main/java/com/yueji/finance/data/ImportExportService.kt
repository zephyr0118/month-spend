package com.yueji.finance.data

import androidx.room.withTransaction
import com.yueji.finance.core.database.*
import com.yueji.finance.core.model.*
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CsvError(val line: Int, val reason: String)
data class CsvImportResult(val added: Int, val skipped: Int, val errors: List<CsvError>, val duplicateBatch: Boolean = false)

@Singleton
class ImportExportService @Inject constructor(
    private val db: YueJiDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val importDao: ImportDao,
) {
    private val headers = listOf("id", "type", "amount", "currency", "date", "time", "account", "destination_account", "category", "subcategory", "merchant", "tags", "note", "necessity", "variability", "is_one_off", "is_reimbursable", "exclude_from_budget", "record_granularity", "period_start", "period_end")

    suspend fun importCsv(input: InputStream, fileName: String): CsvImportResult {
        val bytes = input.readBytes(); val hash = sha256(bytes)
        if (importDao.byHash(hash) != null) return CsvImportResult(0, 0, emptyList(), duplicateBatch = true)
        val text = decode(bytes); val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxBy { firstLine.count { char -> char == it } }
        val rows = parseCsv(text, delimiter)
        if (rows.isEmpty()) return CsvImportResult(0, 0, listOf(CsvError(1, "文件为空")))
        val map = rows.first().mapIndexed { index, value -> value.trim().lowercase() to index }.toMap()
        val required = listOf("type", "amount", "date", "account")
        val missing = required.filterNot(map::containsKey)
        if (missing.isNotEmpty()) return CsvImportResult(0, 0, listOf(CsvError(1, "缺少字段：${missing.joinToString()}")))
        val entities = mutableListOf<TransactionEntity>(); val entityTags = mutableMapOf<String, List<String>>(); val errors = mutableListOf<CsvError>(); var skipped = 0
        for ((index, row) in rows.drop(1).withIndex()) {
            val line = index + 2
            fun field(name: String) = map[name]?.let { row.getOrNull(it) }.orEmpty().trim()
            try {
                if (row.all { it.isBlank() }) continue
                val type = TransactionType.valueOf(field("type").uppercase())
                val amount = Money.parse(field("amount")).minor.also { require(it > 0) { "金额必须大于 0" } }
                val date = LocalDate.parse(field("date")); val time = field("time").takeIf(String::isNotBlank)?.let(LocalTime::parse) ?: LocalTime.NOON
                val account = accountDao.byName(field("account")) ?: error("账户不存在：${field("account")}")
                val destination = field("destination_account").takeIf(String::isNotBlank)?.let { accountDao.byName(it)?.id ?: error("转入账户不存在：$it") }
                if (type == TransactionType.TRANSFER) require(destination != null && destination != account.id) { "转账账户无效" }
                val direction = if (type == TransactionType.INCOME) TransactionDirection.INCOME else TransactionDirection.EXPENSE
                val category = field("category").takeIf(String::isNotBlank)?.let { categoryDao.byName(it, direction)?.id ?: error("分类不存在：$it") }
                if (type in setOf(TransactionType.EXPENSE, TransactionType.INCOME)) require(category != null) { "收入/支出必须有分类" }
                val id = field("id").ifBlank { UUID.randomUUID().toString() }
                val granularity = field("record_granularity").takeIf(String::isNotBlank)?.let { RecordGranularity.valueOf(it.uppercase()) } ?: RecordGranularity.DAY
                if (type !in setOf(TransactionType.EXPENSE, TransactionType.INCOME)) require(granularity == RecordGranularity.DAY) { "只有支出和收入支持周期汇总" }
                val derivedPeriod = RecordPeriods.range(granularity, date)
                val periodStart = field("period_start").takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: derivedPeriod.start
                val periodEnd = field("period_end").takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: derivedPeriod.endInclusive
                require(!periodEnd.isBefore(periodStart)) { "汇总周期结束日期不能早于开始日期" }
                val canonical = listOf(date, time, amount, type, account.id, field("merchant"), field("note"), granularity, periodStart, periodEnd).joinToString("|")
                val fingerprint = sha256(canonical.toByteArray())
                if (transactionDao.fingerprintExists(fingerprint) || entities.any { it.fingerprint == fingerprint }) { skipped++; continue }
                val instant = ZonedDateTime.of(date, time, ZoneId.systemDefault()).toInstant().toEpochMilli(); val now = System.currentTimeMillis()
                entities += TransactionEntity(id, type, amount, field("currency").ifBlank { "CNY" }, instant, date.toEpochDay(), account.id,
                    destination.takeIf { type == TransactionType.TRANSFER },
                    category.takeIf { type in setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND) },
                    merchantName = field("merchant").takeIf { type !in setOf(TransactionType.TRANSFER, TransactionType.BALANCE_ADJUSTMENT) }?.ifBlank { null },
                    note = field("note").ifBlank { null },
                    necessity = field("necessity").takeIf(String::isNotBlank)?.let { Necessity.valueOf(it.uppercase()) },
                    variability = field("variability").takeIf(String::isNotBlank)?.let { Variability.valueOf(it.uppercase()) },
                    isOneOff = field("is_one_off").toBooleanStrictOrNull() ?: false,
                    isReimbursable = field("is_reimbursable").toBooleanStrictOrNull() ?: false,
                    excludeFromBudget = field("exclude_from_budget").toBooleanStrictOrNull() ?: false,
                    importBatchId = hash, source = TransactionSource.CSV, fingerprint = fingerprint,
                    createdAtEpochMillis = now, updatedAtEpochMillis = now, recordGranularity = granularity,
                    periodStartEpochDay = periodStart.toEpochDay(), periodEndEpochDay = periodEnd.toEpochDay())
                entityTags[id] = field("tags").split('|', ';').map(String::trim).filter(String::isNotEmpty)
            } catch (e: Exception) { errors += CsvError(line, e.message ?: "无法解析") }
        }
        var added = 0
        db.withTransaction {
            val result = transactionDao.insertIgnoringDuplicates(entities); added = result.count { it != -1L }; skipped += result.count { it == -1L }
            result.forEachIndexed { index, rowId -> if (rowId != -1L) {
                val entity = entities[index]; val tags = entityTags[entity.id].orEmpty().distinct().map { name -> TagEntity("tag_${sha256(name.lowercase().toByteArray()).take(20)}", name) }
                importDao.upsertTags(tags); importDao.insertTransactionTags(tags.map { TransactionTagCrossRef(entity.id, it.id) })
            } }
            importDao.insertBatch(ImportBatchEntity(hash, fileName, hash, System.currentTimeMillis(), added, skipped, errors.size, "CSV"))
        }
        return CsvImportResult(added, skipped, errors)
    }

    suspend fun exportCsv(output: OutputStream) {
        val accounts = accountDao.active().associateBy { it.id }
        val expenseCategories = categoryDao.active(TransactionDirection.EXPENSE).associateBy { it.id }
        val incomeCategories = categoryDao.active(TransactionDirection.INCOME).associateBy { it.id }
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine(headers.joinToString(","))
            transactionDao.allForExport().forEach { t ->
                val date = LocalDate.ofEpochDay(t.localDateEpochDay)
                val time = Instant.ofEpochMilli(t.occurredAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)
                val category = (expenseCategories[t.categoryId] ?: incomeCategories[t.categoryId])?.name.orEmpty()
                val row = listOf(t.id, t.type.name, BigDecimal.valueOf(t.amountMinor, 2).toPlainString(), t.currencyCode,
                    date.toString(), time.toString(), accounts[t.accountId]?.name.orEmpty(), accounts[t.destinationAccountId]?.name.orEmpty(),
                    category, "", t.merchantName.orEmpty(), importDao.tagsFor(t.id).joinToString("|") { it.name }, t.note.orEmpty(), t.necessity?.name.orEmpty(), t.variability?.name.orEmpty(),
                    t.isOneOff.toString(), t.isReimbursable.toString(), t.excludeFromBudget.toString(), t.recordGranularity.name,
                    t.periodStartEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(), t.periodEndEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty())
                writer.appendLine(row.joinToString(",") { csvEscape(it) })
            }
        }
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if ('\uFFFD' !in utf8) utf8 else bytes.toString(Charset.forName("GB18030"))
    }

    private fun parseCsv(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { row += cell.toString(); cell.clear() }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = mutableListOf()
                }
                else -> cell.append(c)
            }; i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }
    private fun csvEscape(value: String) = if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
