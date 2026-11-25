package com.videomerger.tablet.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.app.Activity.RESULT_OK
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.ui.state.ToggleableState
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomerger.tablet.BuildConfig
import com.videomerger.tablet.app.MainViewModel
import com.videomerger.tablet.model.DailyGroup
import com.videomerger.tablet.model.MergeMode
import com.videomerger.tablet.model.PendingOutput
import com.videomerger.tablet.model.ResultStatus
import com.videomerger.tablet.model.RunUiState
import com.videomerger.tablet.model.VideoClip
import com.videomerger.tablet.service.MergeForegroundService
import com.videomerger.tablet.service.MergeRuntime
import com.videomerger.tablet.util.Formatters
import com.videomerger.tablet.util.StorageSpace
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainTab(val title: String) {
    OVERVIEW("總覽"), DAILY("每日合併"), CUSTOM("自訂合併"), SETTINGS("設定")
}

@Composable
fun VideoMergerApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val dark by viewModel.darkTheme.collectAsStateWithLifecycle()
    val sourceUri by viewModel.sourceUri.collectAsStateWithLifecycle()
    val sourceName by viewModel.sourceName.collectAsStateWithLifecycle()
    val sourceAccessible by viewModel.sourceAccessible.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val selectedDates by viewModel.selectedDates.collectAsStateWithLifecycle()
	val selectedClipUris by viewModel.selectedClipUris.collectAsStateWithLifecycle()
	val scanProgressPercent by viewModel.scanProgressPercent.collectAsStateWithLifecycle()
	val scanScannedFiles by viewModel.scanScannedFiles.collectAsStateWithLifecycle()
	val scanTotalFiles by viewModel.scanTotalFiles.collectAsStateWithLifecycle()
    val customClips by viewModel.customClips.collectAsStateWithLifecycle()
    val customIssues by viewModel.customIssues.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsStateWithLifecycle()
    val pendingOutputs by viewModel.pendingOutputs.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val runState by MergeRuntime.state.collectAsState()
    var currentTab by rememberSaveable { mutableStateOf(MainTab.OVERVIEW) }
    var showUnknownSpaceDialog by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showMessageDialog by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingConfirmationStart by remember { mutableStateOf<(() -> Unit)?>(null) }

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setSourceFolder(uri)
    }
    val customPicker = rememberLauncherForActivityResult(OpenMultipleVideoDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addCustomUris(uris)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingStart?.invoke()
        else viewModel.showMessage("需要允許通知，才能在合併期間顯示持續執行通知。請在系統設定中允許通知後再開始。")
        pendingStart = null
    }

    fun requestStart(action: (Boolean) -> MainViewModel.StartResult) {
        fun run(allowUnknownSpace: Boolean) {
            val result = action(allowUnknownSpace)
            when (result) {
                MainViewModel.StartResult.Started -> {
                    pendingConfirmationStart = null
                    showUnknownSpaceDialog = false
                }
                MainViewModel.StartResult.NeedsUnknownSpaceConfirmation -> {
                    pendingConfirmationStart = { run(true) }
                    showUnknownSpaceDialog = true
                }
                is MainViewModel.StartResult.Error -> viewModel.showMessage(result.message)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStart = { run(false) }
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            run(false)
        }
    }

    LaunchedEffect(message) {
        if (message != null) showMessageDialog = true
    }
    LaunchedEffect(runState.isRunning, runState.status, runState.finished) {
        if ((runState.isRunning && runState.status == "讀取來源影片") || runState.finished) {
            viewModel.refreshPendingOutputs()
        }
    }
    LaunchedEffect(runState.finished, runState.mode, runState.results) {
        if (runState.finished && runState.mode == MergeMode.CUSTOM &&
            runState.results.isNotEmpty() && runState.results.all { it.status == ResultStatus.SUCCESS }) {
            viewModel.clearCustomList()
        }
    }

    val activity = context as? Activity
    DisposableEffect(runState.isRunning, activity) {
        if (runState.isRunning) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

	val light = lightColorScheme(
		primary = androidx.compose.ui.graphics.Color(0xFFF59E0B),
		onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
		primaryContainer = androidx.compose.ui.graphics.Color(0xFFFFE8B3),
		onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF573600),
		secondary = androidx.compose.ui.graphics.Color(0xFF16A36A),
		onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
		secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD8F3E5),
		onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF124B35),
		background = androidx.compose.ui.graphics.Color(0xFFFFF9ED),
		surface = androidx.compose.ui.graphics.Color(0xFFFFFDF7),
		surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFFFFDF7),
		onSurface = androidx.compose.ui.graphics.Color(0xFF27364A),
		onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF667085),
		error = androidx.compose.ui.graphics.Color(0xFFDC4444)
	)
    val darkScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFA9C8F4),
        secondary = androidx.compose.ui.graphics.Color(0xFFB9CBE3)
    )

    MaterialTheme(colorScheme = if (dark) darkScheme else light) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
				val availableHeight = this.maxHeight
                val wide = maxWidth >= 760.dp
                if (wide) {
                    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        NavigationRail(
							containerColor = MaterialTheme.colorScheme.surface
						) {
                            Spacer(Modifier.height(12.dp))
                            MainTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { TabIcon(tab) },
                                    label = { Text(tab.title) }
                                )
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxSize()) {
                            if (runState.isRunning) {
								RunProgressCard(
									state = runState,
									compact = availableHeight < 760.dp,
									onStop = { showStopDialog = true }
								)
							}
                            MainTabContent(
                                tab = currentTab,
                                viewModel = viewModel,
                                sourceUri = sourceUri,
                                sourceName = sourceName,
                                sourceAccessible = sourceAccessible,
                                analysis = analysis,
                                isAnalyzing = isAnalyzing,
                                selectedDates = selectedDates,
								selectedClipUris = selectedClipUris,
								scanProgressPercent = scanProgressPercent,
								scanScannedFiles = scanScannedFiles,
								scanTotalFiles = scanTotalFiles,
                                customClips = customClips,
                                customIssues = customIssues,
                                isBusy = isBusy,
                                soundEnabled = soundEnabled,
                                vibrationEnabled = vibrationEnabled,
                                pendingOutputs = pendingOutputs,
                                runState = runState,
                                onPickSource = { sourcePicker.launch(null) },
                                onPickCustom = { customPicker.launch("video/mp4") },
                                onStartDaily = { requestStart { allow -> viewModel.startDailyMerge(allow) } },
                                onStartCustom = { fileName -> requestStart { allow -> viewModel.startCustomMerge(fileName, allow) } },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
								containerColor = MaterialTheme.colorScheme.surface
							) {
                                MainTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentTab == tab,
                                        onClick = { currentTab = tab },
                                        icon = { TabIcon(tab) },
                                        label = { Text(tab.title) }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Column(Modifier.fillMaxSize().padding(innerPadding)) {
                            if (runState.isRunning) {
								RunProgressCard(
									state = runState,
									compact = availableHeight < 760.dp,
									onStop = { showStopDialog = true }
								)
							}
                            MainTabContent(
                                tab = currentTab,
                                viewModel = viewModel,
                                sourceUri = sourceUri,
                                sourceName = sourceName,
                                sourceAccessible = sourceAccessible,
                                analysis = analysis,
                                isAnalyzing = isAnalyzing,
                                selectedDates = selectedDates,
								selectedClipUris = selectedClipUris,
								scanProgressPercent = scanProgressPercent,
								scanScannedFiles = scanScannedFiles,
								scanTotalFiles = scanTotalFiles,
                                customClips = customClips,
                                customIssues = customIssues,
                                isBusy = isBusy,
                                soundEnabled = soundEnabled,
                                vibrationEnabled = vibrationEnabled,
                                pendingOutputs = pendingOutputs,
                                runState = runState,
                                onPickSource = { sourcePicker.launch(null) },
                                onPickCustom = { customPicker.launch("video/mp4") },
                                onStartDaily = { requestStart { allow -> viewModel.startDailyMerge(allow) } },
                                onStartCustom = { fileName -> requestStart { allow -> viewModel.startCustomMerge(fileName, allow) } },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUnknownSpaceDialog) {
        AlertDialog(
            onDismissRequest = { showUnknownSpaceDialog = false },
            title = { Text("無法確認剩餘空間") },
            text = { Text("系統目前無法可靠取得內部儲存空間剩餘容量。仍要開始合併嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    showUnknownSpaceDialog = false
                    val action = pendingConfirmationStart
                    pendingConfirmationStart = null
                    action?.invoke()
                }) { Text("仍要開始") }
            },
            dismissButton = {
                TextButton(onClick = { showUnknownSpaceDialog = false; pendingConfirmationStart = null }) { Text("取消") }
            }
        )
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("停止目前工作？") },
            text = { Text("系統會盡快停止讀取與寫入，並將目前尚未完成的輸出保留為「疑似未完成」。已完成的日期不受影響。") },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    context.startService(Intent(context, MergeForegroundService::class.java).setAction(MergeForegroundService.ACTION_STOP))
                }) { Text("確認停止") }
            },
            dismissButton = { TextButton(onClick = { showStopDialog = false }) { Text("繼續合併") } }
        )
    }

    if (showMessageDialog && message != null) {
        AlertDialog(
            onDismissRequest = { showMessageDialog = false; viewModel.clearMessage() },
            title = { Text("訊息") },
            text = { Text(message.orEmpty()) },
            confirmButton = { TextButton(onClick = { showMessageDialog = false; viewModel.clearMessage() }) { Text("知道了") } }
        )
    }
}

