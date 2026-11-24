package com.videomerger.tablet.service

import com.videomerger.tablet.model.MergeRequest
import com.videomerger.tablet.model.RunUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object MergeRuntime {
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var pendingRequest: MergeRequest? = null
    @Volatile var activeOutputUri: android.net.Uri? = null
        private set
    private val mutableState = MutableStateFlow(RunUiState())
    val state = mutableState.asStateFlow()

    fun prepare(request: MergeRequest) {
        pendingRequest = request
        stopRequested.set(false)
        mutableState.value = RunUiState(
            isRunning = true,
            mode = request.mode,
            status = "準備中",
            totalJobs = request.jobs.size,
            elapsedMs = 0L
        )
    }

    fun takeRequest(): MergeRequest? {
        val request = pendingRequest
        pendingRequest = null
        return request
    }

    fun shouldStop(): Boolean = stopRequested.get()

    fun setActiveOutput(uri: android.net.Uri?) {
        activeOutputUri = uri
    }

    fun requestStop() {
        stopRequested.set(true)
        mutableState.value = mutableState.value.copy(status = "正在安全停止…")
    }

    fun update(transform: (RunUiState) -> RunUiState) {
        mutableState.value = transform(mutableState.value)
    }

    fun resetAfterServiceFailure(message: String) {
        mutableState.value = mutableState.value.copy(
            isRunning = false,
            finished = true,
            status = "失敗",
            errorDetails = message
        )
    }
}
