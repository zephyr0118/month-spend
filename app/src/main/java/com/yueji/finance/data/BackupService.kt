package com.yueji.finance.data

import android.content.Context
import com.yueji.finance.BuildConfig
import com.yueji.finance.core.database.YueJiDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import com.yueji.finance.core.model.ThemeMode

data class BackupInfo(val createdAt: String, val appVersion: String, val transactionCount: Int, val accountCount: Int, val encrypted: Boolean)
data class RestoreResult(val info: BackupInfo, val safetyBackupPath: String, val restartRequired: Boolean = true)

@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: YueJiDatabase,
    private val financeRepository: FinanceRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val databaseName = "yueji.db"

    suspend fun create(output: OutputStream, password: CharArray? = null): BackupInfo {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val dbFile = context.getDatabasePath(databaseName)
        check(dbFile.exists()) { "数据库尚未创建" }
        val transactionCount = financeRepository.transactionCount()
        val accountCount = db.accountDao().active().size
        val encrypted = password?.isNotEmpty() == true
        val info = BackupInfo(Instant.now().toString(), BuildConfig.VERSION_NAME, transactionCount, accountCount, encrypted)
        val zipFile = File.createTempFile("yueji_backup_", ".zip", context.cacheDir)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                addText(zip, "manifest.json", JSONObject().apply {
                    put("formatVersion", 1); put("createdAt", info.createdAt); put("appVersion", info.appVersion)
                    put("transactionCount", transactionCount); put("accountCount", accountCount)
                }.toString(2))
                addFile(zip, "database.sqlite", dbFile)
                val preferences = settingsRepository.settings.first()
                addText(zip, "preferences.json", JSONObject().apply {
                    put("onboardingComplete", preferences.onboardingComplete); put("fiscalYearStartMonth", preferences.fiscalYearStartMonth)
                    put("themeMode", preferences.themeMode.name); put("dynamicColor", preferences.dynamicColor); put("amountsHidden", preferences.amountsHidden)
                    put("hideInRecents", preferences.hideInRecents); put("appLockEnabled", preferences.appLockEnabled)
                    put("reminderEnabled", preferences.reminderEnabled); put("reminderHour", preferences.reminderHour)
                    preferences.lastBackupEpochMillis?.let { put("lastBackupEpochMillis", it) }
                    preferences.defaultAccountId?.let { put("defaultAccountId", it) }
                }.toString(2))
                val attachments = File(context.filesDir, "attachments")
                if (attachments.exists()) attachments.walkTopDown().filter(File::isFile).forEach { file ->
                    addFile(zip, "attachments/${file.relativeTo(attachments).invariantSeparatorsPath}", file)
                }
                addText(zip, "checksum.json", JSONObject().put("database.sqlite", sha256(dbFile)).toString(2))
            }
            if (!encrypted) zipFile.inputStream().use { it.copyTo(output) } else encrypt(zipFile, output, requireNotNull(password))
            output.flush(); settingsRepository.markBackedUp()
            return info
        } finally { zipFile.delete() }
    }

    suspend fun inspect(input: InputStream, password: CharArray? = null): BackupInfo {
        val zip = materializeZip(input, password)
        return try { readManifest(zip, password?.isNotEmpty() == true) } finally { zip.delete() }
    }

    suspend fun restore(input: InputStream, password: CharArray? = null): RestoreResult {
        val zipFile = materializeZip(input, password)
        val restoreDir = File(context.cacheDir, "restore_${System.nanoTime()}").apply { mkdirs() }
        var safety: File? = null
        var databaseReplaced = false
        try {
            safeUnzip(zipFile, restoreDir)
            val incomingDb = File(restoreDir, "database.sqlite")
            check(incomingDb.exists()) { "备份中缺少 database.sqlite" }
            val checksums = JSONObject(File(restoreDir, "checksum.json").readText())
            check(checksums.getString("database.sqlite") == sha256(incomingDb)) { "数据库校验和不匹配" }
            val info = readManifest(zipFile, password?.isNotEmpty() == true)
            val current = context.getDatabasePath(databaseName)
            val safetyDir = File(context.filesDir, "safety_backups").apply { mkdirs() }
            safety = File(safetyDir, "before_restore_${System.currentTimeMillis()}.sqlite")
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            if (current.exists()) current.copyTo(requireNotNull(safety), overwrite = true)
            db.close()
            val staged = File(current.parentFile, "$databaseName.restore")
            incomingDb.copyTo(staged, overwrite = true)
            if (current.exists() && !current.delete()) error("无法替换当前数据库")
            if (!staged.renameTo(current)) { staged.copyTo(current, overwrite = true); staged.delete() }
            databaseReplaced = true
            File(current.path + "-wal").delete(); File(current.path + "-shm").delete()
            val pref = File(restoreDir, "preferences.json")
            if (pref.exists()) JSONObject(pref.readText()).let { p -> settingsRepository.restore(AppSettings(
                onboardingComplete = p.optBoolean("onboardingComplete", true), fiscalYearStartMonth = p.optInt("fiscalYearStartMonth", 9),
                themeMode = runCatching { ThemeMode.valueOf(p.optString("themeMode", "SYSTEM")) }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = p.optBoolean("dynamicColor", false), amountsHidden = p.optBoolean("amountsHidden", false),
                hideInRecents = p.optBoolean("hideInRecents", false), appLockEnabled = p.optBoolean("appLockEnabled", false),
                reminderEnabled = p.optBoolean("reminderEnabled", false), reminderHour = p.optInt("reminderHour", 20),
                lastBackupEpochMillis = if (p.has("lastBackupEpochMillis")) p.getLong("lastBackupEpochMillis") else null,
                defaultAccountId = p.optString("defaultAccountId").takeIf(String::isNotBlank),
            )) }
            val attachments = File(restoreDir, "attachments")
            if (attachments.exists()) attachments.copyRecursively(File(context.filesDir, "attachments"), overwrite = true)
            return RestoreResult(info, requireNotNull(safety).absolutePath)
        } catch (e: Exception) {
            if (databaseReplaced && safety?.exists() == true) {
                val current = context.getDatabasePath(databaseName)
                safety.copyTo(current, overwrite = true)
                File(current.path + "-wal").delete(); File(current.path + "-shm").delete()
            }
            throw IllegalStateException("恢复失败，当前数据未被修改：${e.message}", e)
        } finally { zipFile.delete(); restoreDir.deleteRecursively() }
    }

    private fun materializeZip(input: InputStream, password: CharArray?): File {
        val raw = File.createTempFile("yueji_input_", ".bin", context.cacheDir)
        input.use { source -> raw.outputStream().use(source::copyTo) }
        val magic = raw.inputStream().use { stream -> ByteArray(MAGIC.size).also { stream.read(it) } }
        val encrypted = magic.contentEquals(MAGIC)
        if (!encrypted) return raw
        require(password?.isNotEmpty() == true) { "该备份已加密，请输入密码" }
        val zip = File.createTempFile("yueji_decrypted_", ".zip", context.cacheDir)
        try { decrypt(raw, zip.outputStream(), requireNotNull(password)); raw.delete(); return zip } catch (e: Exception) { zip.delete(); raw.delete(); throw IllegalArgumentException("密码错误或备份已损坏", e) }
    }

    private fun readManifest(zipFile: File, encrypted: Boolean): BackupInfo {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "manifest.json") {
                    val obj = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    return BackupInfo(obj.getString("createdAt"), obj.getString("appVersion"), obj.getInt("transactionCount"), obj.getInt("accountCount"), encrypted)
                }
            }
        }
        error("备份中缺少 manifest.json")
    }

    private fun safeUnzip(zipFile: File, target: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(target, entry.name).canonicalFile
                check(output.path.startsWith(target.canonicalPath + File.separator)) { "备份包含非法路径" }
                if (entry.isDirectory) output.mkdirs() else { output.parentFile?.mkdirs(); output.outputStream().buffered().use(zip::copyTo) }
            }
        }
    }

    private fun addText(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }
    private fun addFile(zip: ZipOutputStream, path: String, file: File) {
        zip.putNextEntry(ZipEntry(path)); file.inputStream().buffered().use { input -> input.copyTo(zip) }; zip.closeEntry()
    }
    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun encrypt(file: File, output: OutputStream, password: CharArray) {
        val random = SecureRandom(); val salt = ByteArray(16).also(random::nextBytes); val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        output.write(MAGIC); output.write(salt); output.write(iv)
        javax.crypto.CipherOutputStream(output, cipher).use { encrypted -> file.inputStream().use { it.copyTo(encrypted) } }
    }
    private fun decrypt(file: File, output: OutputStream, password: CharArray) {
        file.inputStream().buffered().use { input ->
            check(readExact(input, MAGIC.size).contentEquals(MAGIC)); val salt = readExact(input, 16); val iv = readExact(input, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            javax.crypto.CipherInputStream(input, cipher).use { it.copyTo(output) }
        }
    }
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 120_000, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }
    private fun readExact(input: InputStream, size: Int): ByteArray = ByteArray(size).also { DataInputStream(input).readFully(it) }
    companion object { private val MAGIC = "YJENC1".toByteArray(Charsets.US_ASCII) }
}
