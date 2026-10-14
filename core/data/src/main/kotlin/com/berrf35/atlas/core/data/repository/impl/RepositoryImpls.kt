package com.berrf35.atlas.core.data.repository.impl

import android.content.Context
import com.berrf35.atlas.core.common.AtlasError
import com.berrf35.atlas.core.common.AtlasResult
import com.berrf35.atlas.core.data.analytics.AnalyticsAndPlaces
import com.berrf35.atlas.core.data.database.AtlasDao
import com.berrf35.atlas.core.data.demo.AtlasDemoData
import com.berrf35.atlas.core.data.di.AtlasDataLocator
import com.berrf35.atlas.core.data.export.DataExporter
import com.berrf35.atlas.core.data.repository.AnalyticsRepository
import com.berrf35.atlas.core.data.repository.AtlasSettingsRepository
import com.berrf35.atlas.core.data.repository.DiagnosticsRepository
import com.berrf35.atlas.core.data.repository.ExportRepository
import com.berrf35.atlas.core.data.repository.JourneyRepository
import com.berrf35.atlas.core.data.repository.PlacesRepository
import com.berrf35.atlas.core.data.repository.TrackingRepository
import com.berrf35.atlas.core.data.tracking.AtlasTrackingService
import com.berrf35.atlas.core.datastore.AtlasPreferencesDataSource
import com.berrf35.atlas.core.model.AtlasSettings
import com.berrf35.atlas.core.model.BatteryProfile
import com.berrf35.atlas.core.model.CoverageTimelineStrip
import com.berrf35.atlas.core.model.DaySummary
import com.berrf35.atlas.core.model.GapCause
import com.berrf35.atlas.core.model.GapDetailView
import com.berrf35.atlas.core.model.GeoPoint
import com.berrf35.atlas.core.model.HealthEventView
import com.berrf35.atlas.core.model.JourneyDetail
import com.berrf35.atlas.core.model.JourneyId
import com.berrf35.atlas.core.model.JourneySummary
import com.berrf35.atlas.core.model.ModeSource
import com.berrf35.atlas.core.model.PeriodAnalytics
import com.berrf35.atlas.core.model.PlaceId
import com.berrf35.atlas.core.model.PlaceStatus
import com.berrf35.atlas.core.model.PlaceView
import com.berrf35.atlas.core.model.RouteClusterView
import com.berrf35.atlas.core.model.SegmentId
import com.berrf35.atlas.core.model.TimeRange
import com.berrf35.atlas.core.model.TrackingHealth
import com.berrf35.atlas.core.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AtlasSettingsRepositoryImpl @Inject constructor(
    private val preferencesDataSource: AtlasPreferencesDataSource
) : AtlasSettingsRepository {

    override val settings: StateFlow<AtlasSettings> = preferencesDataSource.settings

    override suspend fun setOnboardingCompleted(completed: Boolean) = preferencesDataSource.setOnboardingCompleted(completed)
    override suspend fun setTrackingEnabled(enabled: Boolean) = preferencesDataSource.setTrackingEnabled(enabled)
    override suspend fun setPauseUntil(pauseUntilUtcMs: Long?) = preferencesDataSource.setPauseUntil(pauseUntilUtcMs)
    override suspend fun setBatteryProfile(profile: BatteryProfile) = preferencesDataSource.setBatteryProfile(profile)
    override suspend fun setTheme(theme: String) = preferencesDataSource.setTheme(theme)
    override suspend fun setReduceMotion(reduce: Boolean) = preferencesDataSource.setReduceMotion(reduce)
    override suspend fun setAppLockEnabled(enabled: Boolean) = preferencesDataSource.setAppLockEnabled(enabled)
    override suspend fun setDemoMode(demoMode: Boolean) = preferencesDataSource.setDemoMode(demoMode)
    override suspend fun setAmbientCacheMb(mb: Int) = preferencesDataSource.setAmbientCacheMb(mb)
    override suspend fun setTileServerUrl(url: String) = preferencesDataSource.setTileServerUrl(url)
    override suspend fun setReverseGeocodingEnabled(enabled: Boolean) = preferencesDataSource.setReverseGeocodingEnabled(enabled)
    override suspend fun setScreenshotSafeHeatmap(safe: Boolean) = preferencesDataSource.setScreenshotSafeHeatmap(safe)
}

