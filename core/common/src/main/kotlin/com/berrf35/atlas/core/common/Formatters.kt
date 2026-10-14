package com.berrf35.atlas.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DistanceFormatter {
    /**
     * Rules from MAPS_AND_ANALYTICS §5:
     * <10 km: metres rounded to 10 m (or "X.XX km" if >= 1 km)
     * 10-100 km: km to 0.1
     * >= 100 km: whole km
     */
    fun format(meters: Long): String {
        return when {
            meters < 1_000 -> {
                val rounded = ((meters + 5) / 10) * 10
                "${rounded} m"
            }
            meters < 10_000 -> {
                val km = meters / 1000.0
                String.format(Locale.US, "%.2f km", km)
            }
            meters < 100_000 -> {
                val km = meters / 1000.0
                String.format(Locale.US, "%.1f km", km)
            }
            else -> {
                val km = Math.round(meters / 1000.0)
                "${km} km"
            }
        }
    }
}

object DurationFormatter {
    /**
     * Rules from MAPS_AND_ANALYTICS §5:
     * < 1 h: "Xm"
     * >= 1 h: "Xh Ym"
     */
    fun format(ms: Long): String {
        val totalSec = Math.max(0L, ms / 1000)
        val minutes = totalSec / 60
        val hours = minutes / 60
        val remMinutes = minutes % 60

        return when {
            minutes < 1 -> "<1m"
            hours < 1 -> "${minutes}m"
            remMinutes == 0L -> "${hours}h"
            else -> "${hours}h ${remMinutes}m"
        }
    }

    fun formatSeconds(sec: Long): String = format(sec * 1000)
}

object SpeedFormatter {
    /**
     * Rules from MAPS_AND_ANALYTICS §5:
     * <20 km/h: km/h to one decimal
     * >=20 km/h: whole km/h
     */
    fun format(kmh: Float): String {
        return when {
            kmh < 0.1f -> "0 km/h"
            kmh < 20.0f -> String.format(Locale.US, "%.1f km/h", kmh)
            else -> "${Math.round(kmh)} km/h"
        }
    }

    fun formatKmh(kmh: Float): String = format(kmh)
}

object DateFormatter {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getDefault()
    }
    private val timeWithSecFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getDefault()
    }
    private val shortDateFormat = SimpleDateFormat("d MMM", Locale.ENGLISH).apply {
        timeZone = TimeZone.getDefault()
    }
    private val fullDateFormat = SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).apply {
        timeZone = TimeZone.getDefault()
    }

    fun formatTime(utcMs: Long): String = timeFormat.format(Date(utcMs))
    fun formatTimeWithSeconds(utcMs: Long): String = timeWithSecFormat.format(Date(utcMs))
    fun formatShortDate(utcMs: Long): String = shortDateFormat.format(Date(utcMs))
    fun formatDate(utcMs: Long): String = shortDateFormat.format(Date(utcMs))
    fun formatFullDate(utcMs: Long): String = fullDateFormat.format(Date(utcMs))
    fun formatTimeRange(startUtcMs: Long, endUtcMs: Long): String =
        "${formatTime(startUtcMs)} – ${formatTime(endUtcMs)}"
}
