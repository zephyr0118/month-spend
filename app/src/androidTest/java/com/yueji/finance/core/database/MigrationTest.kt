package com.yueji.finance.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        YueJiDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrate1To2PreservesImportBatchesAndAddsAppVersion() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO import_batches (id,fileName,fileHash,importedAtEpochMillis,successCount,skippedCount,errorCount,source,errorReportPath) VALUES ('one','a.csv','hash',0,1,0,0,'CSV',NULL)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 2, true, DatabaseMigrations.MIGRATION_1_2).use { db ->
            db.query("SELECT appVersion FROM import_batches WHERE id='one'").use { cursor ->
                check(cursor.moveToFirst()); check(cursor.getString(0) == "1.0")
            }
        }
    }

    @Test fun migrate2To3AddsAggregateRecordPeriodFields() {
        val name = "migration-test-2-3"
        helper.createDatabase(name, 2).close()
        helper.runMigrationsAndValidate(name, 3, true, DatabaseMigrations.MIGRATION_2_3).use { db ->
            db.query("PRAGMA table_info(transactions)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
                check("recordGranularity" in columns)
                check("periodStartEpochDay" in columns)
                check("periodEndEpochDay" in columns)
            }
        }
    }

    @Test fun migrate3To4ClearsInheritedCategoriesFromTransfers() {
        val name = "migration-test-3-4"
        helper.createDatabase(name, 3).apply {
            execSQL(
                """INSERT INTO transactions
                (id,type,amountMinor,currencyCode,occurredAtEpochMillis,localDateEpochDay,accountId,categoryId,
                isOneOff,isReimbursable,excludeFromBudget,source,status,balanceDirection,fingerprint,
                createdAtEpochMillis,updatedAtEpochMillis,recordGranularity)
                VALUES ('transfer','TRANSFER',10000,'CNY',0,0,'a','food',0,0,0,'MANUAL','CONFIRMED',1,'fp-transfer',0,0,'DAY')"""
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, DatabaseMigrations.MIGRATION_3_4).use { db ->
            db.query("SELECT categoryId FROM transactions WHERE id='transfer'").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.isNull(0))
            }
        }
    }
}
