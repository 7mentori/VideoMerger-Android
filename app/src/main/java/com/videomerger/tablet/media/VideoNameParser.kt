package com.videomerger.tablet.media

import com.videomerger.tablet.model.ParsedVideoName
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.time.format.ResolverStyle

/** Strict parser for VID_YYYYMMDD_HHMMSS_NNN.mp4 and legacy _00_NNN format. */
object VideoNameParser {
    private val pattern = Regex(
        "^VID_(\\d{8})_(\\d{6})_(?:(00)_)?(\\d{3})\\.mp4$",
        RegexOption.IGNORE_CASE
    )
    private val dateFormatter = DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
    private val timeFormatter = DateTimeFormatter.ofPattern("HHmmss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    fun parse(fileName: String): ParsedVideoName? {
        val match = pattern.matchEntire(fileName) ?: return null
        return try {
            val date = LocalDate.parse(match.groupValues[1], dateFormatter)
            val time = java.time.LocalTime.parse(match.groupValues[2], timeFormatter)
            val sequence = match.groupValues[4].toIntOrNull() ?: return null
            ParsedVideoName(
                date = date,
                dateTime = LocalDateTime.of(date, time),
                sequence = sequence,
                legacyFormat = match.groupValues[3].isNotEmpty()
            )
        } catch (_: DateTimeParseException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
