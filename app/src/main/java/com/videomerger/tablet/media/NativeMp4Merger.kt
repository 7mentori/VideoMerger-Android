
package com.videomerger.tablet.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.videomerger.tablet.model.VideoClip
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Stream-copies compressed video and audio samples without decoding.
 * All sources are preflighted before MediaMuxer is created.
 */
class NativeMp4Merger(private val context: Context) {

    data class MergeStats(
        val writtenInputCount: Int,
        val writtenSamples: Long,
        val outputDurationUs: Long,
        val skippedBeforeWrite: List<Pair<String, String>>
    )

    class MergeStoppedException : Exception("使用者停止合併")

    class MergeEngineException(
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause)

    private data class SelectedTracks(
        val videoIndex: Int,
        val audioIndex: Int,
        val videoFormat: MediaFormat,
        val audioFormat: MediaFormat
    )

    fun merge(
        clips: List<VideoClip>,
        outputDescriptor: FileDescriptor,
        shouldStop: () -> Boolean,
        onCurrentFile: (String) -> Unit,
        onOutputProgress: (samplesWritten: Long, payloadBytesWritten: Long) -> Unit
    ): MergeStats {

        if (clips.size < 2) {
            throw MergeEngineException("有效影片不足兩部，無法合併")
        }

        var muxer: MediaMuxer? = null
        var started = false

        val extractors = mutableListOf<MediaExtractor>()

        try {
            // 第一階段：完整預檢所有來源，尚未建立輸出封裝器。
            var referenceSignature: String? = null
            var referenceTracks: SelectedTracks? = null

            for ((index, clip) in clips.withIndex()) {
                if (shouldStop()) throw MergeStoppedException()

                onCurrentFile(clip.displayName)

                val extractor = try {
                    createSelectedExtractor(clip.uri).also {
                        extractors += it
                    }
                } catch (error: Exception) {
                    throw MergeEngineException(
                        "${clip.displayName}：來源影片無法讀取，整批取消輸出。",
                        error
                    )
                }

                val tracks = findTracks(extractor)
                    ?: throw MergeEngineException(
                        "${clip.displayName}：缺少影片或音訊軌道，整批取消輸出。"
                    )

                val inspection = VideoInspector.inspect(context, clip.uri)

                if (!inspection.isReadable || !inspection.hasAudio) {
                    throw MergeEngineException(
                        "${clip.displayName}：${inspection.error ?: "缺少有效影片或音訊軌道"}"
                    )
                }

                val signature = inspection.formatSignature
                    ?: throw MergeEngineException(
                        "影片來源存在多種格式，無法輸出"
                    )

                if (inspection.frameRate == null) {
                    throw MergeEngineException(
                        "影片來源存在多種格式，無法輸出"
                    )
                }

                if (index == 0) {
                    referenceSignature = signature
                    referenceTracks = tracks
                } else if (signature != referenceSignature) {
                    throw MergeEngineException(
                        "影片來源存在多種格式，無法輸出"
                    )
                }
            }

            if (shouldStop()) throw MergeStoppedException()

            val templateTracks = referenceTracks
                ?: throw MergeEngineException("沒有可供封裝的有效來源影片。")

            // 第二階段：所有來源格式確認一致後，才建立輸出封裝器。
            val activeMuxer = MediaMuxer(
                outputDescriptor,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = activeMuxer

            val outputVideoTrack = activeMuxer.addTrack(
                templateTracks.videoFormat
            )
            val outputAudioTrack = activeMuxer.addTrack(
                templateTracks.audioFormat
            )

            val rotation =
                if (templateTracks.videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    runCatching {
                        templateTracks.videoFormat.getInteger(MediaFormat.KEY_ROTATION)
                    }.getOrDefault(0)
                } else {
                    0
                }

            if (rotation in setOf(90, 180, 270)) {
                activeMuxer.setOrientationHint(rotation)
            }

            activeMuxer.start()
            started = true

            // 第三階段：依序複製每個來源的壓縮影音樣本。
            var writtenSamples = 0L
            var writtenPayloadBytes = 0L
            var timelineOffsetUs = 0L
            var writtenInputs = 0

            val lastPtsByOutputTrack = mutableMapOf<Int, Long>()

            for ((clipIndex, clip) in clips.withIndex()) {
                if (shouldStop()) throw MergeStoppedException()

                onCurrentFile(clip.displayName)

                val currentExtractor = extractors[clipIndex]
                val tracks = findTracks(currentExtractor)
                    ?: throw MergeEngineException(
                        "${clip.displayName}：來源軌道在預檢後發生變化。"
                    )

                val startUs = firstSelectedPresentationTime(currentExtractor)

                val sourceDurationUs = maxOf(
                    durationOf(tracks.videoFormat),
                    durationOf(tracks.audioFormat),
                    clip.durationUs
                )

                val nominalVideoFrameUs =
                    nominalVideoFrameDuration(tracks.videoFormat)

                var maxRelativePtsUs = 0L
                var samplesFromThisClip = 0L
                var videoSamplesFromThisClip = 0L
                var audioSamplesFromThisClip = 0L

                val inputTrackToOutput = mapOf(
                    tracks.videoIndex to outputVideoTrack,
                    tracks.audioIndex to outputAudioTrack
                )

                val maxInputSize = maxOf(
                    maxInputSize(tracks.videoFormat),
                    maxInputSize(tracks.audioFormat),
                    MIN_BUFFER_BYTES
                ).coerceAtMost(MAX_BUFFER_BYTES)

                var buffer = ByteBuffer.allocateDirect(maxInputSize)
                val bufferInfo = MediaCodec.BufferInfo()

                while (true) {
                    if (shouldStop()) throw MergeStoppedException()

                    val sampleTrack = currentExtractor.sampleTrackIndex
                    if (sampleTrack < 0) break

                    val outputTrack = inputTrackToOutput[sampleTrack]
                    val sampleTimeUs = currentExtractor.sampleTime

                    if (outputTrack != null && sampleTimeUs >= 0L) {
                        val declaredSampleSize = currentExtractor.sampleSize

                        if (declaredSampleSize > MAX_BUFFER_BYTES) {
                            throw MergeEngineException(
                                "${clip.displayName} 的單一影音樣本超過 64 MB 安全緩衝區上限"
                            )
                        }

                        if (declaredSampleSize > buffer.capacity()) {
                            buffer = ByteBuffer.allocateDirect(
                                declaredSampleSize.toInt()
                            )
                        }

                        buffer.clear()

                        val size = currentExtractor.readSampleData(buffer, 0)
                        if (size < 0) break

                        if (size > buffer.capacity()) {
                            throw MergeEngineException(
                                "${clip.displayName} 的單一影音樣本超過緩衝區上限"
                            )
                        }

                        val relativePts =
                            (sampleTimeUs - startUs).coerceAtLeast(0L)

                        val outputPts = timelineOffsetUs + relativePts
                        val previousPts = lastPtsByOutputTrack[outputTrack]

                        if (previousPts != null && outputPts < previousPts) {
                            throw MergeEngineException(
                                "${clip.displayName} 的時間戳順序倒退，MediaMuxer 無法安全封裝。"
                            )
                        }

                        buffer.position(0)
                        buffer.limit(size)

                        bufferInfo.set(
                            0,
                            size,
                            outputPts,
                            currentExtractor.sampleFlags
                        )

                        activeMuxer.writeSampleData(
                            outputTrack,
                            buffer,
                            bufferInfo
                        )

                        lastPtsByOutputTrack[outputTrack] = outputPts
                        maxRelativePtsUs = maxOf(maxRelativePtsUs, relativePts)

                        samplesFromThisClip++

                        if (outputTrack == outputVideoTrack) {
                            videoSamplesFromThisClip++
                        }

                        if (outputTrack == outputAudioTrack) {
                            audioSamplesFromThisClip++
                        }

                        writtenSamples++
                        writtenPayloadBytes += size.toLong()

                        onOutputProgress(
                            writtenSamples,
                            writtenPayloadBytes
                        )
                    }

                    if (!currentExtractor.advance()) break
                }

                if (samplesFromThisClip == 0L ||
                    videoSamplesFromThisClip == 0L ||
                    audioSamplesFromThisClip == 0L
                ) {
                    throw MergeEngineException(
                        "${clip.displayName}：未能完整讀取影片與音訊樣本，整批輸出失敗。"
                    )
                }

                writtenInputs++

                val frameDuration =
                    nominalVideoFrameUs.coerceAtLeast(1L)

                val durationFloor =
                    maxRelativePtsUs + frameDuration

                timelineOffsetUs += maxOf(
                    sourceDurationUs,
                    durationFloor
                )
            }

            if (writtenInputs != clips.size) {
                throw MergeEngineException(
                    "實際成功讀取的影片數量與來源數量不一致，輸出不予發布。"
                )
            }

            if (shouldStop()) throw MergeStoppedException()

            activeMuxer.stop()
            started = false

            return MergeStats(
                writtenInputCount = writtenInputs,
                writtenSamples = writtenSamples,
                outputDurationUs = timelineOffsetUs,
                skippedBeforeWrite = emptyList()
            )

        } catch (error: MergeStoppedException) {
            throw error
        } catch (error: MergeEngineException) {
            throw error
        } catch (error: Exception) {
            throw MergeEngineException(
                error.message ?: error.javaClass.simpleName,
                error
            )
        } finally {
            if (started) {
                runCatching { muxer?.stop() }
            }

            runCatching { muxer?.release() }

            extractors.forEach {
                runCatching { it.release() }
            }
        }
    }

    private fun createSelectedExtractor(uri: Uri): MediaExtractor {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(context, uri, null)

            val tracks = findTracks(extractor)
                ?: throw IllegalStateException("缺少影片或音訊軌道")

            extractor.selectTrack(tracks.videoIndex)
            extractor.selectTrack(tracks.audioIndex)

            return extractor
        } catch (error: Exception) {
            runCatching { extractor.release() }
            throw error
        }
    }