@Composable
private fun TabIcon(tab: MainTab) {
    when (tab) {
        MainTab.OVERVIEW -> Icon(Icons.Default.Home, contentDescription = null)
        MainTab.DAILY -> Icon(Icons.Default.PlayArrow, contentDescription = null)
        MainTab.CUSTOM -> Icon(Icons.Default.FolderOpen, contentDescription = null)
        MainTab.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = null)
    }
}

@Composable
private fun MainTabContent(
    tab: MainTab,
    viewModel: MainViewModel,
    sourceUri: Uri?,
    sourceName: String,
    sourceAccessible: Boolean,
    analysis: com.videomerger.tablet.model.DailyAnalysis?,
    isAnalyzing: Boolean,
    selectedDates: Set<String>,
	selectedClipUris: Set<String>,
	scanProgressPercent: Int?,
	scanScannedFiles: Int,
	scanTotalFiles: Int,
    customClips: List<VideoClip>,
    customIssues: List<String>,
    isBusy: Boolean,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    pendingOutputs: List<PendingOutput>,
    runState: RunUiState,
    onPickSource: () -> Unit,
    onPickCustom: () -> Unit,
    onStartDaily: () -> Unit,
    onStartCustom: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (tab) {
        MainTab.OVERVIEW -> OverviewScreen(
            sourceName, sourceUri, sourceAccessible, analysis, onPickSource, modifier
        )
        MainTab.DAILY -> DailyScreen(
			viewModel = viewModel,
			analysis = analysis,
			isAnalyzing = isAnalyzing,
			selectedDates = selectedDates,
			selectedClipUris = selectedClipUris,
			scanProgressPercent = scanProgressPercent,
			scanScannedFiles = scanScannedFiles,
			scanTotalFiles = scanTotalFiles,
			runState = runState,
			onStart = onStartDaily,
			modifier = modifier
        )
        MainTab.CUSTOM -> CustomScreen(
            viewModel, customClips, customIssues, isBusy, runState, onPickCustom, onStartCustom, modifier
        )
        MainTab.SETTINGS -> SettingsScreen(
            viewModel, sourceUri, sourceAccessible, soundEnabled, vibrationEnabled, pendingOutputs, onPickSource, modifier
        )
    }
}

