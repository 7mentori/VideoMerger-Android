package com.videomerger.tablet.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.videomerger.tablet.model.PendingOutput
import org.json.JSONArray
import org.json.JSONObject

/** Writes final MP4 streams directly into the public Movies/VideoMerger MediaStore collection. */
class MediaStoreOutputRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val collection: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val prefs = context.getSharedPreferences("pending_output_registry", Context.MODE_PRIVATE)

    data class CreatedOutput(val uri: Uri, val displayName: String)

    @Synchronized
    fun createPendingOutput(requestedName: String): CreatedOutput {
        val withoutExtension = if (requestedName.endsWith(".mp4", ignoreCase = true)) requestedName.dropLast(4) else requestedName
        val safeBase = sanitizeFileName(withoutExtension)
        val name = uniqueName(safeBase)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, OUTPUT_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore 無法建立輸出項目")
        val createdAt = System.currentTimeMillis()
        upsertPending(PendingOutput(uri, name, createdAt, 0L, "輸出尚未完成"))
        return CreatedOutput(uri, name)
    }

    fun openForWrite(uri: Uri): ParcelFileDescriptor = resolver.openFileDescriptor(uri, "rw")
        ?: throw IllegalStateException("無法開啟輸出檔案描述元")

    fun publish(uri: Uri) {
        val bytes = querySize(uri)
        if (bytes <= 0L) throw IllegalStateException("輸出檔案大小為 0，不能發布為完成影片")
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        val updated = resolver.update(uri, values, null, null)
        if (updated <= 0) throw IllegalStateException("無法將輸出影片發布到媒體資料庫")
        removePending(uri)
    }

    fun updatePendingReason(uri: Uri, reason: String) {
        val existing = getPendingOutputs().firstOrNull { it.uri == uri }
        upsertPending(
            PendingOutput(
                uri = uri,
                displayName = existing?.displayName ?: queryDisplayName(uri),
                createdAtEpochMs = existing?.createdAtEpochMs ?: System.currentTimeMillis(),
                sizeBytes = querySize(uri),
                reason = reason
            )
        )
    }

    fun querySizeOrNull(uri: Uri): Long? = try {
        val fromQuery = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
            } else null
        }
        if (fromQuery != null && fromQuery >= 0L) fromQuery else {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor -> descriptor.statSize.takeIf { it >= 0L } }
        }
    } catch (_: Exception) {
        null
    }

    fun querySize(uri: Uri): Long = querySizeOrNull(uri) ?: 0L

    fun outputFolderAccessible(): Boolean = try {
        resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    fun existingCompletedDailyDates(): Set<java.time.LocalDate> {
        val result = mutableSetOf<java.time.LocalDate>()
        queryRows(
            projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
            selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.IS_PENDING}=0",
            args = arrayOf(OUTPUT_RELATIVE_PATH)
        ) { cursor ->
            val name = cursor.getString(0) ?: return@queryRows
            val size = cursor.getLong(1)
            if (size > 0L) {
                val match = DAILY_OUTPUT_PATTERN.matchEntire(name)
                match?.groupValues?.getOrNull(1)?.let { dateText ->
                    runCatching { java.time.LocalDate.parse(dateText) }.getOrNull()?.let(result::add)
                }
            }
        }
        return result
    }

    fun totalOutputBytes(): Long? {
        var total = 0L
        val success = queryRows(
            projection = arrayOf(MediaStore.MediaColumns.SIZE),
            selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            args = arrayOf(OUTPUT_RELATIVE_PATH)
        ) { cursor -> total += cursor.getLong(0).coerceAtLeast(0L) }
        return if (success) total else null
    }

    /** Finds pending items left by a process death, including rows not yet saved in the local registry. */
    @Synchronized
    fun reconcilePendingOutputs(): List<PendingOutput> {
        val original = getPendingOutputs().associateBy { it.uri }
        val known = linkedMapOf<Uri, PendingOutput>()
        val querySucceeded = queryRows(
            projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED
            ),
            selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.IS_PENDING}=1",
            args = arrayOf(OUTPUT_RELATIVE_PATH)
        ) { cursor ->
            val id = cursor.getLong(0)
            val uri = Uri.withAppendedPath(collection, id.toString())
            val previous = original[uri]
            val name = cursor.getString(1) ?: previous?.displayName ?: "未命名輸出.mp4"
            val size = cursor.getLong(2).coerceAtLeast(0L)
            val dateAdded = cursor.getLong(3).takeIf { it > 0 }?.times(1000L)
                ?: previous?.createdAtEpochMs ?: System.currentTimeMillis()
            val item = previous?.copy(sizeBytes = size) ?: PendingOutput(
                uri, name, dateAdded, size, "疑似未完成：上次工作未正常發布"
            )
            known[uri] = item
            upsertPending(item)
        }
        if (querySucceeded) {
            // A registry entry absent from the pending MediaStore query is no longer an unfinished item.
            original.keys.filterNot { it in known.keys }.forEach(::removePending)
        } else {
            known.putAll(original)
        }
        return known.values.sortedByDescending { it.createdAtEpochMs }
    }

    fun getPendingOutputs(): List<PendingOutput> {
        val array = runCatching { JSONArray(prefs.getString(KEY_PENDING, "[]")) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val uri = runCatching { Uri.parse(item.getString("uri")) }.getOrNull() ?: continue
                add(
                    PendingOutput(
                        uri = uri,
                        displayName = item.optString("name", "未命名輸出.mp4"),
                        createdAtEpochMs = item.optLong("created", 0L),
                        sizeBytes = item.optLong("size", 0L),
                        reason = item.optString("reason", "疑似未完成")
                    )
                )
            }
        }
    }

    fun deletePending(uri: Uri): Boolean {
        return try {
            val deleted = resolver.delete(uri, null, null) > 0
            if (deleted) removePending(uri)
            deleted
        } catch (_: Exception) {
            false
        }
    }

    private fun uniqueName(base: String): String {
        val existing = queryExistingNames()
        var candidate = "$base.mp4"
        var suffix = 2
        while (candidate in existing) {
            candidate = "${base}_$suffix.mp4"
            suffix++
        }
        return candidate
    }

    private fun queryExistingNames(): Set<String> {
        val result = mutableSetOf<String>()
        val readable = queryRows(
            projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            args = arrayOf(OUTPUT_RELATIVE_PATH)
        ) { cursor -> cursor.getString(0)?.let(result::add) }
        if (!readable) {
            throw IllegalStateException("無法確認輸出目錄中的既有檔案，為避免覆寫或撞名，已停止建立輸出。")
        }
        return result
    }

    private inline fun queryRows(
        projection: Array<String>,
        selection: String,
        args: Array<String>,
        crossinline block: (Cursor) -> Unit
    ): Boolean {
        return try {
            val cursor = resolver.query(collection, projection, selection, args, null) ?: return false
            cursor.use {
                while (it.moveToNext()) block(it)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun queryDisplayName(uri: Uri): String = try {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) ?: "未命名輸出.mp4" else "未命名輸出.mp4"
        } ?: "未命名輸出.mp4"
    } catch (_: Exception) {
        "未命名輸出.mp4"
    }

    @Synchronized
    private fun upsertPending(item: PendingOutput) {
        val all = getPendingOutputs().filterNot { it.uri == item.uri } + item
        val array = JSONArray()
        all.forEach { value ->
            array.put(
                JSONObject().apply {
                    put("uri", value.uri.toString())
                    put("name", value.displayName)
                    put("created", value.createdAtEpochMs)
                    put("size", value.sizeBytes)
                    put("reason", value.reason)
                }
            )
        }
        prefs.edit().putString(KEY_PENDING, array.toString()).apply()
    }

    @Synchronized
    private fun removePending(uri: Uri) {
        val array = JSONArray()
        getPendingOutputs().filterNot { it.uri == uri }.forEach { value ->
            array.put(
                JSONObject().apply {
                    put("uri", value.uri.toString())
                    put("name", value.displayName)
                    put("created", value.createdAtEpochMs)
                    put("size", value.sizeBytes)
                    put("reason", value.reason)
                }
            )
        }
        prefs.edit().putString(KEY_PENDING, array.toString()).apply()
    }

    private fun sanitizeFileName(value: String): String {
        val cleaned = value.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").trim('.')
        return cleaned.ifBlank { "VideoMerger_Output" }
    }

    companion object {
        const val OUTPUT_RELATIVE_PATH = "Movies/VideoMerger/"
        private const val KEY_PENDING = "pending_outputs"
        private val DAILY_OUTPUT_PATTERN = Regex("^VideoMerger_(\\d{4}-\\d{2}-\\d{2})(?:_\\d+)?\\.mp4$", RegexOption.IGNORE_CASE)
    }
}
