
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

    fun analyze(treeUri: Uri): DailyAnalysis {
        if (!sourceRepository.canReadTree(treeUri)) {
            throw SecurityException(
                "來源資料夾目前無法存取。請確認記憶卡已連接，再重新選擇來源資料夾。"
            )
        }

        val groupedValid = linkedMapOf<LocalDate, MutableList<VideoClip>>()
        val groupedSkipped = linkedMapOf<LocalDate, MutableList<ScanIssue>>()
        val abnormal = mutableListOf<ScanIssue>()
        var scanned = 0

        val children = try {
            sourceRepository.listFirstLevel(treeUri)
        } catch (error: Exception) {
            throw SecurityException(
                "無法讀取來源資料夾：${error.message ?: "存取失敗"}",
                error
            )
        }

        for (child in children) {
            if (!child.name.endsWith(".mp4", ignoreCase = true)) {
                abnormal += ScanIssue(child.name, "非 MP4 檔案", child.uri)
                continue
            }

            scanned++

            val parsed = VideoNameParser.parse(child.name)
            if (parsed == null) {
                abnormal += ScanIssue(
                    child.name,
                    "檔名不符合指定格式",
                    child.uri
                )
                continue
            }

            val inspection = VideoInspector.inspect(context, child.uri)

            val issue = when {
                !inspection.isReadable ->
                    inspection.error ?: "檔案無法讀取"

                !inspection.hasAudio ->
                    "已跳過－無音訊"

                else -> null
            }

            if (issue == null) {
                groupedValid.getOrPut(parsed.date) { mutableListOf() } +=
                    VideoInspector.toClip(
                        child.uri,
                        child.name,
                        child.sizeBytes,
                        parsed,
                        inspection
                    )
            } else {
                groupedSkipped.getOrPut(parsed.date) { mutableListOf() } +=
                    ScanIssue(child.name, issue, child.uri)
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

            val hasSkippedFiles = skipped.isNotEmpty()

            val hasUnknownFrameRate = valid.any {
                it.frameRate == null || it.frameRate <= 0
            }

            val signatures = valid.mapNotNull {
                it.formatSignature
            }.distinct()

            val hasMixedFormats =
                valid.size >= 2 && (
                    signatures.size > 1 ||
                    signatures.size != valid.size ||
                    hasUnknownFrameRate
                )

            if (hasMixedFormats) {
                skipped += ScanIssue(
                    name = "當日影片",
                    reason = "影片來源存在多種格式，無法輸出"
                )
            }

            val alreadyOutput = date in existingDates

            val ineligible =
                (valid.isNotEmpty() && hasSkippedFiles) ||
                hasMixedFormats

            DailyGroup(
                date = date,
                clips = valid,
                skipped = skipped,
                hasIneligibleFiles = ineligible,
                alreadyOutput = alreadyOutput,
                defaultSelected =
                    valid.size >= 2 &&
                    !alreadyOutput &&
                    !ineligible
            )
        }

        val warnings = buildList {
            if (groups.isEmpty() && abnormal.isEmpty()) {
                add("沒有符合條件的影片")
            }

            if (groups.any { it.validCount < 2 }) {
                add("部分日期的有效影片少於兩部，無法合併")
            }

            if (groups.any { group ->
                    group.skipped.any {
                        it.reason == "影片來源存在多種格式，無法輸出"
                    }
                }) {
                add("部分日期的影片格式不一致，無法合併")
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