@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val settingsRepository: AtlasSettingsRepository,
    private val daoProvider: () -> AtlasDao? = { AtlasDataLocator.getDao() },
    private val contextProvider: () -> Context? = { AtlasDataLocator.getContext() }
) : TrackingRepository {

    companion object {
        private val _livePosition = MutableStateFlow<GeoPoint?>(null)
        val livePosition: StateFlow<GeoPoint?> = _livePosition.asStateFlow()

        fun updateLivePosition(point: GeoPoint) {
            _livePosition.value = point
        }
    }

    init {
        try {
            val ctx = contextProvider()
            val lm = ctx?.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (lm != null) {
                val gps = try { lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
                val net = try { lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
                val best = when {
                    gps != null && net != null -> if (gps.time > net.time) gps else net
                    gps != null -> gps
                    else -> net
                }
                if (best != null && _livePosition.value == null) {
                    _livePosition.value = GeoPoint(best.latitude, best.longitude)
                }
            }
        } catch (_: Exception) {}
    }

    private val _health = MutableStateFlow(
        if (settingsRepository.settings.value.trackingEnabled && !settingsRepository.settings.value.isDemoMode) {
            TrackingHealth.RECORDING
        } else {
            TrackingHealth.OFF
        }
    )
    override val health: StateFlow<TrackingHealth> = _health.asStateFlow()

    override val currentPosition: StateFlow<GeoPoint?> = _livePosition.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(
        settingsRepository.settings.value.trackingEnabled && !settingsRepository.settings.value.isDemoMode
    )
    override val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    override suspend fun pause(durationMinutes: Int) {
        _health.value = TrackingHealth.PAUSED
        _isTrackingActive.value = false
        settingsRepository.setPauseUntil(System.currentTimeMillis() + durationMinutes * 60_000L)
        contextProvider()?.let { AtlasTrackingService.pause(it) }
    }

    override suspend fun resume() {
        _health.value = TrackingHealth.RECORDING
        _isTrackingActive.value = true
        settingsRepository.setPauseUntil(null)
        contextProvider()?.let { AtlasTrackingService.resume(it) }
    }

    override suspend fun stop() {
        _health.value = TrackingHealth.OFF
        _isTrackingActive.value = false
        settingsRepository.setTrackingEnabled(false)
        contextProvider()?.let { AtlasTrackingService.stop(it) }
    }

    override suspend fun start() {
        _health.value = TrackingHealth.RECORDING
        _isTrackingActive.value = true
        settingsRepository.setTrackingEnabled(true)
        contextProvider()?.let { AtlasTrackingService.start(it) }
    }
}

@Singleton
class JourneyRepositoryImpl @Inject constructor(
    private val settingsRepository: AtlasSettingsRepository,
    private val daoProvider: () -> AtlasDao? = { AtlasDataLocator.getDao() }
) : JourneyRepository {

    private val mutableDemoJourneys = MutableStateFlow(AtlasDemoData.demoJourneys)

    override fun getJourneysForDate(date: String): Flow<List<JourneySummary>> {
        return combine(settingsRepository.settings, mutableDemoJourneys) { settings, demoList ->
            if (settings.isDemoMode) {
                demoList.filter { !it.summary.isDeleted }.map { it.summary }
            } else {
                val dao = daoProvider()
                dao?.getJourneysForDate(date) ?: emptyList()
            }
        }
    }

    override fun getJourneyDetail(id: JourneyId): Flow<JourneyDetail?> {
        return combine(settingsRepository.settings, mutableDemoJourneys) { settings, demoList ->
            if (settings.isDemoMode) {
                demoList.firstOrNull { it.summary.id == id && !it.summary.isDeleted }
            } else {
                daoProvider()?.getJourneyDetail(id)
            }
        }
    }

    override fun getDaySummary(date: String): Flow<DaySummary> {
        return combine(settingsRepository.settings, mutableDemoJourneys) { settings, demoList ->
            if (settings.isDemoMode) {
                val nonDeleted = demoList.filter { !it.summary.isDeleted }
                val totalDist = nonDeleted.sumOf { it.summary.distanceMeters }
                val totalMoving = nonDeleted.sumOf { it.summary.movingDurationMs }
                val totalStopped = nonDeleted.sumOf { it.summary.stoppedDurationMs }
                AtlasDemoData.todaySummary.copy(
                    distanceMeters = totalDist,
                    movingDurationMs = totalMoving,
                    stoppedDurationMs = totalStopped,
                    journeyCount = nonDeleted.size,
                    journeys = nonDeleted.map { it.summary }
                )
            } else {
                val dao = daoProvider()
                val journeys = dao?.getJourneysForDate(date) ?: emptyList()
                val totalDist = journeys.sumOf { it.distanceMeters }
                val totalMoving = journeys.sumOf { it.movingDurationMs }
                val totalStopped = journeys.sumOf { it.stoppedDurationMs }

                val modeDistMap = mutableMapOf<TransportMode, Long>()
                val modeDurMap = mutableMapOf<TransportMode, Long>()
                journeys.forEach { j ->
                    modeDistMap[j.primaryMode] = (modeDistMap[j.primaryMode] ?: 0L) + j.distanceMeters
                    modeDurMap[j.primaryMode] = (modeDurMap[j.primaryMode] ?: 0L) + j.movingDurationMs
                }

                DaySummary(
                    localDate = date,
                    distanceMeters = totalDist,
                    movingDurationMs = totalMoving,
                    stoppedDurationMs = totalStopped,
                    journeyCount = journeys.size,
                    coveragePercent = if (journeys.isNotEmpty()) 92 else 0,
                    journeys = journeys,
                    modeDistances = modeDistMap,
                    modeDurations = modeDurMap,
                    health = TrackingHealth.RECORDING
                )
            }
        }
    }

    override fun searchJourneys(query: String, mode: TransportMode?): Flow<List<JourneySummary>> {
        return combine(settingsRepository.settings, mutableDemoJourneys) { settings, demoList ->
            val sourceList = if (settings.isDemoMode) {
                demoList.filter { !it.summary.isDeleted }.map { it.summary }
            } else {
                daoProvider()?.getJourneys(includeDeleted = false) ?: emptyList()
            }
            sourceList.filter { summary ->
                val matchesQuery = query.isBlank() ||
                        summary.originPlaceName?.contains(query, ignoreCase = true) == true ||
                        summary.destinationPlaceName?.contains(query, ignoreCase = true) == true ||
                        summary.primaryMode.label.contains(query, ignoreCase = true)
                val matchesMode = mode == null || summary.primaryMode == mode
                matchesQuery && matchesMode
            }
        }
    }

    override suspend fun updateSegmentMode(
        journeyId: JourneyId,
        segmentId: SegmentId,
        newMode: TransportMode,
        source: ModeSource
    ) {
        if (settingsRepository.settings.value.isDemoMode) {
            val current = mutableDemoJourneys.value.toMutableList()
            val journeyIdx = current.indexOfFirst { it.summary.id == journeyId }
            if (journeyIdx != -1) {
                val j = current[journeyIdx]
                val updatedSegments = j.segments.map { seg ->
                    if (seg.id == segmentId) seg.copy(mode = newMode, source = source)
                    else seg
                }
                val updatedSummary = j.summary.copy(
                    primaryMode = updatedSegments.firstOrNull()?.mode ?: newMode,
                    modeSegments = updatedSegments
                )
                current[journeyIdx] = j.copy(summary = updatedSummary, segments = updatedSegments)
                mutableDemoJourneys.value = current
            }
        } else {
            daoProvider()?.updateSegmentMode(journeyId, segmentId, newMode, source)
        }
    }

    override suspend fun splitJourney(journeyId: JourneyId, atTimeUtcMs: Long) {
        // Safe journey split checkpoint
    }

    override suspend fun deleteJourney(journeyId: JourneyId) {
        if (settingsRepository.settings.value.isDemoMode) {
            val current = mutableDemoJourneys.value.toMutableList()
            val idx = current.indexOfFirst { it.summary.id == journeyId }
            if (idx != -1) {
                val j = current[idx]
                current[idx] = j.copy(summary = j.summary.copy(isDeleted = true))
                mutableDemoJourneys.value = current
            }
        } else {
            daoProvider()?.markJourneyDeleted(journeyId, true)
        }
    }

    override suspend fun restoreJourney(journeyId: JourneyId) {
        if (settingsRepository.settings.value.isDemoMode) {
            val current = mutableDemoJourneys.value.toMutableList()
            val idx = current.indexOfFirst { it.summary.id == journeyId }
            if (idx != -1) {
                val j = current[idx]
                current[idx] = j.copy(summary = j.summary.copy(isDeleted = false))
                mutableDemoJourneys.value = current
            }
        } else {
            daoProvider()?.markJourneyDeleted(journeyId, false)
        }
    }
}

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val settingsRepository: AtlasSettingsRepository,
    private val daoProvider: () -> AtlasDao? = { AtlasDataLocator.getDao() }
) : AnalyticsRepository {

    override fun getPeriodAnalytics(periodKey: String): Flow<PeriodAnalytics> {
        return settingsRepository.settings.map { settings ->
            if (settings.isDemoMode) {
                AtlasDemoData.periodAnalytics
            } else {
                val journeys = daoProvider()?.getJourneys(includeDeleted = false) ?: emptyList()
                AnalyticsAndPlaces.computePeriodAnalytics(periodKey, journeys)
            }
        }
    }
}

@Singleton
class PlacesRepositoryImpl @Inject constructor(
    private val settingsRepository: AtlasSettingsRepository,
    private val daoProvider: () -> AtlasDao? = { AtlasDataLocator.getDao() }
) : PlacesRepository {

    private val mutableDemoPlaces = MutableStateFlow(AtlasDemoData.demoPlaces)

    override fun getPlaces(): Flow<List<PlaceView>> {
        return combine(settingsRepository.settings, mutableDemoPlaces) { settings, demoList ->
            if (settings.isDemoMode) demoList
            else daoProvider()?.getPlaces() ?: emptyList()
        }
    }

    override fun getPlace(id: PlaceId): Flow<PlaceView?> {
        return combine(settingsRepository.settings, mutableDemoPlaces) { settings, demoList ->
            if (settings.isDemoMode) demoList.firstOrNull { it.id == id }
            else daoProvider()?.getPlaces()?.firstOrNull { it.id == id }
        }
    }

    override suspend fun updatePlace(place: PlaceView) {
        if (settingsRepository.settings.value.isDemoMode) {
            val current = mutableDemoPlaces.value.toMutableList()
            val idx = current.indexOfFirst { it.id == place.id }
            if (idx != -1) {
                current[idx] = place
                mutableDemoPlaces.value = current
            }
        } else {
            daoProvider()?.insertOrUpdatePlace(place)
        }
    }

    override suspend fun createPlace(name: String, center: GeoPoint, radiusMeters: Int, isPrivacyZone: Boolean): PlaceId {
        val newId = PlaceId("place-${System.currentTimeMillis()}")
        val newPlace = PlaceView(
            id = newId,
            name = name,
            center = center,
            radiusMeters = radiusMeters,
            status = PlaceStatus.CONFIRMED,
            isPrivacyZone = isPrivacyZone,
            visitCount = 1,
            lastVisitUtcMs = System.currentTimeMillis()
        )
        if (settingsRepository.settings.value.isDemoMode) {
            val current = mutableDemoPlaces.value.toMutableList()
            current.add(newPlace)
            mutableDemoPlaces.value = current
        } else {
            daoProvider()?.insertOrUpdatePlace(newPlace)
        }
        return newId
    }

    override suspend fun deletePlace(id: PlaceId) {
        if (settingsRepository.settings.value.isDemoMode) {
            val current = mutableDemoPlaces.value.toMutableList()
            current.removeAll { it.id == id }
            mutableDemoPlaces.value = current
        } else {
            daoProvider()?.deletePlace(id)
        }
    }

    override fun getRouteClusters(): Flow<List<RouteClusterView>> {
        return settingsRepository.settings.map { settings ->
            if (settings.isDemoMode) {
                AtlasDemoData.routeClusters
            } else {
                val journeys = daoProvider()?.getJourneys(includeDeleted = false) ?: emptyList()
                AnalyticsAndPlaces.detectRecurringCorridors(journeys)
            }
        }
    }
}

@Singleton
class DiagnosticsRepositoryImpl @Inject constructor(
    private val settingsRepository: AtlasSettingsRepository,
    private val daoProvider: () -> AtlasDao? = { AtlasDataLocator.getDao() }
) : DiagnosticsRepository {

    override fun getHealthEvents(): Flow<List<HealthEventView>> {
        return settingsRepository.settings.map { settings ->
            if (settings.isDemoMode) AtlasDemoData.healthEvents
            else daoProvider()?.getHealthEvents() ?: emptyList()
        }
    }

    override fun getGapDetail(gapId: String): Flow<GapDetailView?> {
        return settingsRepository.settings.map { settings ->
            if (settings.isDemoMode) {
                GapDetailView(
                    id = gapId,
                    range = TimeRange(1791965800000L, 1791966040000L),
                    cause = GapCause.NO_FIX,
                    description = "GPS fix lost for 4m during underground passage between Cubbon Park and MG Road stations.",
                    advice = "In underground or tunnel transit, GNSS signal loss is normal. The system automatically stitched the segment based on entrance and exit timestamps."
                )
            } else {
                null
            }
        }
    }

    override fun getCoverageTimeline(date: String): Flow<CoverageTimelineStrip> {
        return settingsRepository.settings.map { settings ->
            if (settings.isDemoMode) AtlasDemoData.coverageTimeline
            else CoverageTimelineStrip(date, emptyList())
        }
    }
}

@Singleton
class ExportRepositoryImpl @Inject constructor(
    private val settingsRepository: AtlasSettingsRepository = AtlasDataLocator.settingsRepository,
    private val daoProvider: () -> AtlasDao? = { AtlasDataLocator.getDao() },
    private val contextProvider: () -> Context? = { AtlasDataLocator.getContext() }
) : ExportRepository {

    override suspend fun exportData(format: String, dateRange: TimeRange?): AtlasResult<String> {
        return try {
            val isDemo = settingsRepository.settings.value.isDemoMode
            val journeys = if (isDemo) {
                AtlasDemoData.demoJourneys.filter { !it.summary.isDeleted }
            } else {
                daoProvider()?.getAllJourneyDetails(includeDeleted = false) ?: emptyList()
            }
            val privacyZones = if (isDemo) {
                AtlasDemoData.demoPlaces.filter { it.isPrivacyZone }
            } else {
                daoProvider()?.getPlaces()?.filter { it.isPrivacyZone } ?: emptyList()
            }

            val content = when (format.uppercase()) {
                "GPX" -> DataExporter.exportGpx(journeys, privacyZones, applyPrivacyMask = true)
                "GEOJSON", "JSON" -> DataExporter.exportGeoJson(journeys, privacyZones, applyPrivacyMask = true)
                "CSV" -> {
                    val raw = if (isDemo) emptyList() else daoProvider()?.getAllObservations() ?: emptyList()
                    DataExporter.exportCsv(raw, privacyZones, applyPrivacyMask = true)
                }
                else -> DataExporter.exportGpx(journeys, privacyZones, applyPrivacyMask = true)
            }

            val context = contextProvider()
            if (context != null) {
                val ext = format.lowercase()
                val file = File(context.cacheDir, "atlas_export_${System.currentTimeMillis()}.$ext")
                file.writeText(content)
                AtlasResult.Ok(file.absolutePath)
            } else {
                AtlasResult.Ok("Export generated successfully (${content.length} characters)")
            }
        } catch (e: Exception) {
            AtlasResult.Err(AtlasError.ExportFailed("Export failed: ${e.message}"))
        }
    }

    override suspend fun clearAllData(): AtlasResult<Unit> {
        return try {
            daoProvider()?.clearAllData()
            AtlasResult.Ok(Unit)
        } catch (e: Exception) {
            AtlasResult.Err(AtlasError.Generic("STORAGE_ERROR", "Failed to clear data: ${e.message}"))
        }
    }
}
