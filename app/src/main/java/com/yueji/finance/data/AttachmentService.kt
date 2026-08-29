package com.yueji.finance.data

import android.content.Context
import android.net.Uri
import com.yueji.finance.core.database.AttachmentEntity
import com.yueji.finance.core.database.ImportDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentService @Inject constructor(@ApplicationContext private val context: Context, private val importDao: ImportDao) {
    suspend fun add(transactionId: String, uri: Uri) {
        val resolver = context.contentResolver; val mime = resolver.getType(uri) ?: "application/octet-stream"; val id = UUID.randomUUID().toString()
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it" }.orEmpty()
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }; val file = File(directory, "$id$extension")
        resolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) } ?: error("无法读取附件")
        importDao.insertAttachment(AttachmentEntity(id, transactionId, "attachments/${file.name}", mime, file.length(), System.currentTimeMillis()))
    }
}
