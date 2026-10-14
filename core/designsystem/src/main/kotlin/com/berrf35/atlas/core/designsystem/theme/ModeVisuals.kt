package com.berrf35.atlas.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.berrf35.atlas.core.model.TransportMode

/**
 * Visual encoding specification from MAPS_AND_ANALYTICS §3 & UI_UX_SPEC §1.1/§2:
 * Modes are distinguished by pattern, stroke weight, and subtle accessible tones,
 * never by arbitrary colorful gradients.
 */
data class ModeVisual(
    val mode: TransportMode,
    val label: String,
    val patternDescription: String,
    val strokeWidthDp: Float,
    val dashIntervals: FloatArray? = null,
    val dotIntervals: FloatArray? = null,
    val lightTint: Color,
    val darkTint: Color
)

object ModeVisuals {
    fun get(mode: TransportMode): ModeVisual = when (mode) {
        TransportMode.WALK -> ModeVisual(
            mode = mode,
            label = "Walk",
            patternDescription = "Solid stroke (4dp)",
            strokeWidthDp = 4f,
            lightTint = Color(0xFF2B2B2B),
            darkTint = Color(0xFFE5E5E5)
        )
        TransportMode.RUN -> ModeVisual(
            mode = mode,
            label = "Run",
            patternDescription = "Dotted stroke (4dp)",
            strokeWidthDp = 4f,
            dotIntervals = floatArrayOf(8f, 6f),
            lightTint = Color(0xFF1E1E1E),
            darkTint = Color(0xFFF0F0F0)
        )
        TransportMode.BICYCLE -> ModeVisual(
            mode = mode,
            label = "Bicycle",
            patternDescription = "Cross-ticked stroke (4dp)",
            strokeWidthDp = 4f,
            dashIntervals = floatArrayOf(16f, 4f, 4f, 4f),
            lightTint = Color(0xFF383838),
            darkTint = Color(0xFFD6D6D6)
        )
        TransportMode.MOTORCYCLE -> ModeVisual(
            mode = mode,
            label = "Motorbike / scooter",
            patternDescription = "Heavy solid stroke (5dp)",
            strokeWidthDp = 5f,
            lightTint = Color(0xFF111111),
            darkTint = Color(0xFFFFFFFF)
        )
        TransportMode.CAR -> ModeVisual(
            mode = mode,
            label = "Car",
            patternDescription = "Solid stroke (4dp)",
            strokeWidthDp = 4f,
            lightTint = Color(0xFF222222),
            darkTint = Color(0xFFEEEEEE)
        )
        TransportMode.AUTO_RICKSHAW -> ModeVisual(
            mode = mode,
            label = "Auto-rickshaw",
            patternDescription = "Accent-dashed stroke (4dp)",
            strokeWidthDp = 4f,
            dashIntervals = floatArrayOf(14f, 6f),
            lightTint = Color(0xFF333333),
            darkTint = Color(0xFFDDDDDD)
        )
        TransportMode.BUS -> ModeVisual(
            mode = mode,
            label = "Bus",
            patternDescription = "8:3 dash stroke (4dp)",
            strokeWidthDp = 4f,
            dashIntervals = floatArrayOf(24f, 9f),
            lightTint = Color(0xFF151515),
            darkTint = Color(0xFFEDEDED)
        )
        TransportMode.TRAIN -> ModeVisual(
            mode = mode,
            label = "Train",
            patternDescription = "2:2 dash square caps (4dp)",
            strokeWidthDp = 4f,
            dashIntervals = floatArrayOf(8f, 8f),
            lightTint = Color(0xFF0F0F0F),
            darkTint = Color(0xFFFAFAFA)
        )
        TransportMode.METRO -> ModeVisual(
            mode = mode,
            label = "Metro",
            patternDescription = "2:2 dense dash (4dp)",
            strokeWidthDp = 4f,
            dashIntervals = floatArrayOf(6f, 6f),
            lightTint = Color(0xFF1A1A1A),
            darkTint = Color(0xFFE8E8E8)
        )
        TransportMode.OTHER_MOTORIZED -> ModeVisual(
            mode = mode,
            label = "Other motorised",
            patternDescription = "Long dash (4dp)",
            strokeWidthDp = 4f,
            dashIntervals = floatArrayOf(20f, 10f),
            lightTint = Color(0xFF404040),
            darkTint = Color(0xFFCCCCCC)
        )
        TransportMode.FLIGHT -> ModeVisual(
            mode = mode,
            label = "Flight",
            patternDescription = "Fine dash (3dp)",
            strokeWidthDp = 3f,
            dashIntervals = floatArrayOf(12f, 8f),
            lightTint = Color(0xFF555555),
            darkTint = Color(0xFFBBBBBB)
        )
        TransportMode.UNKNOWN -> ModeVisual(
            mode = mode,
            label = "Unknown",
            patternDescription = "2:4 dotted stroke (3dp)",
            strokeWidthDp = 3f,
            dotIntervals = floatArrayOf(6f, 12f),
            lightTint = Color(0xFF777777),
            darkTint = Color(0xFF999999)
        )
        TransportMode.STATIONARY -> ModeVisual(
            mode = mode,
            label = "Stationary",
            patternDescription = "Circle marker",
            strokeWidthDp = 2f,
            lightTint = Color(0xFF888888),
            darkTint = Color(0xFF888888)
        )
    }
}
