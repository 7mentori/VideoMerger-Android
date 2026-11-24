package com.videomerger.tablet.repository

import android.content.Context
import com.videomerger.tablet.model.MergeItemResult
import com.videomerger.tablet.model.MergeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LatestRunLogger(context: Context) {
    private val logFile = File(context.filesDir, "latest_run.json")

    fun write(mode: MergeMode, startedAt: Long, endedAt: Long, results: List<MergeItemResult>) {
        val items = JSONArray()
        results.forEach { item ->
            items.put(
                JSONObject().apply {
                    put("label", item.label)
                    put("output_name", item.outputName)
                    put("status", item.status.name)
                    put("output_bytes", item.outputBytes)
                    put("message", item.message)
                }
            )
        }
        val root = JSONObject().apply {
            put("started_at", startedAt)
            put("ended_at", endedAt)
            put("mode", mode.name)
            put("success_count", results.count { it.status.name == "SUCCESS" })
            put("failure_count", results.count { it.status.name != "SUCCESS" })
            put("output_bytes", results.sumOf { it.outputBytes })
            put("results", items)
        }
        logFile.writeText(root.toString(2), Charsets.UTF_8)
    }

    fun sizeBytes(): Long = logFile.takeIf { it.exists() }?.length() ?: 0L
}
