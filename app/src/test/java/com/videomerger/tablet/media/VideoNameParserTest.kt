package com.videomerger.tablet.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class VideoNameParserTest {
    @Test fun parsesCurrentPattern() {
        val parsed = VideoNameParser.parse("VID_20261124_083000_017.mp4")!!
        assertEquals(LocalDate.of(2026, 11, 24), parsed.date)
        assertEquals(LocalDateTime.of(2026, 11, 24, 8, 30), parsed.dateTime)
        assertEquals(17, parsed.sequence)
        assertFalse(parsed.legacyFormat)
    }

    @Test fun parsesLegacyPattern() {
        val parsed = VideoNameParser.parse("VID_20261124_084000_00_002.MP4")!!
        assertEquals(2, parsed.sequence)
        assertTrue(parsed.legacyFormat)
    }

    @Test fun rejectsInvalidDateAndWrongLegacyMarker() {
        assertNull(VideoNameParser.parse("VID_20260230_083000_001.mp4"))
        assertNull(VideoNameParser.parse("VID_20261124_083000_01_001.mp4"))
        assertNull(VideoNameParser.parse("VID_20261124_083000_001.mov"))
    }
}
