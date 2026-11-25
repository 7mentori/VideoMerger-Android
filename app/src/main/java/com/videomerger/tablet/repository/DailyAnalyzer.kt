
package com.videomerger.tablet.repository

import android.content.Context
import android.net.Uri
import com.videomerger.tablet.media.VideoInspector
import com.videomerger.tablet.media.VideoNameParser
import com.videomerger.tablet.model.DailyAnalysis
import com.videomerger.tablet.model.DailyGroup
import com.videomerger.tablet.model.ScanIssue
import com.videomerger.tablet.model.VideoClip
import com.videomerger.tablet.storage.SafSourceRepository
import com.videomerger.tablet.storage.MediaStoreOutputRepository
import java.time.LocalDate

class DailyAnalyzer(private val context: Context) {

    private val sourceRepository = SafSourceRepository(context)
    private val outputRepository = MediaStoreOutputRepository(context)

    fun analyze(
        treeUri: Uri,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): DailyAnalysis {
        if (!sourceRepository.canReadTree(treeUri)) {
            throw SecurityException(
                "來源資料夾目前無法存取。請確認記憶卡已連接，再重新選擇來源資料夾。"
            )
        }

        val children = try {
            sourceRepository.listFirstLevel(treeUri)
        } catch (error: Exception) {
            throw SecurityException(
                "無法讀取來源資料夾：${error.message ?: "存取失敗"}",
                error
            )
        }

        val total = children.size
        var scanned = 0

        // 檔案清單取得後，先回報一次總數。
        onProgress(0, total)

        val groupedValid = linkedMapOf<LocalDate, MutableList<VideoClip>>()
        val groupedSkipped = linkedMapOf<LocalDate, MutableList<ScanIssue>>()
        val abnormal = mutableListOf<ScanIssue>()

        for (child in children) {
            try {
                // 只分析 MP4；LRV 及其他副檔名完全忽略。
                if (!child.name.endsWith(".mp4", ignoreCase = true)) {
                    continue
                }

                val parsed = VideoNameParser.parse(child.name)

                if (parsed == null) {
                    abnormal += ScanIssue(
                        name = child.name,
                        reason = "檔名不符合指定格式",
                        uri = child.uri
                    )
                    continue
                }

                val inspection = VideoInspector.inspect(context, child.uri)

                val issue = when {
                    !inspection.isReadable ->
                        inspection.error ?: "MP4 檔案無法讀取"

                    !inspection.hasVideo ->
                        "找不到影片軌道"

                    !inspection.hasAudio ->
                        "已跳過－無音訊"

                    inspection.frameRate == null ||
                        inspection.frameRate <= 0 ->
                        "無法判讀影片 FPS"

                    else -> null
                }

                if (issue != null) {
                    groupedSkipped.getOrPut(parsed.date) {
                        mutableListOf()
                    } += ScanIssue(
                        name = child.name,
                        reason = issue,
                        uri = child.uri
                    )
                    continue
                }

                groupedValid.getOrPut(parsed.date) {
                    mutableListOf()
                } += VideoInspector.toClip(
                    child.uri,
                    child.name,
                    child.sizeBytes,
                    parsed,
                    inspection
                )
            } finally {
                // 所有第一層檔案都計入進度，包括被忽略的非 MP4。
                scanned++
                onProgress(scanned, total)
            }
        }

        val allDates = (groupedValid.keys + groupedSkipped.keys)
            .distinct()
            .sorted()

        val existingDates = outputRepository.existingCompletedDailyDates()

        val groups = allDates.map { date ->
            val valid = groupedValid[date].orEmpty().sortedWith(
                compareBy<VideoClip> { it.parsedName?.dateTime }
                    .thenBy { it.parsedName?.sequence ?: Int.MAX_VALUE }
                    .thenBy { it.displayName.lowercase() }
            )

            val skipped = groupedSkipped[date].orEmpty().toMutableList()

            // 同一天必須使用相同 FPS；不同日期可以使用不同 FPS。
			val frameRates = valid.mapNotNull {
				VideoInspector.normalizeFrameRate(it.frameRate)
			}.distinct().sorted()

            val hasMixedFrameRates = frameRates.size > 1

            if (hasMixedFrameRates) {
                skipped += ScanIssue(
                    name = "當日影片",
                    reason = "同一天影片 FPS 不一致：${frameRates.joinToString("、")} FPS"
                )
            }

            val alreadyOutput = date in existingDates

            DailyGroup(
                date = date,
                clips = valid,
                skipped = skipped,
                hasIneligibleFiles = hasMixedFrameRates,
                alreadyOutput = alreadyOutput,
                defaultSelected =
                    valid.size >= 2 &&
                    !alreadyOutput &&
                    !hasMixedFrameRates
            )
        }

        val warnings = buildList {
            if (groups.isEmpty() && abnormal.isEmpty()) {
                add("沒有符合條件的影片")
            }

            if (groups.any { it.validCount < 2 }) {
                add("部分日期的有效影片少於兩部，無法合併")
            }

            if (groups.any { it.hasIneligibleFiles }) {
                add("部分日期的影片 FPS 不一致，無法合併")
            }
        }

        return DailyAnalysis(
            groups = groups,
            abnormalFiles = abnormal,
            scannedFileCount = scanned,
            warnings = warnings
        )
    }
}
