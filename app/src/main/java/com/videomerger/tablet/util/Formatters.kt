package com.videomerger.tablet.util

import java.text.DecimalFormat
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.TAIWAN)
    private val sizeFormat = DecimalFormat("#,##0.##")

    fun bytes(value: Long?): String {
        if (value == null || value < 0L) return "無法取得"
        if (value < 1024L) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var amount = value.toDouble()
        var index = -1
        do {
            amount /= 1024.0
            index++
        } while (amount >= 1024.0 && index < units.lastIndex)
        return "${sizeFormat.format(amount)} ${units[index]}"
    }

    fun date(value: LocalDate): String = dateFormat.format(value)

    fun duration(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val duration = Duration.ofMillis(safe)
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        return if (hours > 0) String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
