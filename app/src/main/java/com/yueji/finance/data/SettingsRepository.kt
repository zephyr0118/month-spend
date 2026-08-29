package com.yueji.finance.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yueji.finance.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore("yueji_settings")

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val fiscalYearStartMonth: Int = 9,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val amountsHidden: Boolean = false,
    val hideInRecents: Boolean = false,
    val appLockEnabled: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val lastBackupEpochMillis: Long? = null,
    val defaultAccountId: String? = null,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val fiscalMonth = intPreferencesKey("fiscal_year_start_month")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val hidden = booleanPreferencesKey("amounts_hidden")
        val hideRecents = booleanPreferencesKey("hide_in_recents")
        val appLock = booleanPreferencesKey("app_lock_enabled")
        val reminder = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val lastBackup = longPreferencesKey("last_backup")
        val defaultAccount = stringPreferencesKey("default_payment_account_id")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            onboardingComplete = p[Keys.onboarding] ?: false, fiscalYearStartMonth = (p[Keys.fiscalMonth] ?: 9).coerceIn(1, 12),
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.dynamic] ?: false, amountsHidden = p[Keys.hidden] ?: false, hideInRecents = p[Keys.hideRecents] ?: false,
            appLockEnabled = p[Keys.appLock] ?: false, reminderEnabled = p[Keys.reminder] ?: false,
            reminderHour = (p[Keys.reminderHour] ?: 20).coerceIn(0, 23), lastBackupEpochMillis = p[Keys.lastBackup], defaultAccountId = p[Keys.defaultAccount],
        )
    }
    suspend fun completeOnboarding() = edit { it[Keys.onboarding] = true }
    suspend fun setFiscalYearStartMonth(value: Int) = edit { it[Keys.fiscalMonth] = value.coerceIn(1, 12) }
    suspend fun setTheme(value: ThemeMode) = edit { it[Keys.theme] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamic] = value }
    suspend fun setAmountsHidden(value: Boolean) = edit { it[Keys.hidden] = value }
    suspend fun setHideInRecents(value: Boolean) = edit { it[Keys.hideRecents] = value }
    suspend fun setAppLock(value: Boolean) = edit { it[Keys.appLock] = value }
    suspend fun setReminder(value: Boolean, hour: Int) = edit { it[Keys.reminder] = value; it[Keys.reminderHour] = hour.coerceIn(0, 23) }
    suspend fun setDefaultAccount(id: String?) = edit { if (id == null) it.remove(Keys.defaultAccount) else it[Keys.defaultAccount] = id }
    suspend fun markBackedUp(now: Long = System.currentTimeMillis()) = edit { it[Keys.lastBackup] = now }
    suspend fun restore(settings: AppSettings) = edit { p ->
        p[Keys.onboarding] = settings.onboardingComplete; p[Keys.fiscalMonth] = settings.fiscalYearStartMonth
        p[Keys.theme] = settings.themeMode.name; p[Keys.dynamic] = settings.dynamicColor; p[Keys.hidden] = settings.amountsHidden
        p[Keys.hideRecents] = settings.hideInRecents; p[Keys.appLock] = settings.appLockEnabled
        p[Keys.reminder] = settings.reminderEnabled; p[Keys.reminderHour] = settings.reminderHour
        settings.lastBackupEpochMillis?.let { p[Keys.lastBackup] = it }
        settings.defaultAccountId?.let { p[Keys.defaultAccount] = it } ?: p.remove(Keys.defaultAccount)
    }
    suspend fun reset() = context.settingsDataStore.edit { it.clear() }
    private suspend fun edit(block: (MutablePreferences) -> Unit) { context.settingsDataStore.edit(block) }
}
