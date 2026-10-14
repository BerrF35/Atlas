package com.berrf35.atlas.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransportMode(val code: String, val label: String) {
    STATIONARY("STATIONARY", "Stationary"),
    WALK("WALK", "Walk"),
    RUN("RUN", "Run"),
    BICYCLE("BICYCLE", "Bicycle"),
    MOTORCYCLE("MOTORCYCLE", "Motorbike / scooter"),
    CAR("CAR", "Car"),
    AUTO_RICKSHAW("AUTO_RICKSHAW", "Auto-rickshaw"),
    BUS("BUS", "Bus"),
    TRAIN("TRAIN", "Train"),
    METRO("METRO", "Metro"),
    OTHER_MOTORIZED("OTHER_MOTORIZED", "Other motorised"),
    FLIGHT("FLIGHT", "Flight"),
    UNKNOWN("UNKNOWN", "Unknown");

    companion object {
        fun fromCode(code: String): TransportMode =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

@Serializable
enum class ModeSource(val code: String) {
    MODEL("MODEL"),
    USER("USER"),
    IMPORT("IMPORT"),
    RULE("RULE")
}

@Serializable
enum class SegmentKind(val code: String) {
    MOVE("MOVE"),
    GAP("GAP")
}

@Serializable
enum class JourneyState(val code: String) {
    OPEN("OPEN"),
    CANDIDATE("CANDIDATE"),
    CONFIRMED("CONFIRMED"),
    DISCARDED("DISCARDED"),
    DELETED("DELETED")
}

@Serializable
enum class StayKind(val code: String, val label: String) {
    STOP("STOP", "Short stop"),
    TRANSFER("TRANSFER", "Transfer"),
    VISIT("VISIT", "Visit")
}

@Serializable
enum class PlaceStatus(val code: String) {
    CANDIDATE("CANDIDATE"),
    CONFIRMED("CONFIRMED"),
    IGNORED("IGNORED"),
    DELETED("DELETED")
}

@Serializable
enum class GapCause(val code: String, val label: String, val guidance: String) {
    RECORDER_DOWN("RECORDER_DOWN", "Recording service was interrupted", "The operating system may have stopped the background service."),
    NO_FIX("NO_FIX", "GPS signal unavailable", "Occurs indoors, underground, in dense urban canyons, or tunnels."),
    PERMISSION_BLOCKED("PERMISSION_BLOCKED", "Location permission missing", "Background location permission is required for continuous tracking."),
    LOCATION_OFF("LOCATION_OFF", "Location services disabled", "System location was turned off in Android settings."),
    USER_PAUSED("USER_PAUSED", "Recording paused by user", "Tracking was manually paused."),
    LATE_START("LATE_START", "Late departure detection", "Sensor detection took extra time before GPS acquisition started."),
    BATTERY_SAVER("BATTERY_SAVER", "Battery Saver restricted location", "OxygenOS Battery Saver throttled background GPS polling."),
    UNKNOWN("UNKNOWN", "Unknown interruption", "Insufficient diagnostic evidence to pinpoint the exact cause.")
}

@Serializable
enum class TrackingHealth(val code: String, val label: String) {
    RECORDING("RECORDING", "Recording"),
    RESTING("RESTING", "Resting"),
    DEGRADED("DEGRADED", "Degraded"),
    INTERRUPTED("INTERRUPTED", "Interrupted"),
    BLOCKED("BLOCKED", "Blocked"),
    PAUSED("PAUSED", "Paused"),
    OFF("OFF", "Off")
}

@Serializable
enum class BatteryProfile(val code: String, val label: String, val description: String) {
    PRECISE("PRECISE", "Precise", "Highest fix frequency; continuous sensor windows while moving; recommended for detailed tracking."),
    BALANCED("BALANCED", "Balanced", "Adaptive sampling with motion gating; optimal compromise between fidelity and battery (default)."),
    SAVER("SAVER", "Saver", "Coarser fix interval and throttled sensors; preserves maximum battery on long journeys.")
}

@Serializable
enum class ConfidenceBand {
    HIGH,
    MEDIUM,
    LOW;

    companion object {
        fun fromPosterior(p: Float?): ConfidenceBand {
            if (p == null) return LOW
            return when {
                p >= 0.80f -> HIGH
                p >= 0.55f -> MEDIUM
                else -> LOW
            }
        }
    }
}
