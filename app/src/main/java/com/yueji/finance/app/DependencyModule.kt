package com.yueji.finance.app

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.yueji.finance.core.database.*
import com.yueji.finance.data.FinanceRepository
import com.yueji.finance.data.OfflineFinanceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindFinanceRepository(impl: OfflineFinanceRepository): FinanceRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): YueJiDatabase =
        Room.databaseBuilder(context, YueJiDatabase::class.java, "yueji.db")
            .addMigrations(*DatabaseMigrations.ALL).enableMultiInstanceInvalidation().build()
    @Provides fun accountDao(db: YueJiDatabase) = db.accountDao()
    @Provides fun categoryDao(db: YueJiDatabase) = db.categoryDao()
    @Provides fun merchantDao(db: YueJiDatabase) = db.merchantDao()
    @Provides fun transactionDao(db: YueJiDatabase) = db.transactionDao()
    @Provides fun planningDao(db: YueJiDatabase) = db.planningDao()
    @Provides fun historyDao(db: YueJiDatabase) = db.historyDao()
    @Provides fun importDao(db: YueJiDatabase) = db.importDao()
    @Provides @Singleton fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
