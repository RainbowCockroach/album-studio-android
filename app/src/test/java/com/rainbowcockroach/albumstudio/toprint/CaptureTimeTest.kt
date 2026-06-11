package com.rainbowcockroach.albumstudio.toprint

import com.rainbowcockroach.albumstudio.toprint.upload.CaptureTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureTimeTest {

    // --- EXIF parsing ---

    @Test
    fun exif_dateTimeOriginal_parses() {
        val dt = CaptureTime.parseExifDateTime("2026:05:30 18:21:09")
        assertEquals("2026-05-30T18:21:09", dt?.let { CaptureTime.format(it) })
    }

    @Test
    fun exif_blankOrNull_isNull() {
        assertNull(CaptureTime.parseExifDateTime(null))
        assertNull(CaptureTime.parseExifDateTime(""))
        assertNull(CaptureTime.parseExifDateTime("   "))
    }

    @Test
    fun exif_zeroPlaceholder_isNull() {
        assertNull(CaptureTime.parseExifDateTime("0000:00:00 00:00:00"))
    }

    @Test
    fun exif_garbage_isNull() {
        assertNull(CaptureTime.parseExifDateTime("not a date"))
    }

    // --- Filename parsing ---

    @Test
    fun filename_validPrefix_parses() {
        val dt = CaptureTime.parseFilename("20260530_182109.jpg")
        assertEquals("2026-05-30T18:21:09", dt?.let { CaptureTime.format(it) })
    }

    @Test
    fun filename_screenshotStylePrefix_parses() {
        val dt = CaptureTime.parseFilename("20260101_000000_extra_suffix.heic")
        assertEquals("2026-01-01T00:00:00", dt?.let { CaptureTime.format(it) })
    }

    @Test
    fun filename_invalidMonth_fallsThrough() {
        // Month 13 is not a real date → null so the chain moves on to MediaStore.
        assertNull(CaptureTime.parseFilename("20261330_182109.jpg"))
    }

    @Test
    fun filename_invalidDay_fallsThrough() {
        assertNull(CaptureTime.parseFilename("20260230_999999.jpg"))
    }

    @Test
    fun filename_noPattern_isNull() {
        assertNull(CaptureTime.parseFilename("IMG_1234.jpg"))
        assertNull(CaptureTime.parseFilename(null))
    }

    // --- Chain ordering (EXIF wins over filename) ---

    @Test
    fun chain_prefersExifOverFilename() {
        // Simulates the resolver's priority: EXIF first, then filename, then null.
        val exif = CaptureTime.parseExifDateTime("2020:01:01 12:00:00")
        val resolved = exif ?: CaptureTime.parseFilename("20260530_182109.jpg")
        assertEquals("2020-01-01T12:00:00", resolved?.let { CaptureTime.format(it) })
    }

    @Test
    fun chain_fallsBackToFilename_whenExifAbsent() {
        val exif = CaptureTime.parseExifDateTime(null)
        val resolved = exif ?: CaptureTime.parseFilename("20260530_182109.jpg")
        assertEquals("2026-05-30T18:21:09", resolved?.let { CaptureTime.format(it) })
    }

    @Test
    fun chain_nothingAvailable_isNull() {
        val exif = CaptureTime.parseExifDateTime(null)
        val resolved = exif ?: CaptureTime.parseFilename("IMG_1234.jpg")
        assertNull(resolved)
    }
}
