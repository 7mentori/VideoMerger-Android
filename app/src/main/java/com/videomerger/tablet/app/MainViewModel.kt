package com.videomerger.tablet.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.videomerger.tablet.media.VideoInspector
import com.videomerger.tablet.media.VideoNameParser
import com.videomerger.tablet.model.DailyAnalysis
import com.videomerger.tablet.model.MergeJob
import com.videomerger.tablet.model.MergeMode
import com.videomerger.tablet.model.MergeRequest
import com.videomerger.tablet.model.PendingOutput
import com.videomerger.tablet.model.VideoClip
import com.videomerger.tablet.repository.DailyAnalyzer
import com.videomerger.tablet.service.MergeForegroundService
import com.videomerger.tablet.service.MergeRuntime
import com.videomerger.tablet.storage.MediaStoreOutputRepository
import com.videomerger.tablet.storage.SafSourceRepository
import com.videomerger.tablet.util.StorageSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val preferences = AppPreferences(application)
    private val sourceRepository = SafSourceRepository(application)
    private val outputRepository = MediaStoreOutputRepository(application)

    private val _sourceUri = MutableStateFlow(preferences.sourceTreeUri)
    val sourceUri = _sourceUri.asStateFlow()
    private val _sourceName = MutableStateFlow(resolveTreeName(preferences.sourceTreeUri))
    val sourceName = _sourceName.asStateFlow()
    private val _sourceAccessible = MutableStateFlow(false)
    val sourceAccessible = _sourceAccessible.asStateFlow()
    private val _analysis = MutableStateFlow<DailyAnalysis?>(null)
    val analysis = _analysis.asStateFlow()
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()
	private val _scanProgressPercent = MutableStateFlow<Int?>(null)
	val scanProgressPercent = _scanProgressPercent.asStateFlow()
	private val _scanScannedFiles = MutableStateFlow(0)
	val scanScannedFiles = _scanScannedFiles.asStateFlow()
	private val _scanTotalFiles = MutableStateFlow(-1)
	val scanTotalFiles = _scanTotalFiles.asStateFlow()
	private val _selectedDates = MutableStateFlow<Set<String>>(emptySet())
	val selectedDates = _selectedDates.asStateFlow()
	private val _selectedClipUris = MutableStateFlow<Set<String>>(emptySet())
	val selectedClipUris = _selectedClipUris.asStateFlow()
    private val _customClips = MutableStateFlow<List<VideoClip>>(emptyList())
    val customClips = _customClips.asStateFlow()
    private val _customIssues = MutableStateFlow<List<String>>(emptyList())
    val customIssues = _customIssues.asStateFlow()
    private val _darkTheme = MutableStateFlow(preferences.darkTheme)
    val darkTheme = _darkTheme.asStateFlow()
    private val _soundEnabled = MutableStateFlow(preferences.soundEnabled)
    val soundEnabled = _soundEnabled.asStateFlow()
    private val _vibrationEnabled = MutableStateFlow(preferences.vibrationEnabled)
    val vibrationEnabled = _vibrationEnabled.asStateFlow()
    private val _pendingOutputs = MutableStateFlow<List<PendingOutput>>(emptyList())
    val pendingOutputs = _pendingOutputs.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    init {
        refreshPendingOutputs()
        preferences.sourceTreeUri?.let { uri ->
            viewModelScope.launch(Dispatchers.IO) {
                _sourceAccessible.value = sourceRepository.canReadTree(uri)
            }
        }
    }

    fun setSourceFolder(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val permissionPersisted = try {
                app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            } catch (_: SecurityException) {
                false
            }
            val readable = permissionPersisted && sourceRepository.canReadTree(uri)
            withContext(Dispatchers.Main) {
                if (!permissionPersisted) {
                    _message.value = "系統檔案選擇器未提供持續讀取授權，無法安全保存來源資料夾。請重新選擇資料夾。"
                    return@withContext
                }
                val previousUri = _sourceUri.value
                if (previousUri != null && previousUri != uri) {
                    runCatching {
                        app.contentResolver.releasePersistableUriPermission(
                            previousUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                preferences.sourceTreeUri = uri
                _sourceUri.value = uri
                _sourceName.value = resolveTreeName(uri)
                _sourceAccessible.value = readable
				_analysis.value = null
				_selectedDates.value = emptySet()
				_selectedClipUris.value = emptySet()
                if (!readable) _message.value = "所選來源資料夾目前無法讀取，請重新連接記憶卡或重新授權。"
            }
        }
    }

    fun recheckSourceFolder() {
        val uri = _sourceUri.value ?: run {
            _message.value = "尚未選擇來源資料夾"
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val readable = withContext(Dispatchers.IO) { sourceRepository.canReadTree(uri) }
            _sourceAccessible.value = readable
            _isBusy.value = false
            _message.value = if (readable) "來源資料夾可以正常存取" else "來源資料夾無法存取，請重新連接記憶卡或重新選擇資料夾"
        }
    }

	fun analyzeDaily() {
		val uri = _sourceUri.value ?: run {
			_message.value = "尚未選擇來源資料夾"
			return
		}

		if (_isAnalyzing.value || MergeRuntime.state.value.isRunning) return

		viewModelScope.launch {
			_isAnalyzing.value = true
			_analysis.value = null
			_selectedDates.value = emptySet()
			_selectedClipUris.value = emptySet()

			// -1 表示正在取得檔案清單，尚未知道總數。
			_scanTotalFiles.value = -1
			_scanScannedFiles.value = 0
			_scanProgressPercent.value = null

			try {
				val result = withContext(Dispatchers.IO) {
					DailyAnalyzer(app).analyze(uri) { scanned, total ->
						_scanScannedFiles.value = scanned
						_scanTotalFiles.value = total

						_scanProgressPercent.value = if (total > 0) {
							(scanned * 100 / total).coerceIn(0, 100)
						} else {
							100
						}
					}
				}

				_analysis.value = result

				// 所有有效影片預設勾選。
				_selectedClipUris.value = result.groups
					.filter { it.defaultSelected }
					.flatMap { it.clips }
					.map { it.uri.toString() }
					.toSet()

				// 日期預設選取條件：至少有兩支有效影片，且沒有 FPS 不一致。
				_selectedDates.value = result.groups
					.filter { it.defaultSelected }
					.map { it.date.toString() }
					.toSet()

				if (result.groups.isEmpty() && result.abnormalFiles.isEmpty()) {
					_message.value = "沒有符合條件的影片"
				}
			} catch (error: Exception) {
				_message.value = error.message ?: "分析來源資料夾時發生錯誤"
			} finally {
				_isAnalyzing.value = false
			}
		}
	}

	fun setDateSelected(date: String, selected: Boolean) {
		val group = _analysis.value?.groups
			?.firstOrNull { it.date.toString() == date }
			?: return

		val current = _selectedClipUris.value.toMutableSet()

		group.clips.forEach { clip ->
			if (selected) {
				current.add(clip.uri.toString())
			} else {
				current.remove(clip.uri.toString())
			}
		}

		_selectedClipUris.value = current
		updateSelectedDates()
	}

	fun setClipSelected(uri: String, selected: Boolean) {
		val current = _selectedClipUris.value.toMutableSet()

		if (selected) {
			current.add(uri)
		} else {
			current.remove(uri)
		}

		_selectedClipUris.value = current
		updateSelectedDates()
	}

	private fun updateSelectedDates() {
		val selectedUris = _selectedClipUris.value

		_selectedDates.value = _analysis.value?.groups
			?.filter { group ->
				group.clips.count { it.uri.toString() in selectedUris } >= 2
			}
			?.map { it.date.toString() }
			?.toSet()
			.orEmpty()
	}

	fun selectAllDates() {
		_selectedClipUris.value = _analysis.value?.groups
			?.flatMap { it.clips }
			?.map { it.uri.toString() }
			?.toSet()
			.orEmpty()

		updateSelectedDates()
	}

	fun clearAllDates() {
		_selectedClipUris.value = emptySet()
		updateSelectedDates()
	}

	fun invertDates() {
		val allUris = _analysis.value?.groups
			?.flatMap { it.clips }
			?.map { it.uri.toString() }
			?.toSet()
			.orEmpty()

		_selectedClipUris.value = allUris - _selectedClipUris.value
		updateSelectedDates()
	}

    fun addCustomUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            val additions = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    try {
                        val permissionPersisted = try {
                            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            true
                        } catch (_: SecurityException) {
                            false
                        }
                        val (name, size) = queryDocumentDetails(uri)
                        if (_customClips.value.any { it.uri == uri }) return@mapNotNull null
                        val parsed = VideoNameParser.parse(name)
                        if (!permissionPersisted) {
                            VideoClip(uri, name, size, parsed, issue = "系統未提供持續讀取權限，請重新選取此檔案")
                        } else if (!name.endsWith(".mp4", true)) {
                            VideoClip(uri, name, size, parsed, issue = "非 MP4 檔案")
                        } else {
                            val inspection = VideoInspector.inspect(app, uri)
                            val issue = when {
                                !inspection.isReadable -> inspection.error ?: "檔案無法讀取"
                                !inspection.hasAudio -> "無音訊軌道"
                                inspection.width != 3840 || inspection.height != 2160 -> "解析度不是 3840 × 2160"
                                else -> null
                            }
                            VideoInspector.toClip(uri, name, size, parsed, inspection).copy(issue = issue)
                        }
                    } catch (error: Exception) {
                        val (name, size) = runCatching { queryDocumentDetails(uri) }
                            .getOrDefault("未命名影片.mp4" to 0L)
                        VideoClip(uri, name, size, VideoNameParser.parse(name), issue = "檔案無法存取：${error.message ?: "未知原因"}")
                    }
                }
            }
            _customClips.value = _customClips.value + additions
            _customIssues.value = _customClips.value.filter { it.issue != null }
                .map { "${it.displayName}：${it.issue}" }
            _isBusy.value = false
        }
    }

    fun moveCustomClip(from: Int, to: Int) {
        val current = _customClips.value.toMutableList()
        if (from !in current.indices || to !in current.indices || from == to) return
        val item = current.removeAt(from)
        current.add(to, item)
        _customClips.value = current
    }

    fun removeCustomClip(index: Int) {
        val current = _customClips.value.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        _customClips.value = current
        _customIssues.value = current.filter { it.issue != null }.map { "${it.displayName}：${it.issue}" }
    }

    fun clearCustomList() {
        _customClips.value = emptyList()
        _customIssues.value = emptyList()
    }

    fun startDailyMerge(allowUnknownSpace: Boolean = false): StartResult {
        val analysis = _analysis.value ?: return StartResult.Error("請先開始分析")
        val selected = analysis.groups.filter { it.date.toString() in _selectedDates.value }
        if (selected.isEmpty()) return StartResult.Error("請至少選取一個日期")
		val selectedUris = _selectedClipUris.value

		val jobs = selected.mapNotNull { group ->
			val clips = group.clips.filter {
				it.uri.toString() in selectedUris
			}

			if (clips.size < 2) null else MergeJob(
				label = group.date.toString(),
				outputBaseName = "VideoMerger_${group.date}",
				clips = clips
			)
		}
        if (jobs.isEmpty()) return StartResult.Error("所選日期沒有至少兩部符合條件的影片")
        return startRequest(MergeRequest(MergeMode.DAILY, jobs, sourceTreeUri = _sourceUri.value), allowUnknownSpace)
    }

    fun startCustomMerge(customFileName: String, allowUnknownSpace: Boolean = false): StartResult {
        if (MergeRuntime.state.value.isRunning) return StartResult.Error("目前已有合併工作正在執行")
        val valid = _customClips.value.filter {
            it.issue == null && it.isReadable && it.hasVideo && it.hasAudio && it.width == 3840 && it.height == 2160
        }
        if (valid.size < 2) return StartResult.Error("有效影片不足兩部，請檢查自訂合併清單")
        val cleaned = customFileName.trim().ifBlank {
            "VideoMerger_Custom_Merge_${DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT).format(LocalDateTime.now())}"
        }
        return startRequest(
            MergeRequest(MergeMode.CUSTOM, listOf(MergeJob("自訂合併", cleaned, valid))),
            allowUnknownSpace
        )
    }

    private fun startRequest(request: MergeRequest, allowUnknownSpace: Boolean): StartResult {
        if (MergeRuntime.state.value.isRunning) return StartResult.Error("目前已有合併工作正在執行")
        val estimated = request.jobs.sumOf { job -> job.clips.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
        val free = StorageSpace.availableBytes()
        if (free != null && free < estimated) return StartResult.Error("儲存空間不足，無法開始合併。")
        if (free == null && !allowUnknownSpace) return StartResult.NeedsUnknownSpaceConfirmation
        try {
            MergeRuntime.prepare(request)
            val intent = Intent(app, MergeForegroundService::class.java)
            ContextCompat.startForegroundService(app, intent)
            return StartResult.Started
        } catch (error: Exception) {
            MergeRuntime.resetAfterServiceFailure(error.message ?: "無法啟動合併服務")
            return StartResult.Error(error.message ?: "無法啟動合併服務")
        }
    }

    fun updateTheme(dark: Boolean) {
        preferences.darkTheme = dark
        _darkTheme.value = dark
    }

    fun updateSound(enabled: Boolean) {
        preferences.soundEnabled = enabled
        _soundEnabled.value = enabled
    }

    fun updateVibration(enabled: Boolean) {
        preferences.vibrationEnabled = enabled
        _vibrationEnabled.value = enabled
    }

    fun testSound() {
        runCatching {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 400)
            android.os.Handler(app.mainLooper).postDelayed({ tone.release() }, 550L)
        }
    }

    fun testVibration() {
        runCatching {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
                app.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            if (android.os.Build.VERSION.SDK_INT >= 26) vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(250L, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
            ) else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(250L)
            }
        }
    }

    fun outputFolderAccessible(): Boolean = outputRepository.outputFolderAccessible()

    fun refreshPendingOutputs() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = outputRepository.reconcilePendingOutputs()
            _pendingOutputs.value = items.map { item ->
                val active = item.uri == MergeRuntime.activeOutputUri
                item.copy(
                    inUse = active,
                    reason = if (active) "目前工作正在寫入" else "疑似未完成：上次輸出未正常發布"
                )
            }
        }
    }

    fun deletePending(uri: Uri): Boolean {
        if (uri == MergeRuntime.activeOutputUri) return false
        val success = outputRepository.deletePending(uri)
        refreshPendingOutputs()
        return success
    }

    fun appStorageStats(): AppStorageStats {
        val appInfo = app.applicationInfo
        val apkBytes = runCatching {
            val base = java.io.File(appInfo.sourceDir).length()
            base + (appInfo.splitSourceDirs?.sumOf { java.io.File(it).length() } ?: 0L)
        }.getOrDefault(0L)
        val privateBytes = directorySize(app.dataDir)
        val cacheBytes = directorySize(app.cacheDir)
        return AppStorageStats(
            apkBytes = apkBytes,
            privateBytes = privateBytes,
            cacheBytes = cacheBytes,
            workDataBytes = (privateBytes - cacheBytes).coerceAtLeast(0L),
            outputBytes = outputRepository.totalOutputBytes(),
            freeBytes = StorageSpace.availableBytes()
        )
    }

    fun clearMessage() { _message.value = null }
    fun showMessage(message: String) { _message.value = message }

    private fun queryDocumentDetails(uri: Uri): Pair<String, Long> {
        var name = "未命名影片.mp4"
        var size = 0L
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }

    private fun resolveTreeName(uri: Uri?): String {
        if (uri == null) return "尚未選擇來源資料夾"
        return runCatching { DocumentFile.fromTreeUri(app, uri)?.name }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    }

    private fun directorySize(file: java.io.File?): Long {
        if (file == null || !file.exists()) return 0L
        return if (file.isFile) file.length() else file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }

    data class AppStorageStats(
        val apkBytes: Long,
        val privateBytes: Long,
        val cacheBytes: Long,
        val workDataBytes: Long,
        val outputBytes: Long?,
        val freeBytes: Long?
    )

    sealed class StartResult {
        data object Started : StartResult()
        data object NeedsUnknownSpaceConfirmation : StartResult()
        data class Error(val message: String) : StartResult()
    }
}
