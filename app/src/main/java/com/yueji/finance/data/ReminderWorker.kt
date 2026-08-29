package com.yueji.finance.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.yueji.finance.R
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.notification_channel_reminders), NotificationManager.IMPORTANCE_DEFAULT).apply { description = applicationContext.getString(R.string.notification_channel_description) })
        val today = LocalDate.now()
        val (title, message) = when {
            today.dayOfMonth == today.lengthOfMonth() -> "月度复盘提醒" to "今天是本月最后一天，花一分钟看看消费、结余和目标完成情况。"
            today.dayOfMonth == 15 -> "月中预算检查" to "本月已经过半，检查消费速度是否与预算时间进度一致。"
            else -> "记账提醒" to "今天是否有尚未记录的收入或支出？"
        }
        manager.notify(1001, NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(title).setContentText(message).setAutoCancel(true).build())
        return Result.success()
    }
    companion object { const val CHANNEL = "finance_reminders" }
}

@Singleton
class ReminderScheduler @Inject constructor(private val workManager: WorkManager) {
    fun schedule(enabled: Boolean, hour: Int) {
        if (!enabled) { workManager.cancelUniqueWork(WORK_NAME); return }
        val now = ZonedDateTime.now(); var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0); if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next)).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    companion object { private const val WORK_NAME = "yueji_daily_reminder" }
}
