package com.videomerger.tablet.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

class SafSourceRepository(private val context: Context) {
    data class ChildFile(val uri: Uri, val name: String, val sizeBytes: Long)

    fun canReadTree(uri: Uri): Boolean = try {
        val tree = DocumentFile.fromTreeUri(context, uri)
        tree != null && tree.exists() && tree.isDirectory && tree.canRead()
    } catch (_: Exception) {
        false
    }

    fun listFirstLevel(uri: Uri): List<ChildFile> {
        val tree = DocumentFile.fromTreeUri(context, uri)
            ?: throw IllegalStateException("來源資料夾 URI 無效")
        if (!tree.isDirectory || !tree.canRead()) {
            throw SecurityException("來源資料夾無法讀取，請重新連接記憶卡或重新授權")
        }
        return tree.listFiles().asSequence()
            .filter { it.isFile }
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                ChildFile(file.uri, name, querySize(file.uri))
            }
            .toList()
    }

    fun querySize(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
                } else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
