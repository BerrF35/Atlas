package com.berrf35.atlas.core.data.repository

import com.berrf35.atlas.core.common.AtlasResult
import com.berrf35.atlas.core.model.AtlasSettings
import com.berrf35.atlas.core.model.BatteryProfile
import com.berrf35.atlas.core.model.CoverageTimelineStrip
import com.berrf35.atlas.core.model.DaySummary
import com.berrf35.atlas.core.model.GapDetailView
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.HealthEventView
import com.berrf35.atlas.core.model.JourneyDetail
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.JourneySummary
import com.berrf35.atlas.core.model.ModeSource
import com.berrf35.atlas.core.model.PeriodAnalytics
import com.berrf35.atlas.core.model.PlaceId
import com.berrf35.atlas.core.model.PlaceView
import com.berrf35.atlas.core.model.RouteClusterView
import com.berrf35.atlas.core.model.SegmentId
import com.berrf35.atlas.core.model.TimeRange
import com.berrf35.atlas.core.model.TrackingHealth
import com.berrf35.atlas.core.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AtlasSettingsRepository {
    val settings: StateFlow<AtlasSettings>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setTrackingEnabled(enabled: Boolean)
    suspend fun setPauseUntil(pauseUntilUtcMs: Long?)
    suspend fun setBatteryProfile(profile: BatteryProfile)
    suspend fun setTheme(theme: String)
    suspend fun setReduceMotion(reduce: Boolean)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setDemoMode(demoMode: Boolean)
    suspend fun setAmbientCacheMb(mb: Int)
    suspend fun setTileServerUrl(url: String)
    suspend fun setReverseGeocodingEnabled(enabled: Boolean)
    suspend fun setScreenshotSafeHeatmap(safe: Boolean)
}

interface TrackingRepository {
    val health: StateFlow<TrackingHealth>
    val currentPosition: StateFlow<GeoPoint?>
    val isTrackingActive: StateFlow<Boolean>
    suspend fun pause(durationMinutes: Int)
    suspend fun resume()
    suspend fun stop()
    suspend fun start()
}

interface JourneyRepository {
    fun getJourneysForDate(date: String): Flow<List<JourneySummary>>
    fun getJourneyDetail(id: JourneyId): Flow<JourneyDetail?>
    fun getDaySummary(date: String): Flow<DaySummary>
    fun searchJourneys(query: String, mode: TransportMode? = null): Flow<List<JourneySummary>>
    suspend fun updateSegmentMode(journeyId: JourneyId, segmentId: SegmentId, newMode: TransportMode, source: ModeSource)
    suspend fun splitJourney(journeyId: JourneyId, atTimeUtcMs: Long)
    suspend fun deleteJourney(journeyId: JourneyId)
    suspend fun restoreJourney(journeyId: JourneyId)
}

interface AnalyticsRepository {
    fun getPeriodAnalytics(periodKey: String): Flow<PeriodAnalytics> // "DAY", "WEEK", "MONTH", "YEAR", "ALL"
}

interface PlacesRepository {
    fun getPlaces(): Flow<List<PlaceView>>
    fun getPlace(id: PlaceId): Flow<PlaceView?>
    suspend fun updatePlace(place: PlaceView)
    suspend fun createPlace(name: String, center: GeoPoint, radiusMeters: Int, isPrivacyZone: Boolean): PlaceId
    suspend fun deletePlace(id: PlaceId)
    fun getRouteClusters(): Flow<List<RouteClusterView>>
}

interface DiagnosticsRepository {
    fun getHealthEvents(): Flow<List<HealthEventView>>
    fun getGapDetail(gapId: String): Flow<GapDetailView?>
    fun getCoverageTimeline(date: String): Flow<CoverageTimelineStrip>
}

interface ExportRepository {
    suspend fun exportData(format: String, dateRange: TimeRange?): AtlasResult<String>
    suspend fun clearAllData(): AtlasResult<Unit>
}
