
package com.videomerger.tablet.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.videomerger.tablet.model.VideoClip
import java.nio.ByteBuffer

object VideoInspector {

    data class Inspection(
        val isReadable: Boolean,
        val width: Int,
        val height: Int,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val durationUs: Long,
        val videoFormat: MediaFormat?,
        val audioFormat: MediaFormat?,
        val error: String? = null,
        val frameRate: Int? = null,
        val formatSignature: String? = null
    )

    fun inspect(context: Context, uri: Uri): Inspection {
        val extractor = MediaExtractor()

        return try {
            extractor.setDataSource(context, uri, null)

            var video: MediaFormat? = null
            var audio: MediaFormat? = null
            var duration = 0L

            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()

                val trackDuration =
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        runCatching {
                            format.getLong(MediaFormat.KEY_DURATION)
                        }.getOrDefault(0L)
                    } else {
                        0L
                    }

                duration = maxOf(duration, trackDuration)

                when {
                    mime.startsWith("video/") && video == null -> video = format
                    mime.startsWith("audio/") && audio == null -> audio = format
                }
            }

            val width = video?.let {
                safeInt(it, MediaFormat.KEY_WIDTH)
            } ?: 0

            val height = video?.let {
                safeInt(it, MediaFormat.KEY_HEIGHT)
            } ?: 0

            val frameRate = video?.let {
                safeInt(it, MediaFormat.KEY_FRAME_RATE)
            }?.takeIf { it > 0 }

            val readable = video != null
            val signature = if (video != null && audio != null) {
                buildFormatSignature(video, audio, frameRate)
            } else {
                null
            }

            Inspection(
                isReadable = readable,
                width = width,
                height = height,
                hasVideo = video != null,
                hasAudio = audio != null,
                durationUs = duration,
                videoFormat = video,
                audioFormat = audio,
                error = if (video == null) "找不到影片軌道" else null,
                frameRate = frameRate,
                formatSignature = signature
            )
        } catch (security: SecurityException) {
            Inspection(
                false, 0, 0, false, false, 0L,
                null, null,
                "檔案無法存取：${security.message.orEmpty()}"
            )
        } catch (error: Exception) {
            Inspection(
                false, 0, 0, false, false, 0L,
                null, null,
                "檔案無法讀取：${error.message ?: error.javaClass.simpleName}"
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun toClip(
        uri: Uri,
        name: String,
        sizeBytes: Long,
        parsed: com.videomerger.tablet.model.ParsedVideoName?,
        inspection: Inspection
    ) = VideoClip(
        uri = uri,
        displayName = name,
        sizeBytes = sizeBytes,
        parsedName = parsed,
        width = inspection.width,
        height = inspection.height,
        hasVideo = inspection.hasVideo,
        hasAudio = inspection.hasAudio,
        durationUs = inspection.durationUs,
        isReadable = inspection.isReadable,
        issue = inspection.error,
        frameRate = inspection.frameRate,
        formatSignature = inspection.formatSignature
    )

    private fun buildFormatSignature(
        video: MediaFormat,
        audio: MediaFormat,
        frameRate: Int?
    ): String {
        return buildString {
            append("VIDEO{")
            append(formatTrackSignature(video, true, frameRate))
            append("};AUDIO{")
            append(formatTrackSignature(audio, false, null))
            append("}")
        }
    }

    private fun formatTrackSignature(
        format: MediaFormat,
        isVideo: Boolean,
        frameRate: Int?
    ): String {
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()

        return buildString {
            append("mime=").append(mime)

            if (isVideo) {
                append("|width=").append(safeInt(format, MediaFormat.KEY_WIDTH))
                append("|height=").append(safeInt(format, MediaFormat.KEY_HEIGHT))
                append("|fps=").append(frameRate ?: "UNKNOWN")
                append("|rotation=").append(
                    safeInt(format, MediaFormat.KEY_ROTATION)
                )
            } else {
                append("|sample-rate=").append(
                    safeInt(format, MediaFormat.KEY_SAMPLE_RATE)
                )
                append("|channels=").append(
                    safeInt(format, MediaFormat.KEY_CHANNEL_COUNT)
                )
            }

            append("|profile=").append(optionalInt(format, "profile"))
            append("|level=").append(optionalInt(format, "level"))

            append("|csd=")
            for (index in 0..3) {
                val key = "csd-$index"
                append(key).append(":")
                append(byteBufferSignature(safeByteBuffer(format, key)))
                append(";")
            }
        }
    }

    private fun safeInt(format: MediaFormat, key: String): Int {
        return if (format.containsKey(key)) {
            runCatching { format.getInteger(key) }.getOrDefault(0)
        } else {
            0
        }
    }

    private fun optionalInt(format: MediaFormat, key: String): String {
        return if (format.containsKey(key)) {
            runCatching { format.getInteger(key).toString() }
                .getOrDefault("UNKNOWN")
        } else {
            "UNKNOWN"
        }
    }

    private fun safeByteBuffer(
        format: MediaFormat,
        key: String
    ): ByteBuffer? {
        return if (format.containsKey(key)) {
            runCatching { format.getByteBuffer(key) }.getOrNull()
        } else {
            null
        }
    }

    private fun byteBufferSignature(buffer: ByteBuffer?): String {
        if (buffer == null) return "NONE"

        return runCatching {
            val duplicate = buffer.duplicate()
            val bytes = ByteArray(duplicate.remaining())
            duplicate.get(bytes)

            buildString(bytes.size * 2) {
                for (byte in bytes) {
                    append("%02x".format(byte.toInt() and 0xff))
                }
            }
        }.getOrDefault("UNREADABLE")
    }
}
