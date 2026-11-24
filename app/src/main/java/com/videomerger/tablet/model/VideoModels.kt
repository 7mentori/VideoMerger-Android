
package com.videomerger.tablet.model

import android.net.Uri
import java.time.LocalDate
import java.time.LocalDateTime

data class VideoClip(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val parsedName: ParsedVideoName?,
    val width: Int = 0,
    val height: Int = 0,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val durationUs: Long = 0L,
    val isReadable: Boolean = false,
    val issue: String? = null,
    val frameRate: Int? = null,
    val formatSignature: String? = null
)

data class ScanIssue(
    val name: String,
    val reason: String,
    val uri: Uri? = null
)

data class DailyGroup(
    val date: LocalDate,
    val clips: List<VideoClip>,
    val skipped: List<ScanIssue>,
    val hasIneligibleFiles: Boolean,
    val alreadyOutput: Boolean,
    val defaultSelected: Boolean
) {
    val validCount: Int get() = clips.size
    val estimatedBytes: Long get() = clips.sumOf { it.sizeBytes.coerceAtLeast(0L) }
}

data class DailyAnalysis(
    val groups: List<DailyGroup>,
    val abnormalFiles: List<ScanIssue>,
    val scannedFileCount: Int,
    val warnings: List<String> = emptyList()
)

enum class MergeMode { DAILY, CUSTOM }

data class MergeJob(
    val label: String,
    val outputBaseName: String,
    val clips: List<VideoClip>
)

data class MergeRequest(
    val mode: MergeMode,
    val jobs: List<MergeJob>,
    val sourceTreeUri: Uri? = null,
    val startedAtEpochMs: Long = System.currentTimeMillis()
)

enum class ResultStatus { SUCCESS, FAILED, STOPPED }

data class MergeItemResult(
    val label: String,
    val outputName: String?,
    val status: ResultStatus,
    val outputBytes: Long,
    val message: String
)

data class RunUiState(
    val isRunning: Boolean = false,
    val mode: MergeMode? = null,
    val status: String = "待命",
    val progressPercent: Int? = null,
    val currentLabel: String = "",
    val currentFile: String = "",
    val completedJobs: Int = 0,
    val totalJobs: Int = 0,
    val elapsedMs: Long = 0L,
    val currentJobElapsedMs: Long = 0L,
    val currentOutputBytes: Long? = null,
    val results: List<MergeItemResult> = emptyList(),
    val finished: Boolean = false,
    val wasStopped: Boolean = false,
    val errorDetails: String? = null
)

data class PendingOutput(
    val uri: Uri,
    val displayName: String,
    val createdAtEpochMs: Long,
    val sizeBytes: Long,
    val reason: String,
    val inUse: Boolean = false
)
