package com.videomerger.tablet.model

import java.time.LocalDate
import java.time.LocalDateTime

data class ParsedVideoName(
    val date: LocalDate,
    val dateTime: LocalDateTime,
    val sequence: Int,
    val legacyFormat: Boolean
)
