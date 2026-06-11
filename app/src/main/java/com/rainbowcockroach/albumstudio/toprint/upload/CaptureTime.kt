package com.rainbowcockroach.albumstudio.toprint.upload

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

/**
 * Pure, Android-free helpers for deciding a photo's capture time (`capturedAt`).
 *
 * This app is the single decision point for `capturedAt` in the whole system: the
 * server stores whatever string we send verbatim, and the desktop app uses it to
 * group photos into month-named projects. The resolution chain (EXIF → filename →
 * MediaStore → null) lives in [CaptureTimeResolver]; this object holds the parsing
 * pieces so they can be unit-tested without a device.
 *
 * EXIF datetimes carry no timezone, so everything here is a naive [LocalDateTime]
 * serialized as ISO-8601 local time (e.g. `2026-05-30T18:21:09`). We never invent a
 * zone offset.
 */
object CaptureTime {

    /** EXIF stores datetimes as `yyyy:MM:dd HH:mm:ss`. */
    private val EXIF_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** Camera/screenshot filenames frequently begin with `YYYYMMDD_HHMMSS`. */
    private val FILENAME_REGEX = Regex("""^(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})""")

    /** Serialize to ISO-8601 local datetime, always with seconds (e.g. `2026-05-30T18:21:09`). */
    private val OUTPUT_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .toFormatter()

    /** Parse an EXIF datetime string (`yyyy:MM:dd HH:mm:ss`). Returns null if absent/invalid. */
    fun parseExifDateTime(value: String?): LocalDateTime? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        // EXIF sometimes uses spaces or all-zero placeholders for "unknown".
        if (trimmed.startsWith("0000")) return null
        return try {
            LocalDateTime.parse(trimmed, EXIF_FORMAT)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse a `YYYYMMDD_HHMMSS` prefix out of a display name and validate it as a real
     * date (so e.g. month 13 falls through to the next strategy). Returns null otherwise.
     */
    fun parseFilename(displayName: String?): LocalDateTime? {
        val name = displayName ?: return null
        val m = FILENAME_REGEX.find(name) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        return try {
            LocalDateTime.of(
                y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt()
            )
        } catch (_: Exception) {
            // Out-of-range component (month 13, day 32, hour 25, …) → not a real date.
            null
        }
    }

    /** Render a resolved [LocalDateTime] to the wire format. */
    fun format(dateTime: LocalDateTime): String = dateTime.format(OUTPUT_FORMAT)
}