    private fun findTracks(extractor: MediaExtractor): SelectedTracks? {
        var videoIndex = -1
        var audioIndex = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()

            when {
                mime.startsWith("video/") && videoIndex < 0 -> {
                    videoIndex = index
                    videoFormat = format
                }

                mime.startsWith("audio/") && audioIndex < 0 -> {
                    audioIndex = index
                    audioFormat = format
                }
            }
        }

        if (videoIndex < 0 || audioIndex < 0) return null

        return SelectedTracks(
            videoIndex,
            audioIndex,
            videoFormat!!,
            audioFormat!!
        )
    }

    private fun firstSelectedPresentationTime(
        extractor: MediaExtractor
    ): Long {
        return extractor.sampleTime.takeIf { it >= 0L } ?: 0L
    }

    private fun durationOf(format: MediaFormat): Long {
        return if (format.containsKey(MediaFormat.KEY_DURATION)) {
            runCatching {
                format.getLong(MediaFormat.KEY_DURATION)
            }.getOrDefault(0L).coerceAtLeast(0L)
        } else {
            0L
        }
    }

    private fun maxInputSize(format: MediaFormat): Int {
        return if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            runCatching {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            }.getOrDefault(MIN_BUFFER_BYTES)
        } else {
            MIN_BUFFER_BYTES
        }
    }

    private fun nominalVideoFrameDuration(format: MediaFormat): Long {
        val frameRate =
            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                runCatching {
                    format.getInteger(MediaFormat.KEY_FRAME_RATE)
                }.getOrDefault(30)
            } else {
                30
            }

        return 1_000_000L / frameRate.coerceIn(1, 240)
    }

    companion object {
        private const val MIN_BUFFER_BYTES = 1024 * 1024
        private const val MAX_BUFFER_BYTES = 64 * 1024 * 1024
    }
}
