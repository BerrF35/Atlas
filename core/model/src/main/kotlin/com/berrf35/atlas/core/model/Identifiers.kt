package com.berrf35.atlas.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class JourneyId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class SegmentId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class ObservationId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class PlaceId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class RouteClusterId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class SessionId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class ExportJobId(val value: String) {
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class StayId(val value: String) {
    override fun toString(): String = value
}