@Composable
private fun OverviewScreen(
    sourceName: String,
    sourceUri: Uri?,
    sourceAccessible: Boolean,
    analysis: com.videomerger.tablet.model.DailyAnalysis?,
    onPickSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val free = StorageSpace.availableBytes()
    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("總覽", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("來源資料夾", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (sourceUri == null) "尚未選擇來源資料夾" else sourceName, style = MaterialTheme.typography.bodyLarge)
                StatusLine(
                    text = when {
                        sourceUri == null -> "尚未設定"
                        sourceAccessible -> "可以存取"
                        else -> "無法存取，請重新連接或授權"
                    },
                    positive = sourceAccessible
                )
                Button(onClick = onPickSource) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sourceUri == null) "選擇來源資料夾" else "重新選擇來源資料夾")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("影片總數", analysis?.scannedFileCount?.toString() ?: "尚未分析", Modifier.weight(1f))
            MetricCard("日期總數", analysis?.groups?.size?.toString() ?: "尚未分析", Modifier.weight(1f))
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("內部儲存空間", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(Formatters.bytes(free), style = MaterialTheme.typography.headlineSmall)
                Text("顯示平板內部儲存空間可用容量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("App 不會在啟動時自動掃描影片。請前往「每日合併」並手動開始分析。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DailyScreen(
    viewModel: MainViewModel,
    analysis: com.videomerger.tablet.model.DailyAnalysis?,
    isAnalyzing: Boolean,
    selectedDates: Set<String>,
    selectedClipUris: Set<String>,
    scanProgressPercent: Int?,
    scanScannedFiles: Int,
    scanTotalFiles: Int,
    runState: RunUiState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = analysis?.groups.orEmpty()
    val abnormalFiles = analysis?.abnormalFiles.orEmpty()
    val selectedGroups = groups.filter { it.date.toString() in selectedDates }
    val selectedClips = selectedGroups.flatMap { group ->
        group.clips.filter { it.uri.toString() in selectedClipUris }
    }
    val selectedBytes = selectedClips.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    val free = StorageSpace.availableBytes()
    val canFit = free == null || selectedBytes <= free

    var showAbnormal by rememberSaveable { mutableStateOf(false) }
    val expandedDates = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier.fillMaxSize().padding(horizontal = 18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "每日合併",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                onClick = { viewModel.analyzeDaily() },
                enabled = !isAnalyzing && !runState.isRunning
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, null)
                }
                Spacer(Modifier.width(6.dp))
                Text(if (analysis == null) "開始分析" else "重新分析")
            }
        }

        when {
            analysis == null && !isAnalyzing -> {
                EmptyState(
                    "尚未分析來源資料夾",
                    "按下「開始分析」後，App 才會掃描所選資料夾第一層的 MP4。"
                )
            }

            isAnalyzing -> {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (scanTotalFiles < 0) {
                                "正在取得檔案清單……"
                            } else {
                                "正在掃描影片"
                            },
                            fontWeight = FontWeight.SemiBold
                        )

                        if (scanTotalFiles >= 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (scanProgressPercent ?: 0) / 100f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "$scanScannedFiles / $scanTotalFiles 個檔案",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${scanProgressPercent ?: 0}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            "正在檢查第一層資料夾中的 MP4，請保持 App 開啟。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("已選日期")
                            Text(
                                "${selectedGroups.size} / ${groups.size} 天",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("已選影片")
                            Text(
                                "${selectedClips.size} 部",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("預估輸出大小")
                            Text(
                                Formatters.bytes(selectedBytes),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Divider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "目前可用空間",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                Formatters.bytes(free),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "輸出後預估剩餘",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                if (free == null) "無法確認"
                                else Formatters.bytes(free - selectedBytes),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        when {
                            selectedGroups.isEmpty() -> {
                                Text(
                                    "至少選取同一天兩部影片，才能合併。",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            !canFit -> {
                                Text(
                                    "儲存空間不足，無法開始合併。",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            free == null -> {
                                Text(
                                    "無法確認剩餘空間，開始前會再詢問是否繼續。",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onStart,
                    enabled = selectedGroups.isNotEmpty() &&
                        canFit &&
                        !runState.isRunning,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("開始合併")
                }

                Text(
                    "日期清單",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (runState.finished && runState.mode == MergeMode.DAILY) {
                        item {
                            RunResultsCard(runState)
                        }
                    }

                    if (groups.none { it.validCount > 0 }) {
                        item {
                            Text(
                                "沒有符合條件的影片",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    items(groups, key = { it.date.toString() }) { group ->
						DailyGroupCard(
							group = group,
							selectedClipUris = selectedClipUris,
							expanded = expandedDates[group.date.toString()] == true,
							onClipToggle = { uri, checked ->
								viewModel.setClipSelected(uri, checked)
							},
							onDateToggle = { selected ->
								viewModel.setDateSelected(group.date.toString(), selected)
							},
							onExpand = {
								val key = group.date.toString()
								expandedDates[key] = !(expandedDates[key] ?: false)
							}
						)
                    }

                    if (abnormalFiles.isNotEmpty()) {
                        item {
                            AppCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            showAbnormal = !showAbnormal
                                        },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "異常 MP4（${abnormalFiles.size}）",
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            if (showAbnormal) Icons.Default.ExpandLess
                                            else Icons.Default.ExpandMore,
                                            null
                                        )
                                    }

                                    if (showAbnormal) {
                                        abnormalFiles.forEach { issue ->
                                            Divider(Modifier.padding(vertical = 6.dp))
                                            Text(
                                                issue.name,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                issue.reason,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyGroupCard(
    group: DailyGroup,
    selectedClipUris: Set<String>,
    expanded: Boolean,
    onClipToggle: (String, Boolean) -> Unit,
    onDateToggle: (Boolean) -> Unit,
    onExpand: () -> Unit
) {
    val selectedClips = group.clips.filter {
        it.uri.toString() in selectedClipUris
    }
    val selectedBytes = selectedClips.sumOf {
        it.sizeBytes.coerceAtLeast(0L)
    }
    val selectionEnabled = true
	val selectedCount = selectedClips.size

	val dateSelectionState = when {
		selectedCount == 0 -> ToggleableState.Off
		group.clips.isNotEmpty() && selectedCount == group.clips.size -> ToggleableState.On
		else -> ToggleableState.Indeterminate
	}

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				TriStateCheckbox(
					state = dateSelectionState,
					onClick = {
						onDateToggle(selectedCount != group.clips.size)
					},
					enabled = selectionEnabled && group.clips.isNotEmpty()
				)

				Column(
					Modifier.weight(1f)
						.clickable(onClick = onExpand)
						.padding(vertical = 5.dp)
				) {
                    Text(
                        Formatters.date(group.date),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "已選 ${selectedClips.size} / ${group.validCount} 部・${Formatters.bytes(selectedBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (group.alreadyOutput) {
                    Text(
                        "已輸出",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.width(6.dp))
                }

                IconButton(onClick = onExpand) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收合" else "展開"
                    )
                }
            }

            if (group.hasIneligibleFiles) {
                Text(
                    group.skipped.firstOrNull { it.name == "當日影片" }?.reason
                        ?: "當日影片 FPS 不一致，無法合併。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (group.validCount < 2) {
                Text(
                    "有效影片不足兩部，無法合併。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (expanded) {
                Divider(Modifier.padding(vertical = 6.dp))

                Text(
                    "符合條件的影片（${group.validCount} 部）",
                    fontWeight = FontWeight.Medium
                )

                group.clips.forEach { clip ->
                    val uri = clip.uri.toString()
                    val checked = uri in selectedClipUris

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onClipToggle(uri, it) },
                            enabled = selectionEnabled
                        )

                        Column(Modifier.weight(1f)) {
                            Text(
                                clip.displayName,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${Formatters.bytes(clip.sizeBytes)}　${if (clip.durationUs > 0L) Formatters.duration(clip.durationUs / 1000L) else "長度未知"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (group.skipped.isNotEmpty()) {
                    Divider(Modifier.padding(vertical = 6.dp))

                    Text(
                        "略過的影片（${group.skipped.size}）",
                        fontWeight = FontWeight.Medium
                    )

                    group.skipped.forEach { issue ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                issue.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                issue.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Text(
                    "符合條件影片總大小：${Formatters.bytes(group.estimatedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun CustomScreen(
    viewModel: MainViewModel,
    clips: List<VideoClip>,
    issues: List<String>,
    isBusy: Boolean,
    runState: RunUiState,
    onPickFiles: () -> Unit,
    onStart: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fileName by rememberSaveable {
        mutableStateOf("VideoMerger_Custom_Merge_${DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT).format(LocalDateTime.now())}")
    }
    val valid = clips.filter { it.issue == null && it.isReadable && it.hasVideo && it.hasAudio && it.width == 3840 && it.height == 2160 }
    val bytes = valid.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    val free = StorageSpace.availableBytes()
    val canFit = free == null || bytes <= free
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val currentClips by rememberUpdatedState(clips)
    var draggingUri by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("自訂合併", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = onPickFiles, enabled = !runState.isRunning && !isBusy) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(6.dp))
                Text("加入影片")
            }
        }
        OutlinedTextField(
            value = fileName,
            onValueChange = { fileName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("輸出檔名（不含副檔名）") },
            singleLine = true
        )
        Text("多選時依系統選擇器回傳順序加入；可長按拖曳或使用上下箭頭調整實際合併順序。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        if (isBusy) Text("正在檢查選取的影片…", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        SummaryCard(
            title = "自訂清單摘要",
            rows = listOf(
                "清單影片" to "${clips.size} 部",
                "有效影片" to "${valid.size} 部",
                "預估輸出大小" to Formatters.bytes(bytes),
                "目前可用空間" to Formatters.bytes(free),
                "輸出後預估剩餘" to if (free == null) "無法確認剩餘空間" else Formatters.bytes(free - bytes)
            ),
            warning = when {
                valid.size < 2 -> "至少需要兩部符合條件的影片。"
                !canFit -> "儲存空間不足，無法開始合併。"
                free == null -> "無法確認剩餘空間，開始前會再詢問是否繼續。"
                else -> null
            }
        )
        Button(
            onClick = { onStart(fileName) },
            enabled = valid.size >= 2 && canFit && !runState.isRunning && !isBusy,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("開始自訂合併")
        }
        if (runState.finished && runState.mode == MergeMode.CUSTOM) RunResultsCard(runState)
        if (issues.isNotEmpty()) {
            Text("不符合條件的檔案會在開始時略過：${issues.size} 筆", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (clips.isEmpty()) {
            EmptyState("自訂清單目前是空的", "加入兩部以上符合 4K、有影片軌道及音訊軌道的 MP4。")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(clips, key = { _, clip -> clip.uri.toString() }) { index, clip ->
                    val key = clip.uri.toString()
                    Card(
                        Modifier.fillMaxWidth()
                            .onGloballyPositioned { bounds[key] = it.boundsInParent() }
                            .pointerInput(key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingUri = key },
                                    onDragEnd = { draggingUri = null },
                                    onDragCancel = { draggingUri = null },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val origin = bounds[key] ?: return@detectDragGesturesAfterLongPress
                                        val pointerY = origin.top + change.position.y
                                        val targetKey = bounds.entries.firstOrNull { (_, rect) -> pointerY in rect.top..rect.bottom }?.key
                                        val latest = currentClips
                                        val from = latest.indexOfFirst { it.uri.toString() == draggingUri }
                                        val to = latest.indexOfFirst { it.uri.toString() == targetKey }
                                        if (from >= 0 && to >= 0 && from != to) {
                                            viewModel.moveCustomClip(from, to)
                                            draggingUri = key
                                        }
                                    }
                                )
                            },
						colors = CardDefaults.cardColors(
							containerColor = if (draggingUri == key) {
								MaterialTheme.colorScheme.primaryContainer
							} else {
								MaterialTheme.colorScheme.surface
							}
						)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text(clip.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(
                                    "${clip.parsedName?.dateTime?.toString()?.replace('T', ' ') ?: "時間未知"}　${clip.width} × ${clip.height}　${Formatters.bytes(clip.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (clip.issue != null) Text(clip.issue, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.moveCustomClip(index, (index - 1).coerceAtLeast(0)) }, enabled = index > 0) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
                            }
                            IconButton(onClick = { viewModel.moveCustomClip(index, (index + 1).coerceAtMost(clips.lastIndex)) }, enabled = index < clips.lastIndex) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
                            }
                            IconButton(onClick = { viewModel.removeCustomClip(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "移除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    viewModel: MainViewModel,
    sourceUri: Uri?,
    sourceAccessible: Boolean,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    pendingOutputs: List<PendingOutput>,
    onPickSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    var storageStats by remember { mutableStateOf<MainViewModel.AppStorageStats?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        storageStats = withContext(Dispatchers.IO) { viewModel.appStorageStats() }
    }
    var showSingleDelete by remember { mutableStateOf<PendingOutput?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showAccessDetails by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("設定", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SettingsCard("App 資訊") {
            SettingsValueRow("App 名稱", "影片合併器")
            SettingsValueRow("App 版本", BuildConfig.VERSION_NAME)
            SettingsValueRow("Android 版本", "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        }
        SettingsCard("存取狀態") {
            SettingsValueRow("來源資料夾", if (sourceUri == null) "尚未設定" else if (sourceAccessible) "可以存取" else "無法存取")
            SettingsValueRow(
                "輸出存取",
                if (viewModel.outputFolderAccessible()) "MediaStore 可查詢；輸出路徑 Movies/VideoMerger" else "無法確認 MediaStore 存取"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.recheckSourceFolder() }) { Text("重新檢查來源資料夾") }
                OutlinedButton(onClick = onPickSource) { Text("重新選擇來源資料夾") }
                OutlinedButton(onClick = { showAccessDetails = !showAccessDetails }) { Text("存取資訊") }
            }
            if (showAccessDetails) {
                Text(
                    "來源使用 Android 系統資料夾選擇器授予的持續讀取權限。輸出使用 MediaStore 寫入平板內部 Movies/VideoMerger。App 不要求廣泛儲存空間權限。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        SettingsCard("外觀") {
            val dark by viewModel.darkTheme.collectAsStateWithLifecycle()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("深色主題")
                Switch(checked = dark, onCheckedChange = viewModel::updateTheme)
            }
            Text("預設淺色；不跟隨 Android 系統主題。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsCard("完成提示") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("完成提示音")
                Switch(checked = soundEnabled, onCheckedChange = viewModel::updateSound)
            }
            OutlinedButton(onClick = { viewModel.testSound() }) { Text("測試提示音") }
            Divider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("完成震動")
                Switch(checked = vibrationEnabled, onCheckedChange = viewModel::updateVibration)
            }
            OutlinedButton(onClick = { viewModel.testVibration() }) { Text("測試震動") }
        }
        SettingsCard("儲存空間使用量") {
            SettingsValueRow("App 安裝容量（估計）", Formatters.bytes(storageStats?.apkBytes))
            SettingsValueRow("App 私有資料（估計）", Formatters.bytes(storageStats?.privateBytes))
            SettingsValueRow("暫存資料（估計）", Formatters.bytes(storageStats?.cacheBytes))
            SettingsValueRow("工作紀錄及其他資料（估計）", Formatters.bytes(storageStats?.workDataBytes))
            SettingsValueRow("App 輸出影片總容量", Formatters.bytes(storageStats?.outputBytes))
            SettingsValueRow("內部儲存空間剩餘", Formatters.bytes(storageStats?.freeBytes))
            OutlinedButton(onClick = {
                scope.launch { storageStats = withContext(Dispatchers.IO) { viewModel.appStorageStats() } }
            }) { Text("重新計算") }
        }
        SettingsCard("暫存及疑似未完成檔案") {
            Text("目前 ${pendingOutputs.size} 個項目。未完成輸出不會自動刪除。", style = MaterialTheme.typography.bodySmall)
            if (pendingOutputs.isEmpty()) {
                Text("目前沒有需要管理的暫存或疑似未完成檔案。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedButton(onClick = { showDeleteAll = true }) { Text("全選刪除") }
                pendingOutputs.forEach { item ->
                    Divider()
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(item.displayName, fontWeight = FontWeight.Medium)
                        Text("${Formatters.bytes(item.sizeBytes)}　${formatTimestamp(item.createdAtEpochMs)}", style = MaterialTheme.typography.bodySmall)
                        Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showSingleDelete = item }, enabled = !item.inUse) {
                                Text(if (item.inUse) "正在使用中" else "刪除此檔案")
                            }
                        }
                    }
                }
            }
        }
        Text("提示：解除安裝或更新 App 不會由本程式主動刪除已完成輸出影片。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    showSingleDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { showSingleDelete = null },
            title = { Text("刪除疑似未完成檔案？") },
            text = { Text("確定刪除「${item.displayName}」？此動作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    val success = viewModel.deletePending(item.uri)
                    viewModel.showMessage(if (success) "已刪除檔案" else "刪除失敗，請稍後再試")
                    showSingleDelete = null
                }) { Text("確認刪除") }
            },
            dismissButton = { TextButton(onClick = { showSingleDelete = null }) { Text("取消") } }
        )
    }
    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("刪除全部管理項目？") },
            text = { Text("將刪除 ${pendingOutputs.count { !it.inUse }} 個未使用中的暫存或疑似未完成檔案。正在使用中的檔案會保留；已完成影片不在此清單中。") },
            confirmButton = {
                TextButton(onClick = {
                    val failed = pendingOutputs.filterNot { it.inUse }.count { !viewModel.deletePending(it.uri) }
                    viewModel.refreshPendingOutputs()
                    viewModel.showMessage(if (failed == 0) "已完成刪除" else "有 $failed 個項目刪除失敗")
                    showDeleteAll = false
                }) { Text("全部刪除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAll = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun RunProgressCard(
    state: RunUiState,
    compact: Boolean = false,
    onStop: () -> Unit
) {
    AppCard(
        Modifier.fillMaxWidth().padding(
            horizontal = 12.dp,
            vertical = if (compact) 4.dp else 8.dp
        ),

    ) {
        Column(
            Modifier.padding(if (compact) 9.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    Modifier.size(if (compact) 16.dp else 20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "影片正在合併中",
                    style = if (compact) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${state.completedJobs}/${state.totalJobs} 項",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, null)
                    Text("停止")
                }
            }

            if (!compact) {
                Text(
                    "請勿切換 App 或離開程式，以免工作中斷或造成輸出失敗。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "${state.status}　${state.completedJobs}/${state.totalJobs} 項",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    state.status,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.progressPercent != null) {
                LinearProgressIndicator(
                    progress = { state.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "整體工作進度 ${state.progressPercent}%",
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.currentLabel.isNotBlank()) {
                Text(
                    "目前項目：${state.currentLabel}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.currentFile.isNotBlank()) {
                Text(
                    "目前檔案：${state.currentFile}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "總經過 ${Formatters.duration(state.elapsedMs)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "目前項目 ${Formatters.duration(state.currentJobElapsedMs)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "輸出 ${Formatters.bytes(state.currentOutputBytes)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun RunResultsCard(state: RunUiState) {
    val context = LocalContext.current
    val successes = state.results.count { it.status == ResultStatus.SUCCESS }
    val failures = state.results.count { it.status != ResultStatus.SUCCESS }
    AppCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("本次合併結果：${state.status}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("成功 $successes　失敗／停止 $failures　總經過 ${Formatters.duration(state.elapsedMs)}")
            Text("輸出總大小：${Formatters.bytes(state.results.sumOf { it.outputBytes })}")
            state.results.forEach { result ->
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (result.status == ResultStatus.SUCCESS) Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        tint = if (result.status == ResultStatus.SUCCESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(result.label, fontWeight = FontWeight.Medium)
                        Text(result.outputName ?: "沒有正式輸出檔名", style = MaterialTheme.typography.bodySmall)
                        Text(result.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.errorDetails?.let { details ->
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("影片合併錯誤資訊", details))
                }) { Text("複製錯誤資訊") }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, rows: List<Pair<String, String>>, warning: String?) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            rows.forEach { (label, value) -> SettingsValueRow(label, value) }
            if (warning != null) Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}


@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),

    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}


@Composable
private fun SettingsValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusLine(text: String, positive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (positive) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    AppCard(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTimestamp(value: Long): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.TAIWAN)
        .format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
}.getOrDefault("時間未知")


/** ACTION_OPEN_DOCUMENT with a best-effort initial location at the shared internal-storage root. */
private class OpenMultipleVideoDocuments : ActivityResultContract<String, List<Uri>>() {
    override fun createIntent(context: Context, input: String): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = input
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        if (Build.VERSION.SDK_INT >= 26) {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3A")
            )
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != RESULT_OK || intent == null) return emptyList()
        val result = mutableListOf<Uri>()
        intent.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) clipData.getItemAt(index).uri?.let(result::add)
        }
        if (result.isEmpty()) intent.data?.let(result::add)
        return result.distinct()
    }
}


@Composable
private fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color =
        MaterialTheme.colorScheme.surface,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        content = content
    )
}