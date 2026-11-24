
package com.videomerger.tablet.service

import android.content.Context
import android.os.SystemClock
import com.videomerger.tablet.media.NativeMp4Merger
import com.videomerger.tablet.media.VideoInspector
import com.videomerger.tablet.model.MergeItemResult
import com.videomerger.tablet.model.MergeMode
import com.videomerger.tablet.model.MergeRequest
import com.videomerger.tablet.model.ResultStatus
import com.videomerger.tablet.model.VideoClip
import com.videomerger.tablet.repository.LatestRunLogger
import com.videomerger.tablet.storage.MediaStoreOutputRepository
import com.videomerger.tablet.storage.SafSourceRepository
import com.videomerger.tablet.util.StorageSpace
import java.io.IOException

class MergeCoordinator(private val context: Context) {

    companion object {
        private const val MIXED_FORMAT_MESSAGE =
            "影片來源存在多種格式，無法輸出"
    }

    fun execute(
        request: MergeRequest,
        onNotificationText: (String) -> Unit
    ) {
        val outputRepository = MediaStoreOutputRepository(context)
        val logger = LatestRunLogger(context)
        val engine = NativeMp4Merger(context)

        val startedAt = System.currentTimeMillis()
        val startedElapsed = SystemClock.elapsedRealtime()
        val results = mutableListOf<MergeItemResult>()

        runCatching {
            logger.write(request.mode, startedAt, startedAt, emptyList())
        }

        var stopped = false
        var insufficientSpace = false
        var sourceUnavailable = false

        try {
            for ((jobIndex, job) in request.jobs.withIndex()) {
                if (MergeRuntime.shouldStop()) {
                    stopped = true
                    break
                }

                if (
                    request.mode == MergeMode.DAILY &&
                    request.sourceTreeUri != null &&
                    !SafSourceRepository(context).canReadTree(request.sourceTreeUri)
                ) {
                    sourceUnavailable = true

                    request.jobs.drop(jobIndex).forEach { pendingJob ->
                        results += MergeItemResult(
                            pendingJob.label,
                            null,
                            ResultStatus.FAILED,
                            0L,
                            "來源資料夾已失去存取能力，未執行此日期。"
                        )
                    }

                    break
                }

                val jobStartElapsed = SystemClock.elapsedRealtime()
                val expectedBytes = job.clips.sumOf {
                    it.sizeBytes.coerceAtLeast(0L)
                }

                val initialAvailable = StorageSpace.availableBytes()

                if (
                    initialAvailable != null &&
                    expectedBytes > initialAvailable
                ) {
                    results += MergeItemResult(
                        job.label,
                        null,
                        ResultStatus.FAILED,
                        0L,
                        "儲存空間不足，無法開始此項合併。"
                    )

                    request.jobs.drop(jobIndex + 1).forEach { pendingJob ->
                        results += MergeItemResult(
                            pendingJob.label,
                            null,
                            ResultStatus.FAILED,
                            0L,
                            "儲存空間不足，未執行此日期。"
                        )
                    }

                    insufficientSpace = true
                    break
                }

                MergeRuntime.update {
                    it.copy(
                        isRunning = true,
                        status = "準備中",
                        currentLabel = job.label,
                        currentFile = "",
                        completedJobs = jobIndex,
                        totalJobs = request.jobs.size,
                        progressPercent =
                            if (request.jobs.isEmpty()) null
                            else jobIndex * 100 / request.jobs.size,
                        currentJobElapsedMs = 0L,
                        currentOutputBytes = 0L,
                        elapsedMs =
                            SystemClock.elapsedRealtime() - startedElapsed
                    )
                }

                onNotificationText("影片合併中：${job.label}")

                // 第一階段：重新檢查來源影片。
                val validClips = mutableListOf<VideoClip>()
                val preflightIssues = mutableListOf<String>()
                val signatures = mutableSetOf<String>()
                var unknownFrameRate = false

                for (clip in job.clips) {
                    if (MergeRuntime.shouldStop()) break

                    val now = SystemClock.elapsedRealtime()

                    MergeRuntime.update {
                        it.copy(
                            status = "讀取來源影片",
                            currentFile = clip.displayName,
                            elapsedMs = now - startedElapsed,
                            currentJobElapsedMs = now - jobStartElapsed
                        )
                    }

                    val inspection = VideoInspector.inspect(
                        context,
                        clip.uri
                    )

                    when {
                        !inspection.isReadable -> {
                            preflightIssues +=
                                "${clip.displayName}：${inspection.error ?: "檔案無法讀取"}"
                        }

                        !inspection.hasAudio -> {
                            preflightIssues +=
                                "${clip.displayName}：無音訊軌道"
                        }

                        inspection.frameRate == null -> {
                            unknownFrameRate = true
                        }

                        inspection.formatSignature == null -> {
                            unknownFrameRate = true
                        }

                        else -> {
                            signatures += inspection.formatSignature

                            validClips += VideoInspector.toClip(
                                clip.uri,
                                clip.displayName,
                                clip.sizeBytes,
                                clip.parsedName,
                                inspection
                            )
                        }
                    }
                }

                if (MergeRuntime.shouldStop()) {
                    stopped = true
                    break
                }

                if (
                    request.mode == MergeMode.DAILY &&
                    request.sourceTreeUri != null &&
                    !SafSourceRepository(context).canReadTree(request.sourceTreeUri)
                ) {
                    sourceUnavailable = true

                    results += MergeItemResult(
                        job.label,
                        null,
                        ResultStatus.FAILED,
                        0L,
                        "來源資料夾已失去存取能力，無法繼續讀取影片。"
                    )

                    request.jobs.drop(jobIndex + 1).forEach { pendingJob ->
                        results += MergeItemResult(
                            pendingJob.label,
                            null,
                            ResultStatus.FAILED,
                            0L,
                            "來源資料夾已失去存取能力，未執行此日期。"
                        )
                    }

                    break
                }

                // 格式不一致或 FPS 無法確認：整批拒絕。
                if (
                    signatures.size > 1 ||
                    unknownFrameRate
                ) {
                    results += MergeItemResult(
                        job.label,
                        null,
                        ResultStatus.FAILED,
                        0L,
                        MIXED_FORMAT_MESSAGE
                    )

                    MergeRuntime.update {
                        it.copy(
                            status = "格式不一致",
                            currentFile = ""
                        )
                    }

                    continue
                }

                if (validClips.size < 2) {
                    val reason = buildString {
                        append("重新檢查後有效影片不足兩部，無法合併。")

                        if (preflightIssues.isNotEmpty()) {
                            append("\n")
                            append(preflightIssues.joinToString("\n"))
                        }
                    }

                    results += MergeItemResult(
                        job.label,
                        null,
                        ResultStatus.FAILED,
                        0L,
                        reason
                    )

                    continue
                }

                var created: MediaStoreOutputRepository.CreatedOutput? = null
                var fileDescriptor: android.os.ParcelFileDescriptor? = null

                try {
                    // 所有前置檢查完成後，才建立輸出檔。
                    created = outputRepository.createPendingOutput(
                        job.outputBaseName
                    )

                    MergeRuntime.setActiveOutput(created.uri)

                    fileDescriptor = outputRepository.openForWrite(
                        created.uri
                    )

                    MergeRuntime.update {
                        it.copy(
                            status = "讀取來源影片",
                            currentOutputBytes = 0L
                        )
                    }

                    var lastUiUpdate = 0L

                    val stats = engine.merge(
                        clips = validClips,
                        outputDescriptor = fileDescriptor.fileDescriptor,
                        shouldStop = {
                            MergeRuntime.shouldStop()
                        },
                        onCurrentFile = { fileName ->
                            val now = SystemClock.elapsedRealtime()

                            MergeRuntime.update {
                                it.copy(
                                    status = "讀取來源影片",
                                    currentFile = fileName,
                                    elapsedMs = now - startedElapsed,
                                    currentJobElapsedMs = now - jobStartElapsed
                                )
                            }
                        },
                        onOutputProgress = { _, payloadBytesWritten ->
                            val now = SystemClock.elapsedRealtime()

                            if (now - lastUiUpdate >= 800L) {
                                lastUiUpdate = now

                                val currentBytes =
                                    outputRepository.querySizeOrNull(
                                        created!!.uri
                                    )

                                MergeRuntime.update {
                                    it.copy(
                                        status = "寫入輸出影片",
                                        elapsedMs = now - startedElapsed,
                                        currentJobElapsedMs = now - jobStartElapsed,
                                        currentOutputBytes = currentBytes
                                    )
                                }

                                val free = StorageSpace.availableBytes()

                                val expectedRemaining =
                                    (expectedBytes - payloadBytesWritten)
                                        .coerceAtLeast(0L)

                                if (
                                    free != null &&
                                    free < expectedRemaining
                                ) {
                                    insufficientSpace = true

                                    throw IOException(
                                        "儲存空間不足，已停止目前輸出以避免寫入失敗。"
                                    )
                                }
                            }
                        }
                    )

                    if (MergeRuntime.shouldStop()) {
                        throw NativeMp4Merger.MergeStoppedException()
                    }

                    if (
                        request.mode == MergeMode.DAILY &&
                        request.sourceTreeUri != null &&
                        !SafSourceRepository(context).canReadTree(request.sourceTreeUri)
                    ) {
                        sourceUnavailable = true

                        throw IOException(
                            "來源資料夾已失去存取能力，輸出不予發布。"
                        )
                    }

                    MergeRuntime.update {
                        it.copy(status = "完成封裝")
                    }

                    fileDescriptor.close()
                    fileDescriptor = null

                    outputRepository.publish(created.uri)

                    val bytes = outputRepository.querySize(created.uri)

                    val skippedMessages =
                        preflightIssues +
                        stats.skippedBeforeWrite.map {
                            "${it.first}：${it.second}"
                        }

                    val message =
                        if (skippedMessages.isEmpty()) {
                            "合併成功"
                        } else {
                            "合併成功；略過：" +
                                skippedMessages.joinToString("；")
                        }

                    results += MergeItemResult(
                        job.label,
                        created.displayName,
                        ResultStatus.SUCCESS,
                        bytes,
                        message
                    )

                } catch (error: NativeMp4Merger.MergeStoppedException) {
                    runCatching {
                        fileDescriptor?.close()
                    }

                    created?.let {
                        outputRepository.updatePendingReason(
                            it.uri,
                            "疑似未完成：使用者停止工作"
                        )
                    }

                    stopped = true

                    results += MergeItemResult(
                        job.label,
                        created?.displayName,
                        ResultStatus.STOPPED,
                        created?.let {
                            outputRepository.querySize(it.uri)
                        } ?: 0L,
                        "使用者停止；未完成輸出已保留並標示疑似未完成。"
                    )

                    break

                } catch (error: Exception) {
                    runCatching {
                        fileDescriptor?.close()
                    }

                    created?.let {
                        outputRepository.updatePendingReason(
                            it.uri,
                            "疑似未完成：${error.message ?: error.javaClass.simpleName}"
                        )
                    }

                    val detail = buildString {
                        append(job.label)
                        append("\n")
                        append("輸出：")
                        append(created?.displayName ?: "尚未建立")
                        append("\n")
                        append("原因：")
                        append(error.message ?: error.javaClass.simpleName)

                        if (preflightIssues.isNotEmpty()) {
                            append("\n前置檢查略過：")
                            append(preflightIssues.joinToString("；"))
                        }
                    }

                    results += MergeItemResult(
                        job.label,
                        created?.displayName,
                        ResultStatus.FAILED,
                        created?.let {
                            outputRepository.querySize(it.uri)
                        } ?: 0L,
                        detail
                    )

                    if (
                        request.mode == MergeMode.DAILY &&
                        request.sourceTreeUri != null &&
                        !SafSourceRepository(context).canReadTree(request.sourceTreeUri)
                    ) {
                        sourceUnavailable = true
                    }

                    if (insufficientSpace) {
                        request.jobs.drop(jobIndex + 1).forEach { pendingJob ->
                            results += MergeItemResult(
                                pendingJob.label,
                                null,
                                ResultStatus.FAILED,
                                0L,
                                "儲存空間不足，未執行此日期。"
                            )
                        }

                        break
                    }

                    if (sourceUnavailable) break

                } finally {
                    MergeRuntime.setActiveOutput(null)
                }

                if (sourceUnavailable) {
                    request.jobs.drop(jobIndex + 1).forEach { pendingJob ->
                        results += MergeItemResult(
                            pendingJob.label,
                            null,
                            ResultStatus.FAILED,
                            0L,
                            "來源資料夾已失去存取能力，未執行此日期。"
                        )
                    }

                    break
                }

                MergeRuntime.update {
                    it.copy(
                        completedJobs = results.size,
                        totalJobs = request.jobs.size,
                        progressPercent =
                            if (request.jobs.isEmpty()) null
                            else (
                                results.size * 100 / request.jobs.size
                            ).coerceAtMost(100),
                        elapsedMs =
                            SystemClock.elapsedRealtime() - startedElapsed
                    )
                }
            }

        } catch (error: Exception) {
            results += MergeItemResult(
                "整批工作",
                null,
                ResultStatus.FAILED,
                0L,
                error.message ?: error.javaClass.simpleName
            )
        }

        if (MergeRuntime.shouldStop()) {
            stopped = true
        }

        val endedAt = System.currentTimeMillis()

        val finalStatus = when {
            stopped -> "使用者停止"
            sourceUnavailable -> "來源資料夾無法存取"

            results.isNotEmpty() &&
                results.all { it.status == ResultStatus.SUCCESS } ->
                "全部成功"

            results.isNotEmpty() &&
                results.none { it.status == ResultStatus.SUCCESS } ->
                "全部失敗"

            else -> "部分完成"
        }

        runCatching {
            logger.write(
                request.mode,
                startedAt,
                endedAt,
                results
            )
        }

        val combinedError = results
            .filter { it.status != ResultStatus.SUCCESS }
            .joinToString("\n\n") { it.message }
            .takeIf { it.isNotBlank() }

        MergeRuntime.update {
            it.copy(
                isRunning = false,
                status = if (insufficientSpace) {
                    "儲存空間不足"
                } else {
                    finalStatus
                },
                progressPercent = if (stopped) {
                    it.progressPercent
                } else {
                    100
                },
                completedJobs = results.count {
                    result -> result.status != ResultStatus.STOPPED
                },
                totalJobs = request.jobs.size,
                elapsedMs =
                    SystemClock.elapsedRealtime() - startedElapsed,
                currentOutputBytes = null,
                results = results.toList(),
                finished = true,
                wasStopped = stopped,
                errorDetails = combinedError
            )
        }
    }
}
